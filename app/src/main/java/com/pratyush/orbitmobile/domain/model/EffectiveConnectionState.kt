package com.pratyush.orbitmobile.domain.model

import com.pratyush.orbitmobile.core.firebase.RemotePeerPresence

/**
 * Unified effective connection state combining local LAN transport and
 * Firebase remote presence, with LAN always taking priority.
 */
sealed interface EffectiveConnectionState {
    data class ConnectedLan(
        val deviceName: String,
        val host: String,
        val port: Int,
        val connectionType: String = "LAN",
        val latencyMs: Long = 0L
    ) : EffectiveConnectionState

    data class ConnectedRemote(
        val deviceName: String,
        val lastSeenMs: Long
    ) : EffectiveConnectionState

    data class Reconnecting(
        val deviceName: String
    ) : EffectiveConnectionState

    data class TemporarilyUnavailable(
        val deviceName: String,
        val reason: String
    ) : EffectiveConnectionState

    data object Disconnected : EffectiveConnectionState

    val isConnected: Boolean
        get() = this is ConnectedLan || this is ConnectedRemote
}

/**
 * Pure resolver that derives [EffectiveConnectionState] from LAN [ConnectionState]
 * and Firebase [RemotePeerPresence].
 *
 * Priority:
 * 1. Healthy LAN [ConnectionState.Connected] always wins.
 * 2. Active, non-stale Firebase [RemotePeerPresence] when a remote link exists.
 * 3. Active reconnection / discovery / connecting states.
 * 4. LAN [ConnectionState.TemporarilyUnavailable] or [ConnectionState.Error].
 * 5. [EffectiveConnectionState.Disconnected].
 */
fun resolveEffectiveConnectionState(
    lanState: ConnectionState,
    peerPresence: RemotePeerPresence?,
    hasRemoteLink: Boolean,
    isReconnecting: Boolean = false,
    fallbackDeviceName: String = "ORBIT Windows PC",
    nowMs: Long = System.currentTimeMillis()
): EffectiveConnectionState {
    if (lanState is ConnectionState.Connected) {
        return EffectiveConnectionState.ConnectedLan(
            deviceName = lanState.deviceName.ifBlank { fallbackDeviceName },
            host = lanState.host,
            port = lanState.port,
            connectionType = lanState.connectionType.ifBlank { "LAN" },
            latencyMs = lanState.latencyMs
        )
    }

    val isPresenceFresh = peerPresence != null &&
        peerPresence.online &&
        !peerPresence.isStale &&
        peerPresence.lastSeenMs > 0L &&
        (nowMs - peerPresence.lastSeenMs) <= 75_000L

    if (hasRemoteLink && isPresenceFresh) {
        return EffectiveConnectionState.ConnectedRemote(
            deviceName = peerPresence!!.deviceName.ifBlank { fallbackDeviceName },
            lastSeenMs = peerPresence.lastSeenMs
        )
    }

    if (isReconnecting || lanState is ConnectionState.Connecting || lanState is ConnectionState.Discovering) {
        val name = (lanState as? ConnectionState.Connecting)?.deviceName?.ifBlank { fallbackDeviceName }
            ?: fallbackDeviceName
        return EffectiveConnectionState.Reconnecting(deviceName = name)
    }

    if (lanState is ConnectionState.TemporarilyUnavailable) {
        return EffectiveConnectionState.TemporarilyUnavailable(
            deviceName = lanState.deviceName.ifBlank { fallbackDeviceName },
            reason = lanState.reason
        )
    }

    if (lanState is ConnectionState.Error) {
        return EffectiveConnectionState.TemporarilyUnavailable(
            deviceName = fallbackDeviceName,
            reason = lanState.message
        )
    }

    return EffectiveConnectionState.Disconnected
}
