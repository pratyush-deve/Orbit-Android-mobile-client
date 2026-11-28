package com.pratyush.orbitmobile.data.remote.ai

import android.util.Log
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.pratyush.orbitmobile.core.common.OrbitResult
import com.pratyush.orbitmobile.core.firebase.FirebasePresenceManager
import com.pratyush.orbitmobile.core.firebase.FirebaseRemoteClient
import com.pratyush.orbitmobile.core.firebase.RemoteLinkStore
import com.pratyush.orbitmobile.domain.model.AcpStatus
import com.pratyush.orbitmobile.domain.model.AiConversation
import com.pratyush.orbitmobile.domain.model.AiMessage
import com.pratyush.orbitmobile.domain.model.AiPermissionOption
import com.pratyush.orbitmobile.domain.model.AiStreamEvent
import com.pratyush.orbitmobile.domain.model.AiToolActivity
import com.pratyush.orbitmobile.domain.repository.AiTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.TreeMap
import java.util.UUID

/**
 * Pure-Kotlin sequence orderer and deduplicator for Firebase RTDB AI stream events.
 * Guarantees strictly increasing sequence delivery, duplicate suppression, and
 * out-of-order buffering without blocking.
 */
internal class SequencedStreamBuffer(
    initialLastSeq: Long = 0L,
    private val onOrderedEvent: (AiStreamEvent) -> Unit
) {
    private val lock = Any()
    private var lastProcessedSeq: Long = initialLastSeq
    private val pending = TreeMap<Long, AiStreamEvent>()

    val currentLastSeq: Long
        get() = synchronized(lock) { lastProcessedSeq }

    val hasGap: Boolean
        get() = synchronized(lock) {
            pending.isNotEmpty() && pending.firstKey() > lastProcessedSeq + 1
        }

    fun submit(seq: Long, event: AiStreamEvent): Boolean {
        if (seq <= 0L) return false
        val toEmit = mutableListOf<AiStreamEvent>()
        synchronized(lock) {
            if (seq <= lastProcessedSeq || pending.containsKey(seq)) {
                return false
            }
            pending[seq] = event
            while (true) {
                val nextSeq = lastProcessedSeq + 1
                val nextEvent = pending.remove(nextSeq) ?: break
                lastProcessedSeq = nextSeq
                toEmit.add(nextEvent)
            }
        }
        toEmit.forEach(onOrderedEvent)
        return true
    }
}

/**
 * Firebase RTDB implementation of [AiTransport].
 * - Dispatches signed commands via [FirebaseRemoteClient] (`ai.*`)
 * - Reads status/conversations/sync snapshots from `/links/{linkId}/ai/`
 * - Consumes sequenced, coalesced streaming events from `/links/{linkId}/ai/streams/{convId}/{msgId}/events/`
 */
