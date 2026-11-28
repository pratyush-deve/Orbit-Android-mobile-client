package com.pratyush.orbitmobile.data.repository.implementations

import android.util.Log
import com.pratyush.orbitmobile.core.common.OrbitResult
import com.pratyush.orbitmobile.data.local.database.OrbitDatabase
import com.pratyush.orbitmobile.data.local.database.entity.AiConversationEntity
import com.pratyush.orbitmobile.data.local.database.entity.AiMessageEntity
import com.pratyush.orbitmobile.data.local.database.entity.AiPendingOpEntity
import com.pratyush.orbitmobile.data.local.database.entity.AiToolActivityEntity
import com.pratyush.orbitmobile.data.remote.ai.RoutingAiTransport
import com.pratyush.orbitmobile.domain.model.AcpStatus
import com.pratyush.orbitmobile.domain.model.AiConversation
import com.pratyush.orbitmobile.domain.model.AiMessage
import com.pratyush.orbitmobile.domain.model.AiPermissionRequest
import com.pratyush.orbitmobile.domain.model.AiToolActivity
import com.pratyush.orbitmobile.domain.model.ConnectionState
import com.pratyush.orbitmobile.domain.model.EffectiveConnectionState
import com.pratyush.orbitmobile.domain.repository.AiChatRepository
import com.pratyush.orbitmobile.domain.repository.AiTransport
import com.pratyush.orbitmobile.domain.repository.ConnectionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.UUID

/**
 * Production implementation of [AiChatRepository].
 * Local persistence via [OrbitDatabase] (Room/SQLite), live synchronization via [AiTransport],
 * and zero fallback to mock models.
 */
