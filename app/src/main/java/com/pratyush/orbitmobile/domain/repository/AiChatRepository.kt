package com.pratyush.orbitmobile.domain.repository

import com.pratyush.orbitmobile.core.common.OrbitResult
import com.pratyush.orbitmobile.domain.model.AcpStatus
import com.pratyush.orbitmobile.domain.model.AiConversation
import com.pratyush.orbitmobile.domain.model.AiMessage
import com.pratyush.orbitmobile.domain.model.AiPermissionRequest
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain repository contract for the shared Antigravity AI chat experience.
 * Manages local Room/SQLite caching, remote synchronization with Windows ACP,
 * and live streaming event delivery.
 */
interface AiChatRepository {
    val acpStatus: StateFlow<AcpStatus>
    val conversations: StateFlow<List<AiConversation>>
    val currentConversationId: StateFlow<String?>
    val currentMessages: StateFlow<List<AiMessage>>
    val isStreaming: StateFlow<Boolean>
    val pendingPermissionRequest: StateFlow<AiPermissionRequest?>

    suspend fun refreshStatus()
    suspend fun refreshConversations()
    suspend fun selectConversation(conversationId: String)
    suspend fun createNewConversation(title: String? = null): OrbitResult<AiConversation>
    suspend fun deleteConversation(conversationId: String): OrbitResult<Unit>
    suspend fun sendMessage(content: String): OrbitResult<Unit>
    suspend fun resolvePermission(optionId: String): OrbitResult<Boolean>
    suspend fun setPermissionPolicy(policy: String): OrbitResult<Unit>
    suspend fun cancelActiveGeneration()
}
