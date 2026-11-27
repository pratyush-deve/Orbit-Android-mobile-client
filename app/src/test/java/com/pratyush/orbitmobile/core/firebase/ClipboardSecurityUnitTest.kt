package com.pratyush.orbitmobile.core.firebase

import com.pratyush.orbitmobile.data.remote.AndroidLanReceiver
import com.pratyush.orbitmobile.domain.model.AppConfig
import com.pratyush.orbitmobile.presentation.screens.home.HomeUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Base64

class ClipboardSecurityUnitTest {

    private val testSecret = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    private val wrongSecret = "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
    private val linkId = "link-xyz"
    private val winId = "win-device-1"
    private val androidId = "android-device-1"
    private val deterministicNonce = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)

    @Test
    fun protocol_deterministicInteroperabilityVector_matchesSpecAndRoundTrips() {
        val issued = 1_700_000_000_000L
        val env = ClipboardCrypto.encryptAndSign(
            sharedSecretBase64 = testSecret,
            linkId = linkId,
            senderDeviceId = winId,
            targetDeviceId = androidId,
            plaintext = "Hello ORBIT 🚀\nLine 2",
            eventId = "evt-fixed-1",
            issuedAtMs = issued,
            deterministicNonce = deterministicNonce
        )

        assertEquals("AQIDBAUGBwgJCgsM", env.nonce)
        assertEquals("text/plain", env.contentType)

        val res = ClipboardCrypto.verifyAndDecrypt(
            envelope = env,
            sharedSecretBase64 = testSecret,
            expectedLinkId = linkId,
            expectedLocalDeviceId = androidId,
            expectedPeerDeviceId = winId,
            nowMs = issued + 5_000L
        )
        assertTrue(res.success)
        assertEquals("Hello ORBIT 🚀\nLine 2", res.plaintext)
    }

    @Test
    fun protocol_rejectsInvalidSignatureWrongSecretRoutingExpiryAndNonce() {
        val issued = 1_700_000_000_000L
        val env = ClipboardCrypto.encryptAndSign(testSecret, linkId, winId, androidId, "secret text", issuedAtMs = issued)

        assertEquals(
            ClipboardValidationError.INVALID_SIGNATURE,
            ClipboardCrypto.verifyAndDecrypt(env, wrongSecret, linkId, androidId, winId, nowMs = issued).error
        )
        assertEquals(
            ClipboardValidationError.INVALID_SIGNATURE,
            ClipboardCrypto.verifyAndDecrypt(env.copy(signature = Base64.getEncoder().encodeToString(ByteArray(32))), testSecret, linkId, androidId, winId, nowMs = issued).error
        )
        assertEquals(
            ClipboardValidationError.INVALID_LINK_ID,
            ClipboardCrypto.verifyAndDecrypt(env, testSecret, "other", androidId, winId, nowMs = issued).error
        )
        assertEquals(
            ClipboardValidationError.INVALID_TARGET_DEVICE,
            ClipboardCrypto.verifyAndDecrypt(env, testSecret, linkId, "other", winId, nowMs = issued).error
        )
        assertEquals(
            ClipboardValidationError.UNAUTHORIZED_SENDER,
            ClipboardCrypto.verifyAndDecrypt(env, testSecret, linkId, androidId, "other", nowMs = issued).error
        )
        assertEquals(
            ClipboardValidationError.EXPIRED_EVENT,
            ClipboardCrypto.verifyAndDecrypt(env, testSecret, linkId, androidId, winId, nowMs = issued + 95_000L).error
        )
        assertEquals(
            ClipboardValidationError.FUTURE_TIMESTAMP,
            ClipboardCrypto.verifyAndDecrypt(env, testSecret, linkId, androidId, winId, nowMs = issued - 40_000L).error
        )
        assertEquals(
            ClipboardValidationError.INVALID_NONCE,
            ClipboardCrypto.verifyAndDecrypt(env.copy(nonce = Base64.getEncoder().encodeToString(ByteArray(8))), testSecret, linkId, androidId, winId, nowMs = issued).error
        )
    }

    @Test
    fun protocol_enforces64KbUtf8Boundary() {
        val exact64Kb = "a".repeat(65_536)
        val over64Kb = "a".repeat(65_537)
        val unicodeOver64Kb = "€".repeat(21_846) // 65,538 UTF-8 bytes

        assertTrue(ClipboardCrypto.isValidPlaintextSize(exact64Kb))
        assertFalse(ClipboardCrypto.isValidPlaintextSize(over64Kb))
        assertFalse(ClipboardCrypto.isValidPlaintextSize(unicodeOver64Kb))

        val env = ClipboardCrypto.encryptAndSign(testSecret, linkId, winId, androidId, exact64Kb)
        val res = ClipboardCrypto.verifyAndDecrypt(env, testSecret, linkId, androidId, winId, nowMs = env.issuedAtMs)
        assertTrue(res.success)
    }

    @Test
    fun replayCache_rejectsDuplicatesAndPersistsAcrossRestart() {
        val tempFile = File.createTempFile("orbit_replay_", ".json")
        try {
            val now = System.currentTimeMillis()
            val cache1 = PersistentClipboardReplayCache(tempFile, maxEntries = 256)
            assertTrue(cache1.tryRegister("evt-1", "nonce-1", now + 60_000L, now))
            assertFalse(cache1.tryRegister("evt-1", "nonce-2", now + 60_000L, now))
            assertFalse(cache1.tryRegister("evt-2", "nonce-1", now + 60_000L, now))

            val cache2 = PersistentClipboardReplayCache(tempFile, maxEntries = 256)
            assertFalse(cache2.tryRegister("evt-1", "nonce-3", now + 60_000L, now + 1000))
            assertFalse(cache2.tryRegister("evt-3", "nonce-1", now + 60_000L, now + 1000))
            assertTrue(cache2.tryRegister("evt-4", "nonce-4", now + 60_000L, now + 1000))
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun loopGuard_suppressesEchoAndAllowsRecopyAfterExpiry() {
        val t0 = 1_700_000_000_000L
        val guard = ClipboardLoopGuard(dedupWindowMs = 5_000L)

        assertTrue(guard.tryBeginRemoteApply("evt-1", "copied from pc", t0))
        assertFalse(guard.tryBeginRemoteApply("evt-1", "copied from pc", t0 + 100))
        assertTrue(guard.shouldSuppressOutgoing("copied from pc", t0 + 1_000))
        assertFalse(guard.shouldSuppressOutgoing("copied from pc", t0 + 6_000))
    }

    @Test
    fun androidLanReceiver_authRejectsMissingInvalidAndAcceptsValidToken() {
        assertFalse(AndroidLanReceiver.isAuthorizedRequest("secret-token", null))
        assertFalse(AndroidLanReceiver.isAuthorizedRequest("secret-token", ""))
        assertFalse(AndroidLanReceiver.isAuthorizedRequest(null, "Bearer secret-token"))
        assertFalse(AndroidLanReceiver.isAuthorizedRequest("", "Bearer secret-token"))
        assertFalse(AndroidLanReceiver.isAuthorizedRequest("secret-token", "Bearer wrong-token"))
        assertTrue(AndroidLanReceiver.isAuthorizedRequest("secret-token", "Bearer secret-token"))
    }

    @Test
    fun settings_clipboardSyncDefaultsToOff() {
        assertFalse(AppConfig().isClipboardSyncEnabled)
        assertFalse(HomeUiState().isClipboardSyncActive)
    }
}