class AiChatRepositoryImpl(
    private val connectionRepository: ConnectionRepository,
    private val aiTransport: AiTransport,
    private val database: OrbitDatabase,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : AiChatRepository {

    private val tag = "AiChatRepo"

    private val _acpStatus = MutableStateFlow(AcpStatus())
    override val acpStatus: StateFlow<AcpStatus> = _acpStatus.asStateFlow()

    private val _conversations = MutableStateFlow<List<AiConversation>>(emptyList())
    override val conversations: StateFlow<List<AiConversation>> = _conversations.asStateFlow()

    private val _currentConversationId = MutableStateFlow<String?>(null)
    override val currentConversationId: StateFlow<String?> = _currentConversationId.asStateFlow()

    private val _currentMessages = MutableStateFlow<List<AiMessage>>(emptyList())
    override val currentMessages: StateFlow<List<AiMessage>> = _currentMessages.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    override val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _pendingPermissionRequest = MutableStateFlow<AiPermissionRequest?>(null)
    override val pendingPermissionRequest: StateFlow<AiPermissionRequest?> = _pendingPermissionRequest.asStateFlow()

    private var statusPollJob: Job? = null
    private var streamingJob: Job? = null
    private val outboxMutex = Mutex()

    init {
        // Recover any outbox ops left in_flight across app restart
        scope.launch {
            database.aiChatDao().resetInFlightPendingOps()
        }

        // 1. Observe local SQLite database for instant cached UI rendering
        scope.launch {
            database.aiChatDao().getConversationsFlow().collectLatest { entities ->
                val domainList = entities.map { e ->
                    AiConversation(
                        id = e.id,
                        title = e.title,
                        backendId = e.backendId,
                        workspacePath = e.workspacePath,
                        createdAtUtc = e.createdAtUtc,
                        updatedAtUtc = e.updatedAtUtc,
                        isPinned = e.isPinned,
                        isArchived = e.isArchived,
                        origin = e.origin,
                        isSynced = e.isSynced
                    )
                }
                _conversations.value = domainList

                // Default to most recent conversation if none selected
                if (_currentConversationId.value == null && domainList.isNotEmpty()) {
                    selectConversation(domainList.first().id)
                }
            }
        }

        // 2. Observe effective connection state (LAN priority + Firebase remote fallback)
        scope.launch {
            val routing = aiTransport as? RoutingAiTransport
            if (routing != null) {
                routing.effectiveConnectionState.collect { state ->
                    if (state.isConnected) {
                        val intervalMs = if (state is EffectiveConnectionState.ConnectedRemote) 12000L else 4000L
                        startStatusMonitoring(intervalMs)
                        launch { replayPendingOps() }
                        refreshConversations()
                        val activeId = _currentConversationId.value
                        if (activeId != null && !_isStreaming.value) {
                            selectConversation(activeId)
                        }
                    } else {
                        stopStatusMonitoring()
                        _acpStatus.update { it.copy(isAvailable = false, state = "unavailable") }
                    }
                }
            } else {
                connectionRepository.connectionState.collect { state ->
                    if (state is ConnectionState.Connected) {
                        startStatusMonitoring(4000L)
                        launch { replayPendingOps() }
                        refreshConversations()
                    } else {
                        stopStatusMonitoring()
                        _acpStatus.update { it.copy(isAvailable = false, state = "unavailable") }
                    }
                }
            }
        }
    }

    private fun isTransportConnected(): Boolean {
        val routing = aiTransport as? RoutingAiTransport
        return if (routing != null) {
            routing.effectiveConnectionState.value.isConnected
        } else {
            connectionRepository.connectionState.value is ConnectionState.Connected
        }
    }

    private fun startStatusMonitoring(intervalMs: Long = 4000L) {
        statusPollJob?.cancel()
        statusPollJob = scope.launch {
            while (isActive) {
                refreshStatus()
                delay(intervalMs)
            }
        }
    }

    private fun stopStatusMonitoring() {
        statusPollJob?.cancel()
        statusPollJob = null
    }

    override suspend fun refreshStatus() {
        if (!isTransportConnected()) return

        when (val result = aiTransport.getStatus()) {
            is OrbitResult.Success -> {
                _acpStatus.value = result.data
            }
            is OrbitResult.Error -> {
                Log.d(tag, "Status check error: ${result.exception.message}")
            }
            else -> Unit
        }
    }

    override suspend fun refreshConversations() {
        if (!isTransportConnected()) return

        when (val result = aiTransport.getConversations()) {
            is OrbitResult.Success -> {
                val pendingDeletes = database.aiChatDao().getPendingOps()
                    .filter { it.opType == AiPendingOpEntity.OP_DELETE }
                    .map { it.conversationId }
                    .toSet()
                val remoteList = result.data.filterNot { it.id in pendingDeletes }
                val entities = remoteList.map { c ->
                    AiConversationEntity(
                        id = c.id,
                        title = c.title,
                        backendId = c.backendId,
                        workspacePath = c.workspacePath,
                        createdAtUtc = c.createdAtUtc,
                        updatedAtUtc = c.updatedAtUtc,
                        isPinned = c.isPinned,
                        isArchived = c.isArchived,
                        origin = c.origin,
                        isSynced = true
                    )
                }
                database.aiChatDao().upsertConversations(entities)
            }
            is OrbitResult.Error -> {
                Log.w(tag, "Failed to refresh conversations from Windows: ${result.exception.message}")
            }
            else -> Unit
        }
    }

    override suspend fun selectConversation(conversationId: String) {
        _currentConversationId.value = conversationId

        // 1. Load cached messages from SQLite immediately
        loadMessagesFromDb(conversationId)

        // 2. Fetch authoritative transcript from Windows if connected
        if (!isTransportConnected()) return

        scope.launch {
            when (val result = aiTransport.getConversation(conversationId)) {
                is OrbitResult.Success -> {
                    val (_, remoteMessages) = result.data
                    val entities = remoteMessages.map { m ->
                        AiMessageEntity(
                            id = m.id,
                            conversationId = m.conversationId,
                            role = m.role,
                            content = m.content,
                            timestampUtc = m.timestampUtc,
                            generationStatus = m.generationStatus,
                            isSynced = true
                        )
                    }
                    database.aiChatDao().upsertMessages(entities)

                    for (m in remoteMessages) {
                        for (t in m.toolActivities) {
                            database.aiChatDao().upsertToolActivity(
                                AiToolActivityEntity(
                                    id = t.id,
                                    messageId = t.messageId,
                                    toolCallId = t.toolCallId,
                                    toolName = t.toolName,
                                    status = t.status,
                                    detail = t.detail,
                                    title = t.title,
                                    timestampUtc = t.timestampUtc
                                )
                            )
                        }
                    }

                    loadMessagesFromDb(conversationId)
                }
                is OrbitResult.Error -> {
                    Log.w(tag, "Failed to fetch remote conversation details: ${result.exception.message}")
                }
                else -> Unit
            }
        }
    }

    private suspend fun loadMessagesFromDb(conversationId: String) {
        val entities = database.aiChatDao().getMessages(conversationId)
        val domainMessages = entities.map { e ->
            val toolEntities = database.aiChatDao().getToolActivities(e.id)
            AiMessage(
                id = e.id,
                conversationId = e.conversationId,
                role = e.role,
                content = e.content,
                timestampUtc = e.timestampUtc,
                generationStatus = e.generationStatus,
                isSynced = e.isSynced,
                toolActivities = toolEntities.map { t ->
                    AiToolActivity(
                        id = t.id,
                        messageId = t.messageId,
                        toolCallId = t.toolCallId,
                        toolName = t.toolName,
                        status = t.status,
                        detail = t.detail,
                        title = t.title,
                        timestampUtc = t.timestampUtc
                    )
                }
            )
        }
        _currentMessages.value = domainMessages
    }

    override suspend fun createNewConversation(title: String?): OrbitResult<AiConversation> {
        val newId = UUID.randomUUID().toString().replace("-", "")
        val resolvedTitle = title ?: "New Chat"
        val now = System.currentTimeMillis()

        val localConv = AiConversation(
            id = newId,
            title = resolvedTitle,
            backendId = "antigravity-acp",
            createdAtUtc = now,
            updatedAtUtc = now,
            origin = "Mobile",
            isSynced = false
        )

        // Save locally first
        database.aiChatDao().upsertConversation(
            AiConversationEntity(
                id = localConv.id,
                title = localConv.title,
                backendId = localConv.backendId,
                workspacePath = localConv.workspacePath,
                createdAtUtc = localConv.createdAtUtc,
                updatedAtUtc = localConv.updatedAtUtc,
                isPinned = localConv.isPinned,
                isArchived = localConv.isArchived,
                origin = localConv.origin,
                isSynced = false
            )
        )

        selectConversation(newId)

        // Enqueue in persistent outbox before attempting network delivery
        val createPayload = JSONObject().apply {
            if (title != null) put("title", title)
        }.toString()
        database.aiChatDao().enqueuePendingOp(
            AiPendingOpEntity(
                opId = "create:$newId",
                conversationId = newId,
                opType = AiPendingOpEntity.OP_CREATE,
                payloadJson = createPayload,
                createdAtMs = now
            )
        )

        // Sync with Windows passing the exact same newId
        if (isTransportConnected()) {
            scope.launch {
                replayPendingOps()
            }
        }

        return OrbitResult.Success(localConv)
    }

    override suspend fun renameConversation(conversationId: String, newTitle: String): OrbitResult<Unit> {
        val trimmedTitle = newTitle.trim()
        if (trimmedTitle.isEmpty()) return OrbitResult.Error(Exception("Conversation title cannot be empty"))

        val now = System.currentTimeMillis()
        database.aiChatDao().updateConversationTitle(conversationId, trimmedTitle, updatedAtUtc = now, isSynced = false)

        val renamePayload = JSONObject().apply {
            put("title", trimmedTitle)
        }.toString()
        database.aiChatDao().enqueuePendingOp(
            AiPendingOpEntity(
                opId = "rename:$conversationId:$now",
                conversationId = conversationId,
                opType = AiPendingOpEntity.OP_RENAME,
                payloadJson = renamePayload,
                createdAtMs = now
            )
        )

        if (isTransportConnected()) {
            scope.launch {
                replayPendingOps()
            }
        }

        return OrbitResult.Success(Unit)
    }

    override suspend fun deleteConversation(conversationId: String): OrbitResult<Unit> {
        database.aiChatDao().deleteConversation(conversationId)
        if (_currentConversationId.value == conversationId) {
            _currentConversationId.value = null
            _currentMessages.value = emptyList()
        }

        val now = System.currentTimeMillis()
        database.aiChatDao().enqueuePendingOp(
            AiPendingOpEntity(
                opId = "delete:$conversationId",
                conversationId = conversationId,
                opType = AiPendingOpEntity.OP_DELETE,
                payloadJson = "{}",
                createdAtMs = now
            )
        )

        if (isTransportConnected()) {
            scope.launch {
                replayPendingOps()
            }
        }

        return OrbitResult.Success(Unit)
    }

    internal suspend fun replayPendingOps() {
        if (!isTransportConnected()) return
        outboxMutex.withLock {
            val pending = database.aiChatDao().getPendingOps()
            for (op in pending) {
                if (!isTransportConnected()) break

                database.aiChatDao().updatePendingOpStatus(
                    op.opId,
                    AiPendingOpEntity.STATUS_IN_FLIGHT,
                    op.retryCount,
                    op.lastError
                )

                val result: OrbitResult<*> = when (op.opType) {
                    AiPendingOpEntity.OP_CREATE -> {
                        val title = runCatching {
                            val obj = JSONObject(op.payloadJson)
                            if (obj.has("title") && !obj.isNull("title")) obj.getString("title") else null
                        }.getOrNull()
                        aiTransport.createConversation(title = title, conversationId = op.conversationId)
                    }
                    AiPendingOpEntity.OP_RENAME -> {
                        val title = runCatching { JSONObject(op.payloadJson).optString("title", "") }.getOrDefault("")
                        if (title.isBlank()) {
                            OrbitResult.Error(IllegalArgumentException("Empty title"))
                        } else {
                            aiTransport.renameConversation(op.conversationId, title)
                        }
                    }
                    AiPendingOpEntity.OP_DELETE -> {
                        aiTransport.deleteConversation(op.conversationId)
                    }
                    else -> OrbitResult.Error(IllegalArgumentException("Unknown opType: ${op.opType}"))
                }

                when (result) {
                    is OrbitResult.Success -> {
                        database.aiChatDao().deletePendingOp(op.opId)
                        if (op.opType == AiPendingOpEntity.OP_CREATE || op.opType == AiPendingOpEntity.OP_RENAME) {
                            database.aiChatDao().getConversationById(op.conversationId)?.let { conv ->
                                database.aiChatDao().upsertConversation(conv.copy(isSynced = true))
                            }
                        }
                    }
                    is OrbitResult.Error -> {
                        val errMsg = result.exception.message ?: "Sync failed"
                        val isPermanent = result.exception is IllegalArgumentException ||
                            errMsg.contains("not found", ignoreCase = true) ||
                            errMsg.contains("404") ||
                            errMsg.contains("400")

                        if (isPermanent && op.opType == AiPendingOpEntity.OP_DELETE) {
                            // Already deleted on authoritative host
                            database.aiChatDao().deletePendingOp(op.opId)
                        } else if (isPermanent || op.retryCount + 1 >= MAX_OUTBOX_RETRIES) {
                            database.aiChatDao().updatePendingOpStatus(
                                op.opId,
                                AiPendingOpEntity.STATUS_FAILED_PERMANENT,
                                op.retryCount + 1,
                                errMsg
                            )
                        } else {
                            val nextRetry = op.retryCount + 1
                            database.aiChatDao().updatePendingOpStatus(
                                op.opId,
                                AiPendingOpEntity.STATUS_PENDING,
                                nextRetry,
                                errMsg
                            )
                            val backoffMs = minOf(1000L * (1 shl nextRetry), 16000L)
                            delay(backoffMs)
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    override suspend fun sendMessage(content: String): OrbitResult<Unit> {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return OrbitResult.Success(Unit)

        if (!isTransportConnected()) {
            return OrbitResult.Error(Exception("Not connected to Windows"))
        }

        var convId = _currentConversationId.value
        if (convId == null) {
            val title = if (trimmed.length > 32) trimmed.substring(0, 32).trim() + "..." else trimmed
            val newConvResult = createNewConversation(title)
            if (newConvResult is OrbitResult.Success) {
                convId = newConvResult.data.id
            } else {
                return OrbitResult.Error(Exception("Failed to initialize conversation"))
            }
        }

        val userMsgId = UUID.randomUUID().toString().replace("-", "")
        val assistantMsgId = UUID.randomUUID().toString().replace("-", "")
        val now = System.currentTimeMillis()

        // 1. Insert user message locally
        val userMsgEntity = AiMessageEntity(
            id = userMsgId,
            conversationId = convId,
            role = "user",
            content = trimmed,
            timestampUtc = now,
            generationStatus = "completed",
            isSynced = false
        )
        database.aiChatDao().upsertMessage(userMsgEntity)

        // 2. Insert assistant streaming placeholder locally
        val assistantMsgEntity = AiMessageEntity(
            id = assistantMsgId,
            conversationId = convId,
            role = "assistant",
            content = "",
            timestampUtc = now + 50,
            generationStatus = "streaming",
            isSynced = false
        )
        database.aiChatDao().upsertMessage(assistantMsgEntity)

        loadMessagesFromDb(convId)
        _isStreaming.value = true

        // 3. Launch streaming coroutine
        val currentCId = convId
        streamingJob?.cancel()
        streamingJob = scope.launch {
            val accumulated = StringBuilder()
            val accumulatedThought = StringBuilder()

            val streamResult = aiTransport.streamPrompt(
                conversationId = currentCId,
                prompt = trimmed,
                userMessageId = userMsgId,
                assistantMessageId = assistantMsgId,
                onEvent = { event ->
                    when (event.type) {
                        "chunk" -> {
                            val textChunk = event.text ?: ""
                            accumulated.append(textChunk)
                            _currentMessages.update { list ->
                                list.map { msg ->
                                    if (msg.id == assistantMsgId) {
                                        msg.copy(content = accumulated.toString())
                                    } else msg
                                }
                            }
                        }

                        "thought" -> {
                            val thoughtChunk = event.text ?: ""
                            accumulatedThought.append(thoughtChunk)
                            _currentMessages.update { list ->
                                list.map { msg ->
                                    if (msg.id == assistantMsgId) {
                                        msg.copy(thought = accumulatedThought.toString())
                                    } else msg
                                }
                            }
                        }

                        "tool" -> {
                            val stableCallId = event.toolCallId?.ifBlank { null } ?: run {
                                if (!event.toolDetail.isNullOrBlank()) {
                                    try {
                                        val td = JSONObject(event.toolDetail)
                                        when {
                                            td.has("toolCallId") -> td.getString("toolCallId")
                                            td.has("id") -> td.getString("id")
                                            td.has("callId") -> td.getString("callId")
                                            else -> null
                                        }
                                    } catch (_: Exception) { null }
                                } else null
                            } ?: (event.toolName ?: "tool")

                            val tName = event.toolName?.ifBlank { null } ?: "Tool"
                            val tStatus = event.toolStatus?.ifBlank { null } ?: "running"
                            val tTitle = event.toolTitle

                            val act = AiToolActivity(
                                id = stableCallId,
                                messageId = assistantMsgId,
                                toolCallId = stableCallId,
                                toolName = tName,
                                status = tStatus,
                                detail = event.toolDetail,
                                title = tTitle,
                                timestampUtc = System.currentTimeMillis()
                            )

                            scope.launch {
                                database.aiChatDao().upsertToolActivity(
                                    AiToolActivityEntity(
                                        id = act.id,
                                        messageId = act.messageId,
                                        toolCallId = act.toolCallId,
                                        toolName = act.toolName,
                                        status = act.status,
                                        detail = act.detail,
                                        title = act.title,
                                        timestampUtc = act.timestampUtc
                                    )
                                )
                            }

                            _currentMessages.update { list ->
                                list.map { msg ->
                                    if (msg.id == assistantMsgId) {
                                        msg.copy(toolActivities = reconcileToolActivities(msg.toolActivities, act))
                                    } else msg
                                }
                            }
                        }

                        "permission_request" -> {
                            if (event.requestId != null && event.toolName != null) {
                                _pendingPermissionRequest.value = AiPermissionRequest(
                                    requestId = event.requestId,
                                    toolName = event.toolName,
                                    description = event.description ?: "Antigravity requested permission to run ${event.toolName}",
                                    options = event.options
                                )
                            }
                        }

                        "permission_approved" -> {
                            _pendingPermissionRequest.value = null
                        }

                        "complete" -> {
                            _pendingPermissionRequest.value = null
                            val finalText = event.text ?: accumulated.toString()
                            scope.launch {
                                database.aiChatDao().updateMessageContentAndStatus(
                                    id = assistantMsgId,
                                    content = finalText,
                                    status = "completed",
                                    isSynced = true
                                )
                                database.aiChatDao().updateMessageContentAndStatus(
                                    id = userMsgId,
                                    content = trimmed,
                                    status = "completed",
                                    isSynced = true
                                )
                                loadMessagesFromDb(currentCId)
                            }
                        }

                        "cancelled" -> {
                            _pendingPermissionRequest.value = null
                            scope.launch {
                                database.aiChatDao().updateMessageContentAndStatus(
                                    id = assistantMsgId,
                                    content = accumulated.toString(),
                                    status = "cancelled",
                                    isSynced = true
                                )
                                loadMessagesFromDb(currentCId)
                            }
                        }

                        "conversation_updated" -> {
                            val updatedTitle = event.text
                            if (!updatedTitle.isNullOrBlank()) {
                                scope.launch {
                                    database.aiChatDao().updateConversationTitle(
                                        id = event.conversationId,
                                        title = updatedTitle,
                                        isSynced = true
                                    )
                                }
                            }
                        }

                        "conversation_deleted" -> {
                            scope.launch {
                                database.aiChatDao().deleteConversation(event.conversationId)
                                if (_currentConversationId.value == event.conversationId) {
                                    _currentConversationId.value = null
                                    _currentMessages.value = emptyList()
                                }
                            }
                        }

                        "error" -> {
                            _pendingPermissionRequest.value = null
                            val errText = event.error ?: "An unexpected error occurred."
                            scope.launch {
                                database.aiChatDao().updateMessageContentAndStatus(
                                    id = assistantMsgId,
                                    content = if (accumulated.isNotEmpty()) accumulated.toString() else "Error: $errText",
                                    status = "failed",
                                    isSynced = true
                                )
                                loadMessagesFromDb(currentCId)
                            }
                        }
                    }
                }
            )

            _isStreaming.value = false
            if (streamResult is OrbitResult.Error) {
                Log.w(tag, "Stream error: ${streamResult.exception.message}")
                database.aiChatDao().updateMessageContentAndStatus(
                    id = assistantMsgId,
                    content = if (accumulated.isNotEmpty()) accumulated.toString() else "Failed to connect to Antigravity.",
                    status = "failed",
                    isSynced = false
                )
                loadMessagesFromDb(currentCId)
            }
        }

        return OrbitResult.Success(Unit)
    }

    override suspend fun resolvePermission(optionId: String): OrbitResult<Boolean> {
        val req = _pendingPermissionRequest.value ?: return OrbitResult.Error(Exception("No pending permission request"))
        if (!isTransportConnected()) {
            return OrbitResult.Error(Exception("Not connected"))
        }

        _pendingPermissionRequest.value = null

        return aiTransport.resolvePermission(req.requestId, optionId)
    }

    override suspend fun setPermissionPolicy(policy: String): OrbitResult<Unit> {
        if (!isTransportConnected()) {
            return OrbitResult.Error(Exception("Not connected"))
        }

        val result = aiTransport.setPolicy(policy)
        if (result is OrbitResult.Success) {
            _acpStatus.update { it.copy(permissionMode = policy) }
        }
        return result
    }

    override suspend fun cancelActiveGeneration() {
        val convId = _currentConversationId.value ?: return

        streamingJob?.cancel()
        _isStreaming.value = false
        _pendingPermissionRequest.value = null

        if (isTransportConnected()) {
            scope.launch {
                aiTransport.cancelPrompt(convId)
            }
        }
    }

    companion object {
        private const val MAX_OUTBOX_RETRIES = 5
        private val TERMINAL_STATUSES = setOf("completed", "failed", "cancelled", "done", "error", "success")

        fun reconcileToolActivities(
            existingList: List<AiToolActivity>,
            newActivity: AiToolActivity
        ): List<AiToolActivity> {
            val existingIndex = existingList.indexOfFirst { it.toolCallId == newActivity.toolCallId }
            if (existingIndex < 0) {
                // First event creates one card
                return existingList + newActivity
            }

            val current = existingList[existingIndex]
            val isCurrentTerminal = current.status.lowercase() in TERMINAL_STATUSES
            val isNewTerminal = newActivity.status.lowercase() in TERMINAL_STATUSES

            // Terminal status cannot be downgraded back to running/pending by late/out-of-order events
            val resolvedStatus = if (isCurrentTerminal && !isNewTerminal) {
                current.status
            } else {
                newActivity.status
            }

            // Preserve non-generic tool name
            val resolvedName = if (newActivity.toolName.isNotBlank() && newActivity.toolName != "Tool") {
                newActivity.toolName
            } else {
                current.toolName
            }

            val resolvedDetail = if (!newActivity.detail.isNullOrBlank()) newActivity.detail else current.detail
            val resolvedTitle = newActivity.title ?: current.title

            val updated = current.copy(
                toolName = resolvedName,
                status = resolvedStatus,
                detail = resolvedDetail,
                title = resolvedTitle
            )

            // In-place replacement preserving insertion order
            return existingList.toMutableList().apply {
                set(existingIndex, updated)
            }
        }
    }
}
