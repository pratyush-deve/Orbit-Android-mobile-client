package com.pratyush.orbitmobile.data.local.database.entity

/**
 * Local Room/SQLite entity representing a cached message in an AI conversation.
 */
data class AiMessageEntity(
    val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val timestampUtc: Long,
    val generationStatus: String,
    val isSynced: Boolean = true
)
