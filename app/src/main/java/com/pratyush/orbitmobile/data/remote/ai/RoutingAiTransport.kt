package com.pratyush.orbitmobile.data.remote.ai

import com.pratyush.orbitmobile.core.common.OrbitResult
import com.pratyush.orbitmobile.core.firebase.FirebasePresenceManager
import com.pratyush.orbitmobile.core.firebase.RemoteLinkStore
import com.pratyush.orbitmobile.domain.model.AcpStatus
import com.pratyush.orbitmobile.domain.model.AiConversation
import com.pratyush.orbitmobile.domain.model.AiMessage
import com.pratyush.orbitmobile.domain.model.AiStreamEvent
import com.pratyush.orbitmobile.domain.model.EffectiveConnectionState
import com.pratyush.orbitmobile.domain.model.resolveEffectiveConnectionState
import com.pratyush.orbitmobile.domain.repository.AiTransport
import com.pratyush.orbitmobile.domain.repository.ConnectionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Transport selector implementing [AiTransport] that routes AI operations to:
 * - [lanTransport] when [EffectiveConnectionState.ConnectedLan] (LAN always takes priority)
 * - [firebaseTransport] when [EffectiveConnectionState.ConnectedRemote]
 * - Fails cleanly when disconnected.
 *
 * Active streams remain pinned to the transport on which they started so transient
 * transport changes do not restart or duplicate in-flight prompts.
 */
class RoutingAiTransport(
    connectionRepository: ConnectionRepository,
    remoteLinkStore: RemoteLinkStore,
    firebasePresenceManager: FirebasePresenceManager,
    private val lanTransport: AiTransport,
    private val firebaseTransport: AiTransport,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : AiTransport {

    val effectiveConnectionState: StateFlow<EffectiveConnectionState> = combine(
        connectionRepository.connectionState,
        firebasePresenceManager.peerPresence
    ) { lanState, peerPresence ->
        resolveEffectiveConnectionState(
            lanState = lanState,
            peerPresence = peerPresence,
            hasRemoteLink = remoteLinkStore.loadLink() != null
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = resolveEffectiveConnectionState(
            lanState = connectionRepository.connectionState.value,
            peerPresence = firebasePresenceManager.peerPresence.value,
            hasRemoteLink = remoteLinkStore.loadLink() != null
        )
    )

    internal fun selectTransportForState(state: EffectiveConnectionState = effectiveConnectionState.value): AiTransport? {
        return when (state) {
            is EffectiveConnectionState.ConnectedLan -> lanTransport
            is EffectiveConnectionState.ConnectedRemote -> firebaseTransport
            else -> null
        }
    }

    private inline fun <T> withActiveTransport(block: (AiTransport) -> OrbitResult<T>): OrbitResult<T> {
        val transport = selectTransportForState()
            ?: return OrbitResult.Error(Exception("Not connected to Windows"))
        return block(transport)
    }

    override suspend fun getStatus(): OrbitResult<AcpStatus> =
        withActiveTransport { it.getStatus() }

    override suspend fun getConversations(): OrbitResult<List<AiConversation>> =
        withActiveTransport { it.getConversations() }

    override suspend fun getConversation(conversationId: String): OrbitResult<Pair<AiConversation, List<AiMessage>>> =
        withActiveTransport { it.getConversation(conversationId) }

    override suspend fun createConversation(title: String?, conversationId: String?): OrbitResult<AiConversation> =
        withActiveTransport { it.createConversation(title, conversationId) }

    override suspend fun renameConversation(conversationId: String, title: String): OrbitResult<AiConversation> =
        withActiveTransport { it.renameConversation(conversationId, title) }

    override suspend fun deleteConversation(conversationId: String): OrbitResult<Unit> =
        withActiveTransport { it.deleteConversation(conversationId) }

    override suspend fun streamPrompt(
        conversationId: String,
        prompt: String,
        userMessageId: String?,
        assistantMessageId: String?,
        onEvent: (AiStreamEvent) -> Unit
    ): OrbitResult<Unit> {
        // Pin the transport for the duration of this stream
        val transport = selectTransportForState()
            ?: return OrbitResult.Error(Exception("Not connected to Windows"))
        return transport.streamPrompt(conversationId, prompt, userMessageId, assistantMessageId, onEvent)
    }

    override suspend fun cancelPrompt(conversationId: String): OrbitResult<Unit> =
        withActiveTransport { it.cancelPrompt(conversationId) }

    override suspend fun resolvePermission(requestId: String, optionId: String): OrbitResult<Boolean> =
        withActiveTransport { it.resolvePermission(requestId, optionId) }

    override suspend fun setPolicy(policy: String): OrbitResult<Unit> =
        withActiveTransport { it.setPolicy(policy) }
}
