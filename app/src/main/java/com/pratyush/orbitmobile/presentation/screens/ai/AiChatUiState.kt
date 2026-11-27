package com.pratyush.orbitmobile.presentation.screens.ai

import com.pratyush.orbitmobile.domain.model.AcpStatus
import com.pratyush.orbitmobile.domain.model.AiConversation
import com.pratyush.orbitmobile.domain.model.AiMessage
import com.pratyush.orbitmobile.domain.model.AiPermissionRequest

/**
 * UI state for the ORBIT Mobile AI Chat screen.
 */
data class AiChatUiState(
    val acpStatus: AcpStatus = AcpStatus(isAvailable = false),
    val conversations: List<AiConversation> = emptyList(),
    val currentConversationId: String? = null,
    val currentConversation: AiConversation? = null,
    val messages: List<AiMessage> = emptyList(),
    val inputText: String = "",
    val isStreaming: Boolean = false,
    val pendingPermissionRequest: AiPermissionRequest? = null,
    val permissionPolicy: String = "AutoApproveSafe",
    val searchQuery: String = "",
    val errorMessage: String? = null,
    val isDrawerOpen: Boolean = false
) {
    val filteredConversations: List<AiConversation>
        get() = if (searchQuery.isBlank()) {
            conversations
        } else {
            conversations.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }
}
