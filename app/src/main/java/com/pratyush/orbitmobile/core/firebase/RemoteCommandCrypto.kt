package com.pratyush.orbitmobile.core.firebase

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Canonical HMAC-SHA256 signing and verification matching ORBIT Windows's RemoteCommandCrypto.
 */
object RemoteCommandCrypto {

    fun buildCanonicalMessage(
        commandId: String,
        linkId: String,
        senderDeviceId: String,
        targetDeviceId: String,
        commandType: String,
        issuedAtMs: Long,
        expiresAtMs: Long,
        nonce: String,
        canonicalPayloadJson: String
    ): String {
        return "$commandId|$linkId|$senderDeviceId|$targetDeviceId|$commandType|$issuedAtMs|$expiresAtMs|$nonce|$canonicalPayloadJson"
    }

    fun sign(sharedSecretBase64: String, canonicalMessage: String): String {
        val keyBytes = Base64.getDecoder().decode(sharedSecretBase64)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(keyBytes, "HmacSHA256"))
        val hash = mac.doFinal(canonicalMessage.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(hash)
    }

    fun verify(sharedSecretBase64: String, canonicalMessage: String, signatureBase64: String): Boolean {
        if (sharedSecretBase64.isBlank() || canonicalMessage.isBlank() || signatureBase64.isBlank()) {
            return false
        }
        return try {
            val keyBytes = Base64.getDecoder().decode(sharedSecretBase64)
            val providedSig = Base64.getDecoder().decode(signatureBase64)
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(keyBytes, "HmacSHA256"))
            val expectedSig = mac.doFinal(canonicalMessage.toByteArray(Charsets.UTF_8))
            MessageDigest.isEqual(expectedSig, providedSig)
        } catch (_: Exception) {
            false
        }
    }
}
