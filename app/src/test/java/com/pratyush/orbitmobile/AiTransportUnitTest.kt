package com.pratyush.orbitmobile

import com.pratyush.orbitmobile.core.common.OrbitResult
import com.pratyush.orbitmobile.data.local.database.OrbitDatabase
import com.pratyush.orbitmobile.data.local.database.dao.AiChatDao
import com.pratyush.orbitmobile.data.local.database.dao.DeviceDao
import com.pratyush.orbitmobile.data.local.database.entity.AiConversationEntity
import com.pratyush.orbitmobile.data.local.database.entity.AiMessageEntity
import com.pratyush.orbitmobile.data.local.database.entity.AiPendingOpEntity
import com.pratyush.orbitmobile.data.local.database.entity.AiToolActivityEntity
import com.pratyush.orbitmobile.data.local.database.entity.DeviceEntity
import com.pratyush.orbitmobile.data.remote.ai.LanAiTransport
import com.pratyush.orbitmobile.data.repository.implementations.AiChatRepositoryImpl
import com.pratyush.orbitmobile.domain.model.AcpStatus
import com.pratyush.orbitmobile.domain.model.AiConversation
import com.pratyush.orbitmobile.domain.model.AiMessage
import com.pratyush.orbitmobile.domain.model.AiStreamEvent
import com.pratyush.orbitmobile.domain.model.ClipboardItem
import com.pratyush.orbitmobile.domain.model.ConnectionState
import com.pratyush.orbitmobile.domain.model.Device
import com.pratyush.orbitmobile.domain.repository.AiTransport
import com.pratyush.orbitmobile.domain.repository.ConnectionRepository
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.StringReader
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

class AiTransportUnitTest {

    private class FakeConnectionRepository(
        initialHost: String? = "127.0.0.1",
        initialPort: Int = 8765,
        initialToken: String? = "test-token"
    ) : ConnectionRepository {
        private val _state = MutableStateFlow<ConnectionState>(
            if (initialHost != null) ConnectionState.Connected("win-1", "WinPC", initialHost, initialPort)
            else ConnectionState.Disconnected
        )
        override val connectionState: StateFlow<ConnectionState> = _state
        override var activeHost: String? = initialHost
        override var activePort: Int = initialPort
        override var activeAuthToken: String? = initialToken
        override val clipboardHistory: StateFlow<List<ClipboardItem>> = MutableStateFlow(emptyList())

        override suspend fun connectToDevice(device: Device): OrbitResult<Unit> = OrbitResult.Success(Unit)
        override suspend fun connectManual(host: String, port: Int): OrbitResult<Unit> = OrbitResult.Success(Unit)
        override suspend fun pairWithQr(qrRawPayload: String): OrbitResult<Device> =
            OrbitResult.Error(Exception("Not used"))
        override suspend fun attemptReconnect(): OrbitResult<Unit> = OrbitResult.Success(Unit)
        override suspend fun disconnect(): OrbitResult<Unit> {
            activeHost = null
            activeAuthToken = null
            _state.value = ConnectionState.Disconnected
            return OrbitResult.Success(Unit)
        }
        override suspend fun sendPing(): OrbitResult<Long> = OrbitResult.Success(1L)
        override suspend fun sendTelemetry(
            batteryPercent: Int,
            isCharging: Boolean,
            batteryStatusText: String,
            batteryHealth: String?,
            batteryTemperature: String?,
            chargeSource: String?,
            wifiConnected: Boolean,
            wifiSsid: String?,
            wifiFrequency: String?,
            wifiLinkSpeed: String?,
            bluetoothEnabled: Boolean,
            bluetoothConnected: Boolean,
            bluetoothStatusText: String,
            usedStorageGb: Double,
            totalStorageGb: Double,
            manufacturer: String?
        ): OrbitResult<Unit> = OrbitResult.Success(Unit)
        override suspend fun sendClipboard(
            type: String,
            textContent: String?,
            urlContent: String?,
            imageBase64: String?,
            imageFileName: String?
        ): OrbitResult<Unit> = OrbitResult.Success(Unit)
        override suspend fun updateClipboardSyncState(enabled: Boolean): OrbitResult<Unit> = OrbitResult.Success(Unit)
        override suspend fun clearClipboardHistory(): OrbitResult<Unit> = OrbitResult.Success(Unit)
        override fun receiveIncomingClipboardFromWindows(
            id: String,
            type: String,
            content: String,
            timestamp: Long,
            origin: String
        ) {}
        override suspend fun refreshClipboardHistoryFromPc(): OrbitResult<Unit> = OrbitResult.Success(Unit)
    }

