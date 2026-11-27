package com.pratyush.orbitmobile.data.repository.implementations

import android.util.Log
import com.pratyush.orbitmobile.core.common.OrbitResult
import com.pratyush.orbitmobile.data.local.database.OrbitDatabase
import com.pratyush.orbitmobile.data.local.database.entity.AiConversationEntity
import com.pratyush.orbitmobile.data.local.database.entity.AiMessageEntity
import com.pratyush.orbitmobile.data.local.database.entity.AiToolActivityEntity
import com.pratyush.orbitmobile.data.remote.RemoteDataSource
import com.pratyush.orbitmobile.domain.model.AcpStatus
import com.pratyush.orbitmobile.domain.model.AiConversation
import com.pratyush.orbitmobile.domain.model.AiMessage
import com.pratyush.orbitmobile.domain.model.AiPermissionRequest
import com.pratyush.orbitmobile.domain.model.AiToolActivity
import com.pratyush.orbitmobile.domain.model.ConnectionState
import com.pratyush.orbitmobile.domain.repository.AiChatRepository
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
import org.json.JSONObject
import java.util.UUID

/**
 * Production implementation of [AiChatRepository].
 * Local persistence via [OrbitDatabase] (Room/SQLite), live LAN synchronization with Windows ACP,
 * and zero fallback to mock models.
 */
class AiChatRepositoryImpl(
    private val connectionRepository: ConnectionRepository,
    private val remoteDataSource: RemoteDataSource,
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

    init {
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

        // 2. Observe connection state: when connected, sync with Windows
        scope.launch {
            connectionRepository.connectionState.collect { state ->
                if (state is ConnectionState.Connected) {
                    startStatusMonitoring()
                    refreshConversations()
                } else {
                    stopStatusMonitoring()
                    _acpStatus.update { it.copy(isAvailable = false, state = "unavailable") }
                }
            }
        }
    }

    private fun startStatusMonitoring() {
        statusPollJob?.cancel()
        statusPollJob = scope.launch {
            while (isActive) {
                refreshStatus()
                delay(4000L)
            }
        }
    }

    private fun stopStatusMonitoring() {
        statusPollJob?.cancel()
        statusPollJob = null
    }

    override suspend fun refreshStatus() {
        val host = connectionRepository.activeHost ?: return
        val port = connectionRepository.activePort ?: return
        val token = connectionRepository.activeAuthToken ?: return

        when (val result = remoteDataSource.fetchAiStatus(host, port, token)) {
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
        val host = connectionRepository.activeHost ?: return
        val port = connectionRepository.activePort ?: return
        val token = connectionRepository.activeAuthToken ?: return

        when (val result = remoteDataSource.fetchAiConversations(host, port, token)) {
            is OrbitResult.Success -> {
                val remoteList = result.data
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
        val host = connectionRepository.activeHost ?: return
        val port = connectionRepository.activePort ?: return
        val token = connectionRepository.activeAuthToken ?: return

        scope.launch {
            when (val result = remoteDataSource.fetchAiConversation(host, port, token, conversationId)) {
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

        // Sync with Windows passing the exact same newId
        val host = connectionRepository.activeHost
        val port = connectionRepository.activePort
        val token = connectionRepository.activeAuthToken

        if (host != null && port != null && token != null) {
            scope.launch {
                when (val result = remoteDataSource.createAiConversation(host, port, token, title, conversationId = newId)) {
                    is OrbitResult.Success -> {
                        val remoteConv = result.data
                        database.aiChatDao().upsertConversation(
                            AiConversationEntity(
                                id = remoteConv.id,
                                title = remoteConv.title,
                                backendId = remoteConv.backendId,
                                workspacePath = remoteConv.workspacePath,
                                createdAtUtc = remoteConv.createdAtUtc,
                                updatedAtUtc = remoteConv.updatedAtUtc,
                                isPinned = remoteConv.isPinned,
                                isArchived = remoteConv.isArchived,
                                origin = remoteConv.origin,
                                isSynced = true
                            )
                        )
                    }
                    is OrbitResult.Error -> {
                        Log.w(tag, "Remote creation failed: ${result.exception.message}")
                    }
                    else -> Unit
                }
            }
        }

        return OrbitResult.Success(localConv)
    }

    override suspend fun deleteConversation(conversationId: String): OrbitResult<Unit> {
        database.aiChatDao().deleteConversation(conversationId)
        if (_currentConversationId.value == conversationId) {
            _currentConversationId.value = null
            _currentMessages.value = emptyList()
        }

        val host = connectionRepository.activeHost
        val port = connectionRepository.activePort
        val token = connectionRepository.activeAuthToken

        if (host != null && port != null && token != null) {
            scope.launch {
                remoteDataSource.deleteAiConversation(host, port, token, conversationId)
            }
        }

        return OrbitResult.Success(Unit)
    }

    override suspend fun sendMessage(content: String): OrbitResult<Unit> {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return OrbitResult.Success(Unit)

        val host = connectionRepository.activeHost ?: return OrbitResult.Error(Exception("Not connected to Windows"))
        val port = connectionRepository.activePort ?: return OrbitResult.Error(Exception("Not connected to Windows"))
        val token = connectionRepository.activeAuthToken ?: return OrbitResult.Error(Exception("Unauthorized"))

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

            val streamResult = remoteDataSource.streamAiPrompt(
                host = host,
                port = port,
                authToken = token,
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
        val host = connectionRepository.activeHost ?: return OrbitResult.Error(Exception("Not connected"))
        val port = connectionRepository.activePort ?: return OrbitResult.Error(Exception("Not connected"))
        val token = connectionRepository.activeAuthToken ?: return OrbitResult.Error(Exception("Unauthorized"))

        _pendingPermissionRequest.value = null

        return remoteDataSource.resolveAiPermission(host, port, token, req.requestId, optionId)
    }

    override suspend fun setPermissionPolicy(policy: String): OrbitResult<Unit> {
        val host = connectionRepository.activeHost ?: return OrbitResult.Error(Exception("Not connected"))
        val port = connectionRepository.activePort ?: return OrbitResult.Error(Exception("Not connected"))
        val token = connectionRepository.activeAuthToken ?: return OrbitResult.Error(Exception("Unauthorized"))

        val result = remoteDataSource.setAiPolicy(host, port, token, policy)
        if (result is OrbitResult.Success) {
            _acpStatus.update { it.copy(permissionMode = policy) }
        }
        return result
    }

    override suspend fun cancelActiveGeneration() {
        val convId = _currentConversationId.value ?: return
        val host = connectionRepository.activeHost ?: return
        val port = connectionRepository.activePort ?: return
        val token = connectionRepository.activeAuthToken ?: return

        streamingJob?.cancel()
        _isStreaming.value = false
        _pendingPermissionRequest.value = null

        scope.launch {
            remoteDataSource.cancelAiPrompt(host, port, token, convId)
        }
    }

    companion object {
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
