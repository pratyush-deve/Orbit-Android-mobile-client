package com.pratyush.orbitmobile.data.local.database.entity

/**
 * Local Room/SQLite entity representing a cached AI conversation.
 */
data class AiConversationEntity(
    val id: String,
    val title: String,
    val backendId: String,
    val workspacePath: String?,
    val createdAtUtc: Long,
    val updatedAtUtc: Long,
    val isPinned: Boolean,
    val isArchived: Boolean,
    val origin: String,
    val isSynced: Boolean = true
)
