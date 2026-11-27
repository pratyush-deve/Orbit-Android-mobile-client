package com.pratyush.orbitmobile.domain.model

/**
 * Domain models for the ORBIT Mobile remote Antigravity AI chat experience.
 */

data class AiConversation(
    val id: String,
    val title: String,
    val backendId: String = "antigravity-acp",
    val workspacePath: String? = null,
    val createdAtUtc: Long = System.currentTimeMillis(),
    val updatedAtUtc: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val origin: String = "Mobile",
    val isSynced: Boolean = true
) {
    val updatedAt: Long get() = updatedAtUtc
    val createdAt: Long get() = createdAtUtc
}

data class AiMessage(
    val id: String,
    val conversationId: String,
    val role: String, // "user", "assistant"
    val content: String,
    val thought: String? = null,
    val timestampUtc: Long = System.currentTimeMillis(),
    val generationStatus: String = "completed", // "streaming", "completed", "failed", "cancelled"
    val isSynced: Boolean = true,
    val toolActivities: List<AiToolActivity> = emptyList()
) {
    val timestamp: Long get() = timestampUtc
}

data class AiToolActivity(
    val id: String,
    val messageId: String,
    val toolCallId: String = id,
    val toolName: String,
    val status: String, // "running", "completed", "failed", "cancelled"
    val detail: String? = null,
    val title: String? = null,
    val timestampUtc: Long = System.currentTimeMillis()
) {
    val parameters: String get() = detail ?: ""
    val output: String? get() = detail
}

data class AcpStatus(
    val isAvailable: Boolean = false,
    val state: String = "unavailable", // "ready", "connecting", "unavailable"
    val backend: String = "antigravity-acp",
    val message: String? = null,
    val permissionMode: String = "AutoApproveSafe"
) {
    val isConnecting: Boolean get() = state.equals("connecting", ignoreCase = true)
    val activeModel: String get() = "Antigravity CLI ACP"
}

data class AiPermissionOption(
    val optionId: String,
    val name: String,
    val kind: String? = null
) {
    val id: String get() = optionId
    val label: String get() = name
}

data class AiPermissionRequest(
    val requestId: String,
    val toolName: String,
    val description: String,
    val options: List<AiPermissionOption>
) {
    val operation: String get() = toolName
}

data class AiStreamEvent(
    val type: String, // "chunk", "thought", "tool", "permission_request", "complete", "error", "conversation_updated", "cancelled"
    val conversationId: String,
    val messageId: String? = null,
    val text: String? = null,
    val toolCallId: String? = null,
    val toolTitle: String? = null,
    val toolName: String? = null,
    val toolStatus: String? = null,
    val toolDetail: String? = null,
    val requestId: String? = null,
    val description: String? = null,
    val options: List<AiPermissionOption> = emptyList(),
    val error: String? = null
)