    private class InMemoryAiChatDao : AiChatDao {
        val conversations = linkedMapOf<String, AiConversationEntity>()
        val messages = linkedMapOf<String, AiMessageEntity>()
        val toolActivities = linkedMapOf<String, AiToolActivityEntity>()
        private val convTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 8)

        override fun getConversationsFlow(): Flow<List<AiConversationEntity>> = flow {
            emit(getConversations())
            convTrigger.collect { emit(getConversations()) }
        }

        override suspend fun getConversations(): List<AiConversationEntity> =
            conversations.values.sortedByDescending { it.updatedAtUtc }

        override suspend fun getConversationById(id: String): AiConversationEntity? = conversations[id]

        override suspend fun upsertConversation(conversation: AiConversationEntity) {
            conversations[conversation.id] = conversation
            convTrigger.tryEmit(Unit)
        }

        override suspend fun upsertConversations(conversations: List<AiConversationEntity>) {
            conversations.forEach { this.conversations[it.id] = it }
            convTrigger.tryEmit(Unit)
        }

        override suspend fun updateConversationTitle(id: String, title: String, updatedAtUtc: Long, isSynced: Boolean) {
            conversations[id]?.let {
                conversations[id] = it.copy(title = title, updatedAtUtc = updatedAtUtc, isSynced = isSynced)
                convTrigger.tryEmit(Unit)
            }
        }

        override suspend fun deleteConversation(id: String) {
            conversations.remove(id)
            messages.values.removeAll { it.conversationId == id }
            convTrigger.tryEmit(Unit)
        }

        override fun getMessagesFlow(conversationId: String): Flow<List<AiMessageEntity>> = flow {
            emit(getMessages(conversationId))
        }

        override suspend fun getMessages(conversationId: String): List<AiMessageEntity> =
            messages.values.filter { it.conversationId == conversationId }.sortedBy { it.timestampUtc }

        override suspend fun upsertMessage(message: AiMessageEntity) {
            messages[message.id] = message
        }

        override suspend fun upsertMessages(messages: List<AiMessageEntity>) {
            messages.forEach { this.messages[it.id] = it }
        }

        override suspend fun updateMessageContentAndStatus(id: String, content: String, status: String, isSynced: Boolean) {
            messages[id]?.let {
                messages[id] = it.copy(content = content, generationStatus = status, isSynced = isSynced)
            }
        }

        override suspend fun deleteMessagesForConversation(conversationId: String) {
            messages.values.removeAll { it.conversationId == conversationId }
        }

        override suspend fun getToolActivities(messageId: String): List<AiToolActivityEntity> =
            toolActivities.values.filter { it.messageId == messageId }

        override suspend fun upsertToolActivity(activity: AiToolActivityEntity) {
            toolActivities[activity.id] = activity
        }

        val pendingOps = linkedMapOf<String, AiPendingOpEntity>()

        override suspend fun enqueuePendingOp(op: AiPendingOpEntity) {
            val hasDelete = pendingOps.values.any { it.conversationId == op.conversationId && it.opType == AiPendingOpEntity.OP_DELETE }
            if (hasDelete && op.opType != AiPendingOpEntity.OP_DELETE) return
            if (op.opType == AiPendingOpEntity.OP_DELETE) {
                pendingOps.entries.removeAll { it.value.conversationId == op.conversationId && it.value.opType != AiPendingOpEntity.OP_DELETE }
            } else if (op.opType == AiPendingOpEntity.OP_RENAME) {
                pendingOps.entries.removeAll { it.value.conversationId == op.conversationId && it.value.opType == AiPendingOpEntity.OP_RENAME && it.value.status == AiPendingOpEntity.STATUS_PENDING }
            }
            pendingOps[op.opId] = op
        }

        override suspend fun getPendingOps(): List<AiPendingOpEntity> =
            pendingOps.values.filter { it.status != AiPendingOpEntity.STATUS_FAILED_PERMANENT }.sortedBy { it.createdAtMs }

        override suspend fun updatePendingOpStatus(opId: String, status: String, retryCount: Int, lastError: String?) {
            pendingOps[opId]?.let {
                pendingOps[opId] = it.copy(status = status, retryCount = retryCount, lastError = lastError)
            }
        }

