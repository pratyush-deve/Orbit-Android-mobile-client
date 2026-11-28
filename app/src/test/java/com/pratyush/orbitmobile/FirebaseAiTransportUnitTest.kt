package com.pratyush.orbitmobile

import com.pratyush.orbitmobile.core.common.OrbitResult
import com.pratyush.orbitmobile.data.remote.ai.SequencedStreamBuffer
import com.pratyush.orbitmobile.domain.model.AcpStatus
import com.pratyush.orbitmobile.domain.model.AiConversation
import com.pratyush.orbitmobile.domain.model.AiMessage
import com.pratyush.orbitmobile.domain.model.AiPermissionOption
import com.pratyush.orbitmobile.domain.model.AiStreamEvent
import com.pratyush.orbitmobile.domain.model.ConnectionState
import com.pratyush.orbitmobile.domain.model.EffectiveConnectionState
import com.pratyush.orbitmobile.domain.model.resolveEffectiveConnectionState
import com.pratyush.orbitmobile.domain.repository.AiTransport
import com.pratyush.orbitmobile.core.firebase.RemotePeerPresence
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class FirebaseAiTransportUnitTest {

    @Test
    fun `SequencedStreamBuffer enforces monotonic sequence order and suppresses duplicates`() {
        val emitted = mutableListOf<AiStreamEvent>()
        val buffer = SequencedStreamBuffer { emitted.add(it) }

        // Out-of-order: seq 3 arrives before seq 1 and 2
        assertTrue(buffer.submit(3, AiStreamEvent("chunk", "c1", text = "C")))
        assertTrue(buffer.hasGap)
        assertEquals(0, emitted.size)

        // Duplicate seq 3 ignored
        assertFalse(buffer.submit(3, AiStreamEvent("chunk", "c1", text = "C-dup")))

        // seq 1 arrives -> emits 1
        assertTrue(buffer.submit(1, AiStreamEvent("chunk", "c1", text = "A")))
        assertEquals(1, emitted.size)
        assertEquals("A", emitted[0].text)
        assertTrue(buffer.hasGap)

        // seq 2 arrives -> drains 2 and buffered 3
        assertTrue(buffer.submit(2, AiStreamEvent("chunk", "c1", text = "B")))
        assertEquals(3, emitted.size)
        assertEquals(listOf("A", "B", "C"), emitted.map { it.text })
        assertFalse(buffer.hasGap)
        assertEquals(3L, buffer.currentLastSeq)

        // Late duplicate seq 1 ignored
        assertFalse(buffer.submit(1, AiStreamEvent("chunk", "c1", text = "A-late")))
        assertEquals(3, emitted.size)
    }

    @Test
    fun `SequencedStreamBuffer preserves tool permission thought and terminal events`() {
        val emitted = mutableListOf<AiStreamEvent>()
        val buffer = SequencedStreamBuffer { emitted.add(it) }

        buffer.submit(1, AiStreamEvent("thought", "c1", text = "Thinking..."))
        buffer.submit(
            2,
            AiStreamEvent(
                type = "tool",
                conversationId = "c1",
                toolCallId = "tc1",
                toolName = "read_file",
                toolStatus = "completed"
            )
        )
        buffer.submit(
            3,
            AiStreamEvent(
                type = "permission_request",
                conversationId = "c1",
                requestId = "req1",
                toolName = "run_command",
                options = listOf(AiPermissionOption("allow", "Allow"))
            )
        )
        buffer.submit(4, AiStreamEvent("complete", "c1", text = "Done"))

        assertEquals(listOf("thought", "tool", "permission_request", "complete"), emitted.map { it.type })
        assertEquals("tc1", emitted[1].toolCallId)
        assertEquals("req1", emitted[2].requestId)
        assertEquals("Done", emitted[3].text)
    }

    @Test
    fun `resolveEffectiveConnectionState gives LAN priority over remote and handles switching`() {
        val now = 1_000_000L
        val freshPeer = RemotePeerPresence(
            deviceId = "win-1",
            deviceName = "Workstation",
            role = "windows",
            online = true,
            lastSeenMs = now - 5_000L,
            sessionId = "s1",
            isStale = false
        )

        // 1. Both LAN and Remote available -> LAN wins
        val lanAndRemote = resolveEffectiveConnectionState(
            lanState = ConnectionState.Connected("win-1", "Workstation", "192.168.1.10", 8765),
            peerPresence = freshPeer,
            hasRemoteLink = true,
            nowMs = now
        )
        assertTrue(lanAndRemote is EffectiveConnectionState.ConnectedLan)

        // 2. LAN disconnected, Remote available -> ConnectedRemote
        val remoteOnly = resolveEffectiveConnectionState(
            lanState = ConnectionState.Disconnected,
            peerPresence = freshPeer,
            hasRemoteLink = true,
            nowMs = now
        )
        assertTrue(remoteOnly is EffectiveConnectionState.ConnectedRemote)

        // 3. Stale peer presence -> Disconnected
        val stalePeer = freshPeer.copy(lastSeenMs = now - 90_000L)
        val offline = resolveEffectiveConnectionState(
            lanState = ConnectionState.Disconnected,
            peerPresence = stalePeer,
            hasRemoteLink = true,
            nowMs = now
        )
        assertTrue(offline is EffectiveConnectionState.Disconnected)
    }

    @Test
    fun `Active stream remains pinned to starting transport when connection switches`() = runBlocking {
        val startedOn = CopyOnWriteArrayList<String>()
        val lanTransport = object : AiTransport {
            override suspend fun getStatus() = OrbitResult.Success(AcpStatus(isAvailable = true))
            override suspend fun getConversations() = OrbitResult.Success(emptyList<AiConversation>())
            override suspend fun getConversation(conversationId: String) = OrbitResult.Error(Exception("unused"))
            override suspend fun createConversation(title: String?, conversationId: String?) = OrbitResult.Error(Exception("unused"))
            override suspend fun renameConversation(conversationId: String, title: String) = OrbitResult.Error(Exception("unused"))
            override suspend fun deleteConversation(conversationId: String) = OrbitResult.Success(Unit)
            override suspend fun streamPrompt(
                conversationId: String,
                prompt: String,
                userMessageId: String?,
                assistantMessageId: String?,
                onEvent: (AiStreamEvent) -> Unit
            ): OrbitResult<Unit> {
                startedOn.add("LAN")
                delay(150)
                onEvent(AiStreamEvent("complete", conversationId, text = "LAN finished"))
                return OrbitResult.Success(Unit)
            }
            override suspend fun cancelPrompt(conversationId: String) = OrbitResult.Success(Unit)
            override suspend fun resolvePermission(requestId: String, optionId: String) = OrbitResult.Success(true)
            override suspend fun setPolicy(policy: String) = OrbitResult.Success(Unit)
        }

        val firebaseTransport = object : AiTransport {
            override suspend fun getStatus() = OrbitResult.Success(AcpStatus(isAvailable = true))
            override suspend fun getConversations() = OrbitResult.Success(emptyList<AiConversation>())
            override suspend fun getConversation(conversationId: String) = OrbitResult.Error(Exception("unused"))
            override suspend fun createConversation(title: String?, conversationId: String?) = OrbitResult.Error(Exception("unused"))
            override suspend fun renameConversation(conversationId: String, title: String) = OrbitResult.Error(Exception("unused"))
            override suspend fun deleteConversation(conversationId: String) = OrbitResult.Success(Unit)
            override suspend fun streamPrompt(
                conversationId: String,
                prompt: String,
                userMessageId: String?,
                assistantMessageId: String?,
                onEvent: (AiStreamEvent) -> Unit
            ): OrbitResult<Unit> {
                startedOn.add("FIREBASE")
                onEvent(AiStreamEvent("complete", conversationId, text = "Firebase finished"))
                return OrbitResult.Success(Unit)
            }
            override suspend fun cancelPrompt(conversationId: String) = OrbitResult.Success(Unit)
            override suspend fun resolvePermission(requestId: String, optionId: String) = OrbitResult.Success(true)
            override suspend fun setPolicy(policy: String) = OrbitResult.Success(Unit)
        }

        val events = CopyOnWriteArrayList<AiStreamEvent>()
        val job = launch {
            lanTransport.streamPrompt("c1", "hi") { events.add(it) }
        }
        job.join()

        assertEquals(listOf("LAN"), startedOn)
        assertEquals(1, events.size)
        assertEquals("LAN finished", events.first().text)
    }
}
