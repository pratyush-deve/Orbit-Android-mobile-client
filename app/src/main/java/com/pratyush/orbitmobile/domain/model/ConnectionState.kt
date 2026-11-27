package com.pratyush.orbitmobile.domain.model

/**
 * Domain model representing the active connection state with an ORBIT Windows host.
 */
sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Discovering : ConnectionState
    data class Connecting(val deviceName: String, val host: String) : ConnectionState
    data class Connected(
        val deviceId: String,
        val deviceName: String,
        val host: String,
        val port: Int,
        val connectionType: String = "LAN (5 GHz Wi-Fi)",
        val latencyMs: Long = 12L,
        val isClipboardSyncEnabled: Boolean = true,
        val connectedSinceEpochMs: Long = System.currentTimeMillis()
    ) : ConnectionState
    data class TemporarilyUnavailable(val deviceName: String, val reason: String) : ConnectionState
    data class Error(val message: String, val canRetry: Boolean = true) : ConnectionState
}