        override suspend fun deletePendingOp(opId: String) {
            pendingOps.remove(opId)
        }

        override suspend fun resetInFlightPendingOps() {
            pendingOps.replaceAll { _, v -> if (v.status == AiPendingOpEntity.STATUS_IN_FLIGHT) v.copy(status = AiPendingOpEntity.STATUS_PENDING) else v }
        }
    }

    private class FakeOrbitDatabase(private val aiDao: AiChatDao) : OrbitDatabase {
        override fun deviceDao(): DeviceDao = object : DeviceDao {
            override fun getAllDevices(): Flow<List<DeviceEntity>> = flow { emit(emptyList()) }
            override fun getPairedDevices(): Flow<List<DeviceEntity>> = flow { emit(emptyList()) }
            override suspend fun insertDevice(device: DeviceEntity) {}
            override suspend fun deleteDevice(deviceId: String) {}
        }

        override fun aiChatDao(): AiChatDao = aiDao
    }

    @Test
    fun `LanAiTransport endpoints send expected methods headers and parse responses`() = runBlocking {
        val requests = CopyOnWriteArrayList<Triple<String, String, String?>>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/mobile/ai/") { exchange ->
            val path = exchange.requestURI.path
            val method = exchange.requestMethod
            val auth = exchange.requestHeaders.getFirst("Authorization")
            val body = exchange.requestBody.bufferedReader().readText()
            requests.add(Triple(method, path, auth))

            val (status, resp) = when {
                method == "GET" && path == "/api/mobile/ai/status" ->
                    200 to """{"IsAvailable":true,"State":"ready","Backend":"antigravity-acp","PermissionMode":"AutoApproveAll"}"""
                method == "GET" && path == "/api/mobile/ai/conversations" ->
                    200 to """[{"Id":"c1","Title":"Chat 1","BackendId":"antigravity-acp","Origin":"Mobile"}]"""
                method == "POST" && path == "/api/mobile/ai/conversations/new" ->
                    200 to """{"Id":"c2","Title":"Created","BackendId":"antigravity-acp","Origin":"Mobile"}"""
                method == "PUT" && path == "/api/mobile/ai/conversations/c1" -> {
                    assertTrue(body.contains("Renamed"))
                    200 to """{"Id":"c1","Title":"Renamed","BackendId":"antigravity-acp","Origin":"Mobile"}"""
                }
                method == "DELETE" && path == "/api/mobile/ai/conversations/c1" ->
                    200 to """{"success":true}"""
                method == "POST" && path == "/api/mobile/ai/permission/resolve" ->
                    200 to """{"success":true}"""
                method == "POST" && path == "/api/mobile/ai/policy" ->
                    200 to """{"success":true}"""
                method == "POST" && path == "/api/mobile/ai/cancel" ->
                    200 to """{"cancelled":true}"""
                else -> 404 to """{"error":"Not Found"}"""
            }

            val bytes = resp.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()

        try {
            val connRepo = FakeConnectionRepository("127.0.0.1", server.address.port, "secret-123")
            val transport: AiTransport = LanAiTransport(connRepo)

            val status = transport.getStatus()
            assertTrue(status is OrbitResult.Success && status.data.isAvailable)

            val convs = transport.getConversations()
            assertTrue(convs is OrbitResult.Success && convs.data.size == 1 && convs.data[0].id == "c1")

            val created = transport.createConversation("Created", "c2")
            assertTrue(created is OrbitResult.Success && created.data.id == "c2")

            val renamed = transport.renameConversation("c1", "Renamed")
            assertTrue(renamed is OrbitResult.Success && renamed.data.title == "Renamed")

            assertTrue(transport.deleteConversation("c1") is OrbitResult.Success)
            assertTrue(transport.resolvePermission("req-1", "allow") is OrbitResult.Success)
            assertTrue(transport.setPolicy("AlwaysAsk") is OrbitResult.Success)
            assertTrue(transport.cancelPrompt("c1") is OrbitResult.Success)

            assertTrue(requests.all { it.third == "Bearer secret-123" })
            assertEquals(8, requests.size)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `LanAiTransport parseSseStream parses chunks tools permissions and ignores malformed JSON`() = runBlocking {
        val ssePayload = """
            event: chunk
            data: {"Type":"chunk","ConversationId":"c1","MessageId":"m1","Text":"Hello "}

            event: tool
            data: {"Type":"tool","ConversationId":"c1","ToolName":"read_file","ToolStatus":"running","ToolDetail":"{\"toolCallId\":\"tc-9\"}"}

            event: message
            data: {not-valid-json}

            event: permission_request
            data: {"Type":"permission_request","ConversationId":"c1","RequestId":"r1","ToolName":"run_command","Options":[{"optionId":"allow","name":"Allow"}]}

            event: complete
            data: {"Type":"complete","ConversationId":"c1","MessageId":"m1","Text":"Hello world"}
        """.trimIndent()

        val transport = LanAiTransport(FakeConnectionRepository())
        val events = mutableListOf<AiStreamEvent>()
        transport.parseSseStream(BufferedReader(StringReader(ssePayload)), "fallback") {
            events.add(it)
        }

        assertEquals(4, events.size)
        assertEquals("chunk", events[0].type)
        assertEquals("Hello ", events[0].text)
        assertEquals("tool", events[1].type)
        assertEquals("tc-9", events[1].toolCallId)
        assertEquals("permission_request", events[2].type)
        assertEquals("allow", events[2].options.first().optionId)
        assertEquals("complete", events[3].type)
        assertEquals("Hello world", events[3].text)
    }

    @Test
    fun `LanAiTransport streamPrompt propagates cancellation and HTTP errors`() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/mobile/ai/prompt") { exchange ->
            exchange.responseHeaders.add("Content-Type", "text/event-stream")
            exchange.sendResponseHeaders(200, 0)
            val out = exchange.responseBody
            out.write("event: chunk\ndata: {\"Type\":\"chunk\",\"ConversationId\":\"c1\",\"Text\":\"Part1\"}\n\n".toByteArray())
            out.flush()
            // Hold stream open until client disconnects/cancels
            try {
                Thread.sleep(5000)
            } catch (_: InterruptedException) {
            } finally {
                runCatching { out.close() }
            }
        }
        server.createContext("/api/mobile/ai/status") { exchange ->
            val bytes = """{"error":"Server Error"}""".toByteArray()
            exchange.sendResponseHeaders(500, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()

        try {
            val connRepo = FakeConnectionRepository("127.0.0.1", server.address.port, "tok")
            val transport = LanAiTransport(connRepo)

            // HTTP 500 error propagation
            val statusErr = transport.getStatus()
            assertTrue(statusErr is OrbitResult.Error)

            // Missing credentials error propagation
            val disconnectedTransport = LanAiTransport(FakeConnectionRepository(null, 8765, null))
            assertTrue(disconnectedTransport.getStatus() is OrbitResult.Error)

            // Stream cancellation
            val received = CopyOnWriteArrayList<AiStreamEvent>()
            var caughtCancel = false
            val job = launch {
                try {
                    transport.streamPrompt("c1", "hi") { received.add(it) }
                } catch (_: CancellationException) {
                    caughtCancel = true
                }
            }
            delay(250)
            job.cancel()
            job.join()

            assertTrue(received.isNotEmpty())
            assertTrue(caughtCancel)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `AiChatRepositoryImpl delegates operations to AiTransport and updates SQLite`() = runBlocking {
        val dao = InMemoryAiChatDao()
        val db = FakeOrbitDatabase(dao)
        val connRepo = FakeConnectionRepository()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        val called = CopyOnWriteArrayList<String>()
        val fakeTransport = object : AiTransport {
            override suspend fun getStatus(): OrbitResult<AcpStatus> {
                called.add("getStatus")
                return OrbitResult.Success(AcpStatus(isAvailable = true, state = "ready"))
            }

            override suspend fun getConversations(): OrbitResult<List<AiConversation>> {
                called.add("getConversations")
                return OrbitResult.Success(listOf(AiConversation(id = "c1", title = "Remote Chat")))
            }

            override suspend fun getConversation(conversationId: String): OrbitResult<Pair<AiConversation, List<AiMessage>>> {
                called.add("getConversation:$conversationId")
                return OrbitResult.Success(AiConversation(id = conversationId, title = "Remote Chat") to emptyList())
            }

            override suspend fun createConversation(title: String?, conversationId: String?): OrbitResult<AiConversation> {
                called.add("createConversation:${conversationId ?: ""}")
                return OrbitResult.Success(AiConversation(id = conversationId ?: "new", title = title ?: "New Chat"))
            }

            override suspend fun renameConversation(conversationId: String, title: String): OrbitResult<AiConversation> {
                called.add("renameConversation:$conversationId:$title")
                return OrbitResult.Success(AiConversation(id = conversationId, title = title))
            }

            override suspend fun deleteConversation(conversationId: String): OrbitResult<Unit> {
                called.add("deleteConversation:$conversationId")
                return OrbitResult.Success(Unit)
            }

            override suspend fun streamPrompt(
                conversationId: String,
                prompt: String,
                userMessageId: String?,
                assistantMessageId: String?,
                onEvent: (AiStreamEvent) -> Unit
            ): OrbitResult<Unit> {
                called.add("streamPrompt:$conversationId")
                onEvent(AiStreamEvent(type = "chunk", conversationId = conversationId, text = "Hello"))
                onEvent(AiStreamEvent(type = "complete", conversationId = conversationId, text = "Hello!"))
                return OrbitResult.Success(Unit)
            }

            override suspend fun cancelPrompt(conversationId: String): OrbitResult<Unit> {
                called.add("cancelPrompt:$conversationId")
                return OrbitResult.Success(Unit)
            }

            override suspend fun resolvePermission(requestId: String, optionId: String): OrbitResult<Boolean> {
                called.add("resolvePermission:$requestId:$optionId")
                return OrbitResult.Success(true)
            }

            override suspend fun setPolicy(policy: String): OrbitResult<Unit> {
                called.add("setPolicy:$policy")
                return OrbitResult.Success(Unit)
            }
        }

        val repo = AiChatRepositoryImpl(connRepo, fakeTransport, db, scope)
        try {
            delay(150)
            assertTrue(called.contains("getStatus"))
            assertTrue(called.contains("getConversations"))
            assertNotNull(dao.getConversationById("c1"))

            repo.selectConversation("c1")
            delay(100)
            assertTrue(called.contains("getConversation:c1"))

            assertTrue(repo.sendMessage("Test prompt") is OrbitResult.Success)
            delay(150)
            assertTrue(called.contains("streamPrompt:c1"))
            val msgs = dao.getMessages("c1")
            assertEquals(2, msgs.size)
            assertEquals("Hello!", msgs.last().content)

            assertTrue(repo.renameConversation("c1", "Updated Title") is OrbitResult.Success)
            delay(100)
            assertTrue(called.contains("renameConversation:c1:Updated Title"))
            assertEquals("Updated Title", dao.getConversationById("c1")?.title)

            assertTrue(repo.setPermissionPolicy("AlwaysAsk") is OrbitResult.Success)
            assertEquals("AlwaysAsk", repo.acpStatus.value.permissionMode)

            assertTrue(repo.deleteConversation("c1") is OrbitResult.Success)
            delay(100)
            assertTrue(called.contains("deleteConversation:c1"))
            assertFalse(dao.conversations.containsKey("c1"))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `Offline outbox queues create rename delete and replays in order on reconnect`() = runBlocking {
        val dao = InMemoryAiChatDao()
        val db = FakeOrbitDatabase(dao)
        val connRepo = FakeConnectionRepository(initialHost = null, initialPort = 8765, initialToken = null)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        val replayed = CopyOnWriteArrayList<String>()
        val transport = object : AiTransport {
            override suspend fun getStatus() = OrbitResult.Success(AcpStatus(isAvailable = true))
            override suspend fun getConversations() = OrbitResult.Success(emptyList<AiConversation>())
            override suspend fun getConversation(conversationId: String) = OrbitResult.Error(Exception("unused"))
            override suspend fun createConversation(title: String?, conversationId: String?): OrbitResult<AiConversation> {
                replayed.add("create:$conversationId:${title ?: ""}")
                return OrbitResult.Success(AiConversation(id = conversationId ?: "", title = title ?: "New Chat"))
            }
            override suspend fun renameConversation(conversationId: String, title: String): OrbitResult<AiConversation> {
                replayed.add("rename:$conversationId:$title")
                return OrbitResult.Success(AiConversation(id = conversationId, title = title))
            }
            override suspend fun deleteConversation(conversationId: String): OrbitResult<Unit> {
                replayed.add("delete:$conversationId")
                return OrbitResult.Success(Unit)
            }
            override suspend fun streamPrompt(conversationId: String, prompt: String, userMessageId: String?, assistantMessageId: String?, onEvent: (AiStreamEvent) -> Unit) = OrbitResult.Success(Unit)
            override suspend fun cancelPrompt(conversationId: String) = OrbitResult.Success(Unit)
            override suspend fun resolvePermission(requestId: String, optionId: String) = OrbitResult.Success(true)
            override suspend fun setPolicy(policy: String) = OrbitResult.Success(Unit)
        }

        val repo = AiChatRepositoryImpl(connRepo, transport, db, scope)
        try {
            // 1. Offline create + rename on conv A, offline create + rename + delete on conv B
            val createdA = repo.createNewConversation("Draft A") as OrbitResult.Success
            val idA = createdA.data.id
            repo.renameConversation(idA, "Final A")

            val createdB = repo.createNewConversation("Draft B") as OrbitResult.Success
            val idB = createdB.data.id
            repo.renameConversation(idB, "Renamed B")
            repo.deleteConversation(idB)
            // Rename after delete must be ignored by tombstone guard
            repo.renameConversation(idB, "Should Not Resurrect")

            assertEquals(0, replayed.size)
            assertTrue(dao.getPendingOps().any { it.conversationId == idA && it.opType == AiPendingOpEntity.OP_RENAME })
            assertEquals(1, dao.getPendingOps().count { it.conversationId == idB })
            assertEquals(AiPendingOpEntity.OP_DELETE, dao.getPendingOps().first { it.conversationId == idB }.opType)

            // 2. Simulate reconnect
            connRepo.activeHost = "127.0.0.1"
            connRepo.activeAuthToken = "tok"
            // Trigger replay directly
            val stateField = FakeConnectionRepository::class.java.getDeclaredField("_state")
            stateField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            (stateField.get(connRepo) as MutableStateFlow<ConnectionState>).value =
                ConnectionState.Connected("win-1", "WinPC", "127.0.0.1", 8765)

            delay(250)

            assertTrue(replayed.contains("create:$idA:Draft A"))
            assertTrue(replayed.contains("rename:$idA:Final A"))
            assertTrue(replayed.contains("delete:$idB"))
            assertFalse(replayed.any { it.contains("Should Not Resurrect") })
            assertTrue(dao.getPendingOps().isEmpty())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `Offline outbox resets in_flight ops on restart and marks permanent errors without infinite retries`() = runBlocking {
        val dao = InMemoryAiChatDao()
        val db = FakeOrbitDatabase(dao)
        val connRepo = FakeConnectionRepository()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        // Pre-populate an in_flight op from a prior app session
        dao.enqueuePendingOp(
            AiPendingOpEntity(
                opId = "rename:missing-conv:100",
                conversationId = "missing-conv",
                opType = AiPendingOpEntity.OP_RENAME,
                payloadJson = """{"title":"Ghost"}""",
                createdAtMs = 100L,
                status = AiPendingOpEntity.STATUS_IN_FLIGHT
            )
        )

        val transport = object : AiTransport {
            override suspend fun getStatus() = OrbitResult.Success(AcpStatus(isAvailable = true))
            override suspend fun getConversations() = OrbitResult.Success(emptyList<AiConversation>())
            override suspend fun getConversation(conversationId: String) = OrbitResult.Error(Exception("404"))
            override suspend fun createConversation(title: String?, conversationId: String?) = OrbitResult.Error(Exception("404"))
            override suspend fun renameConversation(conversationId: String, title: String) =
                OrbitResult.Error(Exception("HTTP 404 Conversation not found"))
            override suspend fun deleteConversation(conversationId: String) = OrbitResult.Success(Unit)
            override suspend fun streamPrompt(conversationId: String, prompt: String, userMessageId: String?, assistantMessageId: String?, onEvent: (AiStreamEvent) -> Unit) = OrbitResult.Success(Unit)
            override suspend fun cancelPrompt(conversationId: String) = OrbitResult.Success(Unit)
            override suspend fun resolvePermission(requestId: String, optionId: String) = OrbitResult.Success(true)
            override suspend fun setPolicy(policy: String) = OrbitResult.Success(Unit)
        }

        val repo = AiChatRepositoryImpl(connRepo, transport, db, scope)
        try {
            delay(250)
            // Must have been reset from in_flight -> replayed -> marked failed_permanent (not in active pendingOps)
            assertTrue(dao.getPendingOps().isEmpty())
            assertEquals(AiPendingOpEntity.STATUS_FAILED_PERMANENT, dao.pendingOps["rename:missing-conv:100"]?.status)
        } finally {
            scope.cancel()
        }
    }
}
