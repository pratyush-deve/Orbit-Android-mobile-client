package com.pratyush.orbitmobile.domain.model

import com.pratyush.orbitmobile.core.firebase.RemotePeerPresence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectiveConnectionStateUnitTest {

    private val now = 1_700_000_000_000L

    private fun lanConnected() = ConnectionState.Connected(
        deviceId = "win-1",
        deviceName = "My Laptop",
        host = "192.168.1.10",
        port = 8443,
        connectionType = "LAN",
        latencyMs = 5L
    )

    private fun freshRemote(online: Boolean = true) = RemotePeerPresence(
        deviceId = "win-1",
        deviceName = "My Laptop",
        role = "windows",
        online = online,
        lastSeenMs = now - 5_000L,
        sessionId = "sess-1",
        isStale = false
    )

    private fun staleRemote() = RemotePeerPresence(
        deviceId = "win-1",
        deviceName = "My Laptop",
        role = "windows",
        online = true,
        lastSeenMs = now - 120_000L,
        sessionId = "sess-1",
        isStale = true
    )

    @Test
    fun transitionA_lanDropsWithFreshFirebase_becomesConnectedRemote() {
        val initial = resolveEffectiveConnectionState(
            lanState = lanConnected(),
            peerPresence = freshRemote(),
            hasRemoteLink = true,
            nowMs = now
        )
        assertTrue(initial is EffectiveConnectionState.ConnectedLan)

        val fallback = resolveEffectiveConnectionState(
            lanState = ConnectionState.TemporarilyUnavailable("My Laptop", "Wi-Fi lost"),
            peerPresence = freshRemote(),
            hasRemoteLink = true,
            nowMs = now
        )
        assertTrue(fallback is EffectiveConnectionState.ConnectedRemote)
        assertTrue(fallback.isConnected)
    }

    @Test
    fun transitionB_remoteToLanRestored_lanTakesPriority() {
        val state = resolveEffectiveConnectionState(
            lanState = lanConnected(),
            peerPresence = freshRemote(),
            hasRemoteLink = true,
            nowMs = now
        )
        assertTrue(state is EffectiveConnectionState.ConnectedLan)
        assertEquals("LAN", (state as EffectiveConnectionState.ConnectedLan).connectionType)
    }

    @Test
    fun transitionC_lanDisconnectedAndFirebaseOffline_isDisconnected() {
        val state = resolveEffectiveConnectionState(
            lanState = ConnectionState.Disconnected,
            peerPresence = freshRemote(online = false),
            hasRemoteLink = true,
            nowMs = now
        )
        assertEquals(EffectiveConnectionState.Disconnected, state)
        assertFalse(state.isConnected)
    }

    @Test
    fun transitionD_staleFirebaseRecord_doesNotReportConnectedRemote() {
        val state = resolveEffectiveConnectionState(
            lanState = ConnectionState.Disconnected,
            peerPresence = staleRemote(),
            hasRemoteLink = true,
            nowMs = now
        )
        assertEquals(EffectiveConnectionState.Disconnected, state)
        assertFalse(state.isConnected)
    }

    @Test
    fun transitionE_staleOrOfflineFirebase_neverOverridesHealthyLan() {
        val state = resolveEffectiveConnectionState(
            lanState = lanConnected(),
            peerPresence = staleRemote().copy(online = false),
            hasRemoteLink = true,
            nowMs = now
        )
        assertTrue(state is EffectiveConnectionState.ConnectedLan)
        assertTrue(state.isConnected)
    }

    @Test
    fun transitionF_reconnectingWithoutRemote_returnsReconnecting() {
        val state = resolveEffectiveConnectionState(
            lanState = ConnectionState.Disconnected,
            peerPresence = null,
            hasRemoteLink = true,
            isReconnecting = true,
            nowMs = now
        )
        assertTrue(state is EffectiveConnectionState.Reconnecting)
        assertFalse(state.isConnected)
    }

    @Test
    fun transitionG_lanUnavailableWithoutRemote_returnsTemporarilyUnavailable() {
        val state = resolveEffectiveConnectionState(
            lanState = ConnectionState.TemporarilyUnavailable("My Laptop", "Timeout"),
            peerPresence = freshRemote(online = false),
            hasRemoteLink = true,
            nowMs = now
        )
        assertTrue(state is EffectiveConnectionState.TemporarilyUnavailable)
        assertFalse(state.isConnected)
    }

    @Test
    fun transitionH_missingRemoteLink_ignoresPeerPresence() {
        val state = resolveEffectiveConnectionState(
            lanState = ConnectionState.Disconnected,
            peerPresence = freshRemote(online = true),
            hasRemoteLink = false,
            nowMs = now
        )
        assertEquals(EffectiveConnectionState.Disconnected, state)
    }
}
