package com.pratyush.orbitmobile.data.local.database.dao

import com.pratyush.orbitmobile.data.local.database.entity.AiConversationEntity
import com.pratyush.orbitmobile.data.local.database.entity.AiMessageEntity
import com.pratyush.orbitmobile.data.local.database.entity.AiPendingOpEntity
import com.pratyush.orbitmobile.data.local.database.entity.AiToolActivityEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object contract for AI conversations and message caching.
 */
interface AiChatDao {
    fun getConversationsFlow(): Flow<List<AiConversationEntity>>
    suspend fun getConversations(): List<AiConversationEntity>
    suspend fun getConversationById(id: String): AiConversationEntity?
    suspend fun upsertConversation(conversation: AiConversationEntity)
    suspend fun upsertConversations(conversations: List<AiConversationEntity>)
    suspend fun updateConversationTitle(id: String, title: String, updatedAtUtc: Long = System.currentTimeMillis(), isSynced: Boolean = false)
    suspend fun deleteConversation(id: String)

    fun getMessagesFlow(conversationId: String): Flow<List<AiMessageEntity>>
    suspend fun getMessages(conversationId: String): List<AiMessageEntity>
    suspend fun upsertMessage(message: AiMessageEntity)
    suspend fun upsertMessages(messages: List<AiMessageEntity>)
    suspend fun updateMessageContentAndStatus(id: String, content: String, status: String, isSynced: Boolean = true)
    suspend fun deleteMessagesForConversation(conversationId: String)

    suspend fun getToolActivities(messageId: String): List<AiToolActivityEntity>
    suspend fun upsertToolActivity(activity: AiToolActivityEntity)

    suspend fun enqueuePendingOp(op: AiPendingOpEntity)
    suspend fun getPendingOps(): List<AiPendingOpEntity>
    suspend fun updatePendingOpStatus(opId: String, status: String, retryCount: Int, lastError: String?)
    suspend fun deletePendingOp(opId: String)
    suspend fun resetInFlightPendingOps()
}
