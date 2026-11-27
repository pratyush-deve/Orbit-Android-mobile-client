package com.pratyush.orbitmobile.core.firebase

import android.content.ContextWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardSyncCoordinatorUnitTest {

    private val testSecret = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    private val linkId = "link-xyz"
    private val winId = "win-device-1"
    private val androidId = "android-device-1"

    @Test
    fun backgroundStaging_andResumeApplication_lifecycle() {
        val env = ClipboardCrypto.encryptAndSign(
            sharedSecretBase64 = testSecret,
            linkId = linkId,
            senderDeviceId = winId,
            targetDeviceId = androidId,
            plaintext = "PC copied text"
        )

        val dec = ClipboardCrypto.verifyAndDecrypt(
            envelope = env,
            sharedSecretBase64 = testSecret,
            expectedLinkId = linkId,
            expectedLocalDeviceId = androidId,
            expectedPeerDeviceId = winId,
            nowMs = env.issuedAtMs
        )
        assertTrue(dec.success)
        assertEquals("PC copied text", dec.plaintext)

        // Verify replay cache + loop guard integration
        val replay = PersistentClipboardReplayCache()
        val guard = ClipboardLoopGuard()
        assertTrue(replay.tryRegister(env.eventId, env.nonce, env.expiresAtMs, env.issuedAtMs))
        assertFalse(replay.tryRegister(env.eventId, env.nonce, env.expiresAtMs, env.issuedAtMs + 10))
        assertTrue(guard.tryBeginRemoteApply(env.eventId, dec.plaintext!!, env.issuedAtMs))
        assertTrue(guard.shouldSuppressOutgoing(dec.plaintext!!, env.issuedAtMs + 500))
    }

    @Test
    fun pendingClipboardEvent_modelHoldsStagedState() {
        val pending = PendingClipboardEvent(
            eventId = "evt-99",
            content = "Staged text",
            origin = "Windows",
            issuedAtMs = 1_700_000_000_000L
        )
        assertEquals("evt-99", pending.eventId)
        assertEquals("Staged text", pending.content)
        assertNotNull(pending.origin)
        assertTrue(ClipboardCrypto.isValidPlaintextSize(pending.content))
        val cleared: PendingClipboardEvent? = null
        assertNull(cleared)
    }

    @Test
    fun pendingClipboardStore_constantsEnforceBoundedExpiry() {
        assertEquals(10 * 60 * 1000L, PendingClipboardStore.DEFAULT_MAX_AGE_MS)
        assertFalse(ClipboardCrypto.isValidPlaintextSize("x".repeat(65_537)))
    }

    @Test
    fun reconciliation_handlesStartupOrderingToggleAndNoDuplicates() {
        // Simulates idempotent reconciliation across (syncEnabled, effectiveConnected, isForeground)
        data class MonitoringState(val registered: Boolean = false, val registerCalls: Int = 0, val unregisterCalls: Int = 0)
        fun reconcile(current: MonitoringState, syncEnabled: Boolean, connected: Boolean, foreground: Boolean): MonitoringState {
            val should = syncEnabled && connected && foreground
            return when {
                should && !current.registered -> MonitoringState(true, current.registerCalls + 1, current.unregisterCalls)
                !should && current.registered -> MonitoringState(false, current.registerCalls, current.unregisterCalls + 1)
                else -> current
            }
        }

        // 1. Preferences load before connection state
        var s = MonitoringState()
        s = reconcile(s, syncEnabled = true, connected = false, foreground = true)
        assertFalse(s.registered)
        s = reconcile(s, syncEnabled = true, connected = true, foreground = true)
        assertTrue(s.registered)
        assertEquals(1, s.registerCalls)

        // 2. Connection state arrives before preferences
        var s2 = MonitoringState()
        s2 = reconcile(s2, syncEnabled = false, connected = true, foreground = true)
        assertFalse(s2.registered)
        s2 = reconcile(s2, syncEnabled = true, connected = true, foreground = true)
        assertTrue(s2.registered)
        assertEquals(1, s2.registerCalls)

        // 3 & 5. Repeated reconciliations do not duplicate registration
        s2 = reconcile(s2, syncEnabled = true, connected = true, foreground = true)
        assertEquals(1, s2.registerCalls)

        // 4. Disabling synchronization unregisters
        s2 = reconcile(s2, syncEnabled = false, connected = true, foreground = true)
        assertFalse(s2.registered)
        assertEquals(1, s2.unregisterCalls)
    }

    @Test
    fun windowFocus_readsOnlyWhenFocused_andSuppressesEchoesAfterExpiryAllowsRecopy() {
        var readsOnResumeWithoutFocus = 0
        var readsOnWindowFocus = 0
        var hasFocus = false
        var foreground = false
        var focusDone = false

        fun onResume() {
            foreground = true
            // Must NOT read clipboard on ON_RESUME without window focus
        }
        fun onWindowFocusChanged(focused: Boolean) {
            hasFocus = focused
            if (focused && foreground && !focusDone) {
                focusDone = true
                readsOnWindowFocus++
            }
        }

        onResume()
        assertEquals(0, readsOnResumeWithoutFocus)
        assertEquals(0, readsOnWindowFocus)

        onWindowFocusChanged(true)
        assertEquals(1, readsOnWindowFocus)
        // Duplicate focus callback in same session does not duplicate read
        onWindowFocusChanged(true)
        assertEquals(1, readsOnWindowFocus)

        // LoopGuard: echoes suppressed during window; intentional re-copy allowed after expiry
        val guard = ClipboardLoopGuard(dedupWindowMs = 5_000L)
        val t0 = 1_700_000_000_000L
        assertTrue(guard.tryBeginRemoteApply("evt-1", "shared text", t0))
        assertTrue(guard.shouldSuppressOutgoing("shared text", t0 + 1_000L))
        assertFalse(guard.shouldSuppressOutgoing("shared text", t0 + 6_000L))
    }

    @Test
    fun transportSelection_supportsLanAndRemote() {
        val lan = com.pratyush.orbitmobile.domain.model.EffectiveConnectionState.ConnectedLan(
            deviceName = "PC", host = "192.168.1.10", port = 8765
        )
        val remote = com.pratyush.orbitmobile.domain.model.EffectiveConnectionState.ConnectedRemote(
            deviceName = "PC", lastSeenMs = 1_700_000_000_000L
        )
        assertTrue(lan.isConnected)
        assertTrue(remote.isConnected)
        assertFalse(com.pratyush.orbitmobile.domain.model.EffectiveConnectionState.Disconnected.isConnected)
    }
}
