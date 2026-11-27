package com.pratyush.orbitmobile.data.local.database.entity

/**
 * Local Room/SQLite entity representing a tool execution event in a message.
 */
data class AiToolActivityEntity(
    val id: String,
    val messageId: String,
    val toolCallId: String = id,
    val toolName: String,
    val status: String,
    val detail: String?,
    val title: String? = null,
    val timestampUtc: Long
)
