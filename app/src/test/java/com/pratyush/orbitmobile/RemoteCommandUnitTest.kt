package com.pratyush.orbitmobile

import com.pratyush.orbitmobile.core.firebase.RemoteCommandCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteCommandUnitTest {

    private val testSecretBase64 = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="

    @Test
    fun testCanonicalHmacSignAndVerify() {
        val canonical = RemoteCommandCrypto.buildCanonicalMessage(
            commandId = "cmd-123",
            linkId = "link-abc",
            senderDeviceId = "android-1",
            targetDeviceId = "win-1",
            commandType = "controls.volume.set",
            issuedAtMs = 1700000000000L,
            expiresAtMs = 1700000030000L,
            nonce = "nonce-001",
            canonicalPayloadJson = "{\"percentage\":75}"
        )

        val signature = RemoteCommandCrypto.sign(testSecretBase64, canonical)
        assertTrue(signature.isNotBlank())
        assertTrue(RemoteCommandCrypto.verify(testSecretBase64, canonical, signature))

        val tampered = RemoteCommandCrypto.buildCanonicalMessage(
            commandId = "cmd-123",
            linkId = "link-abc",
            senderDeviceId = "android-1",
            targetDeviceId = "win-1",
            commandType = "controls.volume.set",
            issuedAtMs = 1700000000000L,
            expiresAtMs = 1700000030000L,
            nonce = "nonce-001",
            canonicalPayloadJson = "{\"percentage\":100}"
        )
        assertFalse(RemoteCommandCrypto.verify(testSecretBase64, tampered, signature))
    }

    @Test
    fun testCanonicalFormatStability() {
        val canonical = RemoteCommandCrypto.buildCanonicalMessage(
            "c1", "l1", "s1", "t1", "controls.refresh", 10L, 20L, "n1", "{}"
        )
        assertEquals("c1|l1|s1|t1|controls.refresh|10|20|n1|{}", canonical)
    }
}
