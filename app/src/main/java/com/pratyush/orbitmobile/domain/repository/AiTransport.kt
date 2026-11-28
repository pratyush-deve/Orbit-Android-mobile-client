package com.pratyush.orbitmobile.domain.repository

import com.pratyush.orbitmobile.core.common.OrbitResult
import com.pratyush.orbitmobile.domain.model.AcpStatus
import com.pratyush.orbitmobile.domain.model.AiConversation
import com.pratyush.orbitmobile.domain.model.AiMessage
import com.pratyush.orbitmobile.domain.model.AiStreamEvent

/**
 * Transport-independent contract for AI chat communication between ORBIT Mobile
 * and the Windows ORBIT host (e.g., LAN HTTP+SSE or remote Firebase RTDB).
 */
interface AiTransport {
    suspend fun getStatus(): OrbitResult<AcpStatus>

    suspend fun getConversations(): OrbitResult<List<AiConversation>>

    suspend fun getConversation(
        conversationId: String
    ): OrbitResult<Pair<AiConversation, List<AiMessage>>>

    suspend fun createConversation(
        title: String? = null,
        conversationId: String? = null
    ): OrbitResult<AiConversation>

    suspend fun renameConversation(
        conversationId: String,
        title: String
    ): OrbitResult<AiConversation>

    suspend fun deleteConversation(conversationId: String): OrbitResult<Unit>

    suspend fun streamPrompt(
        conversationId: String,
        prompt: String,
        userMessageId: String? = null,
        assistantMessageId: String? = null,
        onEvent: (AiStreamEvent) -> Unit
    ): OrbitResult<Unit>

    suspend fun cancelPrompt(conversationId: String): OrbitResult<Unit>

    suspend fun resolvePermission(
        requestId: String,
        optionId: String
    ): OrbitResult<Boolean>

    suspend fun setPolicy(policy: String): OrbitResult<Unit>
}