class FirebaseAiTransport(
    private val firebaseRemoteClient: FirebaseRemoteClient,
    private val linkStore: RemoteLinkStore,
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance(FirebasePresenceManager.RTDB_URL)
) : AiTransport {

    companion object {
        private const val TAG = "FirebaseAiTransport"
        private const val SYNC_FETCH_TIMEOUT_MS = 15_000L
        private const val STREAM_IDLE_TIMEOUT_MS = 120_000L
        private val TERMINAL_EVENT_TYPES = setOf("complete", "error", "cancelled")
    }

    private fun requireLinkId(): OrbitResult<String> {
        val link = linkStore.loadLink()
            ?: return OrbitResult.Error(IllegalStateException("No remote link configured."))
        if (!firebaseRemoteClient.isRemoteAvailable) {
            return OrbitResult.Error(IllegalStateException("Windows host is offline remotely."))
        }
        return OrbitResult.Success(link.linkId)
    }

    override suspend fun getStatus(): OrbitResult<AcpStatus> = withContext(Dispatchers.IO) {
        val linkId = when (val r = requireLinkId()) {
            is OrbitResult.Success -> r.data
            is OrbitResult.Error -> return@withContext r
            else -> return@withContext OrbitResult.Error(Exception("Unavailable"))
        }

        try {
            val snap = database.getReference("links")
                .child(linkId)
                .child("ai")
                .child("status")
                .get()
                .await()

            if (!snap.exists()) {
                return@withContext OrbitResult.Success(AcpStatus(isAvailable = false, state = "unavailable"))
            }

            OrbitResult.Success(
                AcpStatus(
                    isAvailable = snap.child("isAvailable").getValue(Boolean::class.java) ?: false,
                    state = snap.child("state").getValue(String::class.java) ?: "unavailable",
                    backend = snap.child("backend").getValue(String::class.java) ?: "antigravity-acp",
                    message = snap.child("message").getValue(String::class.java),
                    permissionMode = snap.child("permissionMode").getValue(String::class.java) ?: "AutoApproveSafe"
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OrbitResult.Error(e)
        }
    }

    override suspend fun getConversations(): OrbitResult<List<AiConversation>> = withContext(Dispatchers.IO) {
        val linkId = when (val r = requireLinkId()) {
            is OrbitResult.Success -> r.data
            is OrbitResult.Error -> return@withContext r
            else -> return@withContext OrbitResult.Error(Exception("Unavailable"))
        }

        try {
            val snap = database.getReference("links")
                .child(linkId)
                .child("ai")
                .child("conversations")
                .get()
                .await()

            val list = mutableListOf<AiConversation>()
            for (child in snap.children) {
                val isDeleted = child.child("isDeleted").getValue(Boolean::class.java) ?: false
                if (isDeleted) continue
                parseConversationSnapshot(child)?.let { list.add(it) }
            }
            list.sortWith(compareByDescending<AiConversation> { it.isPinned }.thenByDescending { it.updatedAtUtc })
            OrbitResult.Success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OrbitResult.Error(e)
        }
    }

    override suspend fun getConversation(
        conversationId: String
    ): OrbitResult<Pair<AiConversation, List<AiMessage>>> = withContext(Dispatchers.IO) {
        val linkId = when (val r = requireLinkId()) {
            is OrbitResult.Success -> r.data
            is OrbitResult.Error -> return@withContext r
            else -> return@withContext OrbitResult.Error(Exception("Unavailable"))
        }

        val requestId = UUID.randomUUID().toString().replace("-", "")
        val syncRef = database.getReference("links")
            .child(linkId)
            .child("ai")
            .child("sync")
            .child(requestId)

        val syncDeferred = CompletableDeferred<DataSnapshot>()
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists() && !syncDeferred.isCompleted) {
                    syncDeferred.complete(snapshot)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (!syncDeferred.isCompleted) {
                    syncDeferred.completeExceptionally(error.toException())
                }
            }
        }

        syncRef.addValueEventListener(listener)
        try {
            val payload = JSONObject().apply {
                put("conversationId", conversationId)
                put("limit", 100)
                put("requestId", requestId)
            }.toString()

            val cmdRes = firebaseRemoteClient.sendCommand("ai.conversation.fetch", payload)
            if (cmdRes is OrbitResult.Error) {
                return@withContext cmdRes
            }

            val snap = withTimeoutOrNull(SYNC_FETCH_TIMEOUT_MS) { syncDeferred.await() }
                ?: return@withContext OrbitResult.Error(Exception("Timed out waiting for remote conversation sync."))

            val conv = parseConversationSnapshot(snap.child("conversation"))
                ?: return@withContext OrbitResult.Error(Exception("Invalid conversation payload in sync snapshot."))

            val messages = mutableListOf<AiMessage>()
            for (mSnap in snap.child("messages").children) {
                parseSyncMessageSnapshot(mSnap)?.let { messages.add(it) }
            }

            OrbitResult.Success(Pair(conv, messages))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OrbitResult.Error(e)
        } finally {
            syncRef.removeEventListener(listener)
        }
    }

    override suspend fun createConversation(
        title: String?,
        conversationId: String?
    ): OrbitResult<AiConversation> = withContext(Dispatchers.IO) {
        val linkId = when (val r = requireLinkId()) {
            is OrbitResult.Success -> r.data
            is OrbitResult.Error -> return@withContext r
            else -> return@withContext OrbitResult.Error(Exception("Unavailable"))
        }

        val convId = conversationId ?: UUID.randomUUID().toString().replace("-", "")
        val payload = JSONObject().apply {
            put("conversationId", convId)
            if (title != null) put("title", title)
        }.toString()

        when (val res = firebaseRemoteClient.sendCommand("ai.conversation.create", payload)) {
            is OrbitResult.Success -> {
                val snap = runCatching {
                    database.getReference("links").child(linkId).child("ai").child("conversations").child(convId).get().await()
                }.getOrNull()
                val conv = snap?.let { parseConversationSnapshot(it) } ?: AiConversation(
                    id = convId,
                    title = title ?: "New Chat",
                    origin = "Mobile",
                    isSynced = true
                )
                OrbitResult.Success(conv)
            }
            is OrbitResult.Error -> res
            else -> OrbitResult.Error(Exception("Unexpected result creating conversation"))
        }
    }

    override suspend fun renameConversation(
        conversationId: String,
        title: String
    ): OrbitResult<AiConversation> = withContext(Dispatchers.IO) {
        val linkId = when (val r = requireLinkId()) {
            is OrbitResult.Success -> r.data
            is OrbitResult.Error -> return@withContext r
            else -> return@withContext OrbitResult.Error(Exception("Unavailable"))
        }

        val payload = JSONObject().apply {
            put("conversationId", conversationId)
            put("title", title)
        }.toString()

        when (val res = firebaseRemoteClient.sendCommand("ai.conversation.rename", payload)) {
            is OrbitResult.Success -> {
                val snap = runCatching {
                    database.getReference("links").child(linkId).child("ai").child("conversations").child(conversationId).get().await()
                }.getOrNull()
                val conv = snap?.let { parseConversationSnapshot(it) } ?: AiConversation(
                    id = conversationId,
                    title = title,
                    origin = "Mobile",
                    isSynced = true
                )
                OrbitResult.Success(conv)
            }
            is OrbitResult.Error -> res
            else -> OrbitResult.Error(Exception("Unexpected result renaming conversation"))
        }
    }

    override suspend fun deleteConversation(conversationId: String): OrbitResult<Unit> = withContext(Dispatchers.IO) {
        if (requireLinkId() is OrbitResult.Error) {
            return@withContext requireLinkId() as OrbitResult.Error
        }
        val payload = JSONObject().apply {
            put("conversationId", conversationId)
        }.toString()
        firebaseRemoteClient.sendCommand("ai.conversation.delete", payload)
    }

    override suspend fun streamPrompt(
        conversationId: String,
        prompt: String,
        userMessageId: String?,
        assistantMessageId: String?,
        onEvent: (AiStreamEvent) -> Unit
    ): OrbitResult<Unit> = withContext(Dispatchers.IO) {
        val linkId = when (val r = requireLinkId()) {
            is OrbitResult.Success -> r.data
            is OrbitResult.Error -> return@withContext r
            else -> return@withContext OrbitResult.Error(Exception("Unavailable"))
        }

        val resolvedUserMsgId = userMessageId ?: UUID.randomUUID().toString().replace("-", "")
        val resolvedAssistantMsgId = assistantMessageId ?: UUID.randomUUID().toString().replace("-", "")

        val streamRef = database.getReference("links")
            .child(linkId)
            .child("ai")
            .child("streams")
            .child(conversationId)
            .child(resolvedAssistantMsgId)
        val eventsRef = streamRef.child("events")
        val stateRef = streamRef.child("state")

        val streamDone = CompletableDeferred<OrbitResult<Unit>>()
        val buffer = SequencedStreamBuffer(0L) { orderedEvent ->
            onEvent(orderedEvent)
            if (orderedEvent.type in TERMINAL_EVENT_TYPES && !streamDone.isCompleted) {
                streamDone.complete(OrbitResult.Success(Unit))
            }
        }

        val eventsListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val (seq, ev) = parseStreamEventSnapshot(snapshot, conversationId, resolvedAssistantMsgId) ?: return
                buffer.submit(seq, ev)
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                val (seq, ev) = parseStreamEventSnapshot(snapshot, conversationId, resolvedAssistantMsgId) ?: return
                buffer.submit(seq, ev)
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                if (!streamDone.isCompleted) {
                    streamDone.complete(OrbitResult.Error(error.toException()))
                }
            }
        }

        val stateListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val st = snapshot.getValue(String::class.java) ?: return
                if ((st == "completed" || st == "failed" || st == "cancelled") && !streamDone.isCompleted) {
                    // Recover any missed events before completing
                    eventsRef.get().addOnSuccessListener { allSnap ->
                        for (child in allSnap.children) {
                            val (seq, ev) = parseStreamEventSnapshot(child, conversationId, resolvedAssistantMsgId) ?: continue
                            buffer.submit(seq, ev)
                        }
                        if (!streamDone.isCompleted) {
                            streamDone.complete(OrbitResult.Success(Unit))
                        }
                    }.addOnFailureListener {
                        if (!streamDone.isCompleted) {
                            streamDone.complete(OrbitResult.Success(Unit))
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        eventsRef.addChildEventListener(eventsListener)
        stateRef.addValueEventListener(stateListener)

        try {
            val payload = JSONObject().apply {
                put("assistantMessageId", resolvedAssistantMsgId)
                put("conversationId", conversationId)
                put("prompt", prompt)
                put("userMessageId", resolvedUserMsgId)
            }.toString()

            val startRes = firebaseRemoteClient.sendCommand("ai.prompt.start", payload)
            if (startRes is OrbitResult.Error) {
                return@withContext startRes
            }

            val result = withTimeoutOrNull(STREAM_IDLE_TIMEOUT_MS) { streamDone.await() }
                ?: OrbitResult.Error(Exception("Remote AI stream timed out."))
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OrbitResult.Error(e)
        } finally {
            eventsRef.removeEventListener(eventsListener)
            stateRef.removeEventListener(stateListener)
        }
    }

    /**
     * Recovers missed events after [afterSeq] for an existing remote stream without
     * re-submitting `ai.prompt.start`. If the stream is still active (`state == "streaming"`),
     * continues listening until a terminal event arrives.
     */
    suspend fun recoverStream(
        conversationId: String,
        assistantMessageId: String,
        afterSeq: Long,
        onEvent: (AiStreamEvent) -> Unit
    ): OrbitResult<Long> = withContext(Dispatchers.IO) {
        val linkId = when (val r = requireLinkId()) {
            is OrbitResult.Success -> r.data
            is OrbitResult.Error -> return@withContext r
            else -> return@withContext OrbitResult.Error(Exception("Unavailable"))
        }

        val streamRef = database.getReference("links")
            .child(linkId)
            .child("ai")
            .child("streams")
            .child(conversationId)
            .child(assistantMessageId)
        val eventsRef = streamRef.child("events")
        val stateRef = streamRef.child("state")

        val buffer = SequencedStreamBuffer(afterSeq, onEvent)
        val streamDone = CompletableDeferred<OrbitResult<Unit>>()

        val eventsListener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val (seq, ev) = parseStreamEventSnapshot(snapshot, conversationId, assistantMessageId) ?: return
                buffer.submit(seq, ev)
                if (ev.type in TERMINAL_EVENT_TYPES && !streamDone.isCompleted) {
                    streamDone.complete(OrbitResult.Success(Unit))
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                val (seq, ev) = parseStreamEventSnapshot(snapshot, conversationId, assistantMessageId) ?: return
                buffer.submit(seq, ev)
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                if (!streamDone.isCompleted) streamDone.complete(OrbitResult.Error(error.toException()))
            }
        }

        val stateListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val st = snapshot.getValue(String::class.java) ?: return
                if ((st == "completed" || st == "failed" || st == "cancelled") && !streamDone.isCompleted) {
                    streamDone.complete(OrbitResult.Success(Unit))
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        try {
            val snap = streamRef.get().await()
            if (!snap.exists()) {
                return@withContext OrbitResult.Success(buffer.currentLastSeq)
            }

            for (child in snap.child("events").children) {
                val (seq, ev) = parseStreamEventSnapshot(child, conversationId, assistantMessageId) ?: continue
                buffer.submit(seq, ev)
            }

            val currentState = snap.child("state").getValue(String::class.java) ?: "completed"
            if (currentState == "streaming") {
                eventsRef.addChildEventListener(eventsListener)
                stateRef.addValueEventListener(stateListener)
                withTimeoutOrNull(STREAM_IDLE_TIMEOUT_MS) { streamDone.await() }
            }

            OrbitResult.Success(buffer.currentLastSeq)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OrbitResult.Error(e)
        } finally {
            eventsRef.removeEventListener(eventsListener)
            stateRef.removeEventListener(stateListener)
        }
    }

    override suspend fun cancelPrompt(conversationId: String): OrbitResult<Unit> = withContext(Dispatchers.IO) {
        if (requireLinkId() is OrbitResult.Error) {
            return@withContext requireLinkId() as OrbitResult.Error
        }
        val payload = JSONObject().apply {
            put("conversationId", conversationId)
        }.toString()
        firebaseRemoteClient.sendCommand("ai.prompt.cancel", payload)
    }

    override suspend fun resolvePermission(requestId: String, optionId: String): OrbitResult<Boolean> = withContext(Dispatchers.IO) {
        if (requireLinkId() is OrbitResult.Error) {
            return@withContext requireLinkId() as OrbitResult.Error
        }
        val payload = JSONObject().apply {
            put("optionId", optionId)
            put("requestId", requestId)
        }.toString()
        when (val res = firebaseRemoteClient.sendCommand("ai.permission.resolve", payload)) {
            is OrbitResult.Success -> OrbitResult.Success(true)
            is OrbitResult.Error -> res
            else -> OrbitResult.Error(Exception("Failed to resolve permission"))
        }
    }

    override suspend fun setPolicy(policy: String): OrbitResult<Unit> = withContext(Dispatchers.IO) {
        if (requireLinkId() is OrbitResult.Error) {
            return@withContext requireLinkId() as OrbitResult.Error
        }
        val payload = JSONObject().apply {
            put("policy", policy)
        }.toString()
        firebaseRemoteClient.sendCommand("ai.policy.set", payload)
    }

    private fun parseConversationSnapshot(snap: DataSnapshot): AiConversation? {
        if (!snap.exists()) return null
        val id = snap.child("id").getValue(String::class.java) ?: snap.key ?: return null
        val title = snap.child("title").getValue(String::class.java) ?: "Untitled Chat"
        val now = System.currentTimeMillis()
        return AiConversation(
            id = id,
            title = title,
            backendId = snap.child("backendId").getValue(String::class.java) ?: "antigravity-acp",
            workspacePath = snap.child("workspacePath").getValue(String::class.java),
            createdAtUtc = snap.child("createdAtUtcMs").getValue(Long::class.java) ?: now,
            updatedAtUtc = snap.child("updatedAtUtcMs").getValue(Long::class.java) ?: now,
            isPinned = snap.child("isPinned").getValue(Boolean::class.java) ?: false,
            isArchived = snap.child("isArchived").getValue(Boolean::class.java) ?: false,
            origin = snap.child("origin").getValue(String::class.java) ?: "Mobile",
            isSynced = true
        )
    }

    private fun parseSyncMessageSnapshot(mSnap: DataSnapshot): AiMessage? {
        if (!mSnap.exists()) return null
        val id = mSnap.child("id").getValue(String::class.java) ?: return null
        val convId = mSnap.child("conversationId").getValue(String::class.java) ?: return null
        val role = mSnap.child("role").getValue(String::class.java) ?: "assistant"
        val content = mSnap.child("content").getValue(String::class.java) ?: ""
        val ts = mSnap.child("timestampUtcMs").getValue(Long::class.java) ?: System.currentTimeMillis()
        val genStatus = mSnap.child("generationStatus").getValue(String::class.java) ?: "completed"

        val tools = mutableListOf<AiToolActivity>()
        for (tSnap in mSnap.child("toolActivities").children) {
            val tid = tSnap.child("id").getValue(String::class.java) ?: continue
            tools.add(
                AiToolActivity(
                    id = tid,
                    messageId = tSnap.child("messageId").getValue(String::class.java) ?: id,
                    toolCallId = tSnap.child("toolCallId").getValue(String::class.java) ?: tid,
                    toolName = tSnap.child("toolName").getValue(String::class.java) ?: "Tool",
                    status = tSnap.child("status").getValue(String::class.java) ?: "completed",
                    detail = tSnap.child("detail").getValue(String::class.java),
                    timestampUtc = tSnap.child("timestampUtcMs").getValue(Long::class.java) ?: ts
                )
            )
        }

        return AiMessage(
            id = id,
            conversationId = convId,
            role = role,
            content = content,
            timestampUtc = ts,
            generationStatus = genStatus,
            isSynced = true,
            toolActivities = tools
        )
    }

    private fun parseStreamEventSnapshot(
        snap: DataSnapshot,
        fallbackConvId: String,
        fallbackMsgId: String
    ): Pair<Long, AiStreamEvent>? {
        if (!snap.exists()) return null
        val seq = snap.child("seq").getValue(Long::class.java)
            ?: snap.key?.toLongOrNull()
            ?: return null
        val type = snap.child("type").getValue(String::class.java) ?: return null

        val options = mutableListOf<AiPermissionOption>()
        for (oSnap in snap.child("options").children) {
            val optId = oSnap.child("optionId").getValue(String::class.java) ?: continue
            val name = oSnap.child("name").getValue(String::class.java) ?: optId
            val kind = oSnap.child("kind").getValue(String::class.java)
            options.add(AiPermissionOption(optionId = optId, name = name, kind = kind))
        }

        val event = AiStreamEvent(
            type = type,
            conversationId = snap.child("conversationId").getValue(String::class.java) ?: fallbackConvId,
            messageId = snap.child("messageId").getValue(String::class.java) ?: fallbackMsgId,
            text = snap.child("text").getValue(String::class.java),
            toolCallId = snap.child("toolCallId").getValue(String::class.java),
            toolTitle = snap.child("toolTitle").getValue(String::class.java),
            toolName = snap.child("toolName").getValue(String::class.java),
            toolStatus = snap.child("toolStatus").getValue(String::class.java),
            toolDetail = snap.child("toolDetail").getValue(String::class.java),
            requestId = snap.child("requestId").getValue(String::class.java),
            description = snap.child("description").getValue(String::class.java),
            options = options,
            error = snap.child("error").getValue(String::class.java)
        )
        return seq to event
    }
}
