package com.pratyush.orbitmobile.core.firebase

import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class ClipboardEventEnvelope(
    val eventId: String,
    val linkId: String,
    val senderDeviceId: String,
    val targetDeviceId: String,
    val issuedAtMs: Long,
    val expiresAtMs: Long,
    val contentType: String = ClipboardCrypto.CONTENT_TYPE_PLAIN_TEXT,
    val nonce: String,
    val encryptedPayload: String,
    val signature: String,
    val senderUid: String = "",
    val targetUid: String = ""
) {
    fun toJsonString(): String = JSONObject().apply {
        put("eventId", eventId)
        put("linkId", linkId)
        put("senderDeviceId", senderDeviceId)
        put("targetDeviceId", targetDeviceId)
        put("issuedAtMs", issuedAtMs)
        put("expiresAtMs", expiresAtMs)
        put("contentType", contentType)
        put("nonce", nonce)
        put("encryptedPayload", encryptedPayload)
        put("signature", signature)
    }.toString()

    companion object {
        fun fromJsonString(json: String): ClipboardEventEnvelope? = try {
            val obj = JSONObject(json)
            ClipboardEventEnvelope(
                eventId = obj.optString("eventId", ""),
                linkId = obj.optString("linkId", ""),
                senderDeviceId = obj.optString("senderDeviceId", ""),
                targetDeviceId = obj.optString("targetDeviceId", ""),
                issuedAtMs = obj.optLong("issuedAtMs", 0L),
                expiresAtMs = obj.optLong("expiresAtMs", 0L),
                contentType = obj.optString("contentType", ""),
                nonce = obj.optString("nonce", ""),
                encryptedPayload = obj.optString("encryptedPayload", ""),
                signature = obj.optString("signature", "")
            )
        } catch (_: Exception) {
            null
        }
    }
}

enum class ClipboardValidationError {
    NONE,
    EMPTY_PAYLOAD,
    PAYLOAD_TOO_LARGE,
    UNSUPPORTED_CONTENT_TYPE,
    MALFORMED_ENVELOPE,
    INVALID_LINK_ID,
    UNAUTHORIZED_SENDER,
    INVALID_TARGET_DEVICE,
    EXPIRED_EVENT,
    FUTURE_TIMESTAMP,
    INVALID_NONCE,
    INVALID_SIGNATURE,
    DECRYPTION_FAILED,
    REPLAYED_EVENT
}

data class ClipboardDecryptResult(
    val success: Boolean,
    val plaintext: String?,
    val error: ClipboardValidationError,
    val errorMessage: String? = null
)

/**
 * Interoperable HKDF-SHA256 + AES-256-GCM + HMAC-SHA256 clipboard protocol.
 */
object ClipboardCrypto {
    const val MAX_PAYLOAD_BYTES = 65_536
    const val EVENT_TTL_MS = 60_000L
    const val MAX_CLOCK_SKEW_MS = 30_000L
    const val GCM_NONCE_BYTES = 12
    const val GCM_TAG_BYTES = 16
    const val CONTENT_TYPE_PLAIN_TEXT = "text/plain"
    const val ENC_KEY_INFO = "orbit-clipboard-enc-v1"
    const val MAC_KEY_INFO = "orbit-clipboard-mac-v1"

    private val secureRandom = SecureRandom()

    fun deriveKeys(sharedSecretBase64: String): Pair<ByteArray, ByteArray> {
        require(sharedSecretBase64.isNotBlank()) { "Shared secret is required." }
        val ikm = Base64.getDecoder().decode(sharedSecretBase64)
        val encKey = hkdfSha256(ikm, ENC_KEY_INFO.toByteArray(Charsets.UTF_8), 32)
        val macKey = hkdfSha256(ikm, MAC_KEY_INFO.toByteArray(Charsets.UTF_8), 32)
        return encKey to macKey
    }

    private fun hkdfSha256(ikm: ByteArray, info: ByteArray, length: Int): ByteArray {
        val zeroSalt = ByteArray(32)
        val extractMac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(zeroSalt, "HmacSHA256"))
        }
        val prk = extractMac.doFinal(ikm)

        val expandMac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(prk, "HmacSHA256"))
        }
        expandMac.update(info)
        expandMac.update(0x01.toByte())
        return expandMac.doFinal().copyOf(length)
    }

    fun buildAadString(
        eventId: String,
        linkId: String,
        senderDeviceId: String,
        targetDeviceId: String,
        issuedAtMs: Long,
        expiresAtMs: Long,
        contentType: String
    ): String = "$eventId|$linkId|$senderDeviceId|$targetDeviceId|$issuedAtMs|$expiresAtMs|$contentType"

    fun buildCanonicalMacString(
        eventId: String,
        linkId: String,
        senderDeviceId: String,
        targetDeviceId: String,
        issuedAtMs: Long,
        expiresAtMs: Long,
        contentType: String,
        nonceBase64: String,
        encryptedPayloadBase64: String
    ): String = "$eventId|$linkId|$senderDeviceId|$targetDeviceId|$issuedAtMs|$expiresAtMs|$contentType|$nonceBase64|$encryptedPayloadBase64"

    fun isValidPlaintextSize(text: String?): Boolean {
        if (text.isNullOrEmpty()) return false
        val bytes = text.toByteArray(Charsets.UTF_8).size
        return bytes in 1..MAX_PAYLOAD_BYTES
    }

    fun encryptAndSign(
        sharedSecretBase64: String,
        linkId: String,
        senderDeviceId: String,
        targetDeviceId: String,
        plaintext: String,
        eventId: String? = null,
        issuedAtMs: Long? = null,
        deterministicNonce: ByteArray? = null
    ): ClipboardEventEnvelope {
        require(isValidPlaintextSize(plaintext)) {
            "Clipboard text must be non-empty and at most $MAX_PAYLOAD_BYTES UTF-8 bytes."
        }

        val (encKey, macKey) = deriveKeys(sharedSecretBase64)
        val now = issuedAtMs ?: System.currentTimeMillis()
        val expires = now + EVENT_TTL_MS
        val id = if (!eventId.isNullOrBlank()) eventId else UUID.randomUUID().toString().replace("-", "")

        val nonce = ByteArray(GCM_NONCE_BYTES)
        if (deterministicNonce != null) {
            require(deterministicNonce.size == GCM_NONCE_BYTES) { "Nonce must be $GCM_NONCE_BYTES bytes." }
            System.arraycopy(deterministicNonce, 0, nonce, 0, GCM_NONCE_BYTES)
        } else {
            secureRandom.nextBytes(nonce)
        }

        val aadBytes = buildAadString(id, linkId, senderDeviceId, targetDeviceId, now, expires, CONTENT_TYPE_PLAIN_TEXT)
            .toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(encKey, "AES"), GCMParameterSpec(GCM_TAG_BYTES * 8, nonce))
        cipher.updateAAD(aadBytes)
        val combinedPayload = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val nonceB64 = Base64.getEncoder().encodeToString(nonce)
        val payloadB64 = Base64.getEncoder().encodeToString(combinedPayload)
        val macInput = buildCanonicalMacString(
            id, linkId, senderDeviceId, targetDeviceId, now, expires, CONTENT_TYPE_PLAIN_TEXT, nonceB64, payloadB64
        )
        val mac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(macKey, "HmacSHA256"))
        }
        val sigB64 = Base64.getEncoder().encodeToString(mac.doFinal(macInput.toByteArray(Charsets.UTF_8)))

        return ClipboardEventEnvelope(
            eventId = id,
            linkId = linkId,
            senderDeviceId = senderDeviceId,
            targetDeviceId = targetDeviceId,
            issuedAtMs = now,
            expiresAtMs = expires,
            contentType = CONTENT_TYPE_PLAIN_TEXT,
            nonce = nonceB64,
            encryptedPayload = payloadB64,
            signature = sigB64
        )
    }

    fun verifyAndDecrypt(
        envelope: ClipboardEventEnvelope?,
        sharedSecretBase64: String,
        expectedLinkId: String,
        expectedLocalDeviceId: String,
        expectedPeerDeviceId: String? = null,
        replayCache: PersistentClipboardReplayCache? = null,
        nowMs: Long? = null
    ): ClipboardDecryptResult {
        if (envelope == null ||
            envelope.eventId.isBlank() ||
            envelope.linkId.isBlank() ||
            envelope.senderDeviceId.isBlank() ||
            envelope.targetDeviceId.isBlank() ||
            envelope.nonce.isBlank() ||
            envelope.encryptedPayload.isBlank() ||
            envelope.signature.isBlank()
        ) {
            return ClipboardDecryptResult(false, null, ClipboardValidationError.MALFORMED_ENVELOPE, "Missing required envelope fields.")
        }

        if (envelope.contentType != CONTENT_TYPE_PLAIN_TEXT) {
            return ClipboardDecryptResult(false, null, ClipboardValidationError.UNSUPPORTED_CONTENT_TYPE, "Unsupported content type.")
        }

        if (envelope.linkId != expectedLinkId) {
            return ClipboardDecryptResult(false, null, ClipboardValidationError.INVALID_LINK_ID, "Link ID mismatch.")
        }

        if (envelope.targetDeviceId != expectedLocalDeviceId) {
            return ClipboardDecryptResult(false, null, ClipboardValidationError.INVALID_TARGET_DEVICE, "Target device mismatch.")
        }

        if (envelope.senderDeviceId == expectedLocalDeviceId ||
            (!expectedPeerDeviceId.isNullOrBlank() && envelope.senderDeviceId != expectedPeerDeviceId)
        ) {
            return ClipboardDecryptResult(false, null, ClipboardValidationError.UNAUTHORIZED_SENDER, "Sender device mismatch.")
        }

        val now = nowMs ?: System.currentTimeMillis()
        if (envelope.issuedAtMs > now + MAX_CLOCK_SKEW_MS) {
            return ClipboardDecryptResult(false, null, ClipboardValidationError.FUTURE_TIMESTAMP, "Issued timestamp exceeds allowed clock skew.")
        }

        if (envelope.expiresAtMs < envelope.issuedAtMs || now > envelope.expiresAtMs + MAX_CLOCK_SKEW_MS) {
            return ClipboardDecryptResult(false, null, ClipboardValidationError.EXPIRED_EVENT, "Clipboard event has expired.")
        }

        val nonce: ByteArray
        val combinedPayload: ByteArray
        val providedSig: ByteArray
        try {
            nonce = Base64.getDecoder().decode(envelope.nonce)
            combinedPayload = Base64.getDecoder().decode(envelope.encryptedPayload)
            providedSig = Base64.getDecoder().decode(envelope.signature)
        } catch (_: Exception) {
            return ClipboardDecryptResult(false, null, ClipboardValidationError.MALFORMED_ENVELOPE, "Invalid Base64 encoding.")
        }

        if (nonce.size != GCM_NONCE_BYTES) {
            return ClipboardDecryptResult(false, null, ClipboardValidationError.INVALID_NONCE, "Invalid GCM nonce length.")
        }

        if (combinedPayload.size <= GCM_TAG_BYTES || combinedPayload.size > MAX_PAYLOAD_BYTES + GCM_TAG_BYTES) {
            return ClipboardDecryptResult(false, null, ClipboardValidationError.PAYLOAD_TOO_LARGE, "Encrypted payload size out of bounds.")
        }

        val (encKey, macKey) = try {
            deriveKeys(sharedSecretBase64)
        } catch (_: Exception) {
            return ClipboardDecryptResult(false, null, ClipboardValidationError.INVALID_SIGNATURE, "Invalid shared secret.")
        }

        val macInput = buildCanonicalMacString(
            envelope.eventId,
            envelope.linkId,
            envelope.senderDeviceId,
            envelope.targetDeviceId,
            envelope.issuedAtMs,
            envelope.expiresAtMs,
            envelope.contentType,
            envelope.nonce,
            envelope.encryptedPayload
        )
        val mac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(macKey, "HmacSHA256"))
        }
        val expectedSig = mac.doFinal(macInput.toByteArray(Charsets.UTF_8))
        if (!MessageDigest.isEqual(expectedSig, providedSig)) {
            return ClipboardDecryptResult(false, null, ClipboardValidationError.INVALID_SIGNATURE, "HMAC signature verification failed.")
        }

        if (replayCache != null && !replayCache.tryRegister(envelope.eventId, envelope.nonce, envelope.expiresAtMs, now)) {
            return ClipboardDecryptResult(false, null, ClipboardValidationError.REPLAYED_EVENT, "Duplicate or replayed clipboard event.")
        }

        return try {
            val aadBytes = buildAadString(
                envelope.eventId,
                envelope.linkId,
                envelope.senderDeviceId,
                envelope.targetDeviceId,
                envelope.issuedAtMs,
                envelope.expiresAtMs,
                envelope.contentType
            ).toByteArray(Charsets.UTF_8)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(encKey, "AES"), GCMParameterSpec(GCM_TAG_BYTES * 8, nonce))
            cipher.updateAAD(aadBytes)
            val plaintext = String(cipher.doFinal(combinedPayload), Charsets.UTF_8)
            if (!isValidPlaintextSize(plaintext)) {
                ClipboardDecryptResult(false, null, ClipboardValidationError.PAYLOAD_TOO_LARGE, "Decrypted payload empty or exceeds 64 KB.")
            } else {
                ClipboardDecryptResult(true, plaintext, ClipboardValidationError.NONE)
            }
        } catch (_: Exception) {
            ClipboardDecryptResult(false, null, ClipboardValidationError.DECRYPTION_FAILED, "AES-GCM decryption failed.")
        }
    }
}

/**
 * Persistent, bounded replay-protection cache for clipboard events (eventId + nonce).
 */
class PersistentClipboardReplayCache(
    private val persistenceFile: File? = null,
    private val maxEntries: Int = 512
) {
    private data class Entry(val eventId: String, val nonce: String, val expiresAtMs: Long)

    private val lock = Any()
    private val seenEventIds = HashMap<String, Long>()
    private val seenNonces = HashMap<String, Long>()
    private val entries = ArrayList<Entry>()

    init {
        loadFromDisk()
    }

    fun tryRegister(eventId: String, nonce: String, expiresAtMs: Long, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (eventId.isBlank() || nonce.isBlank()) return false
        synchronized(lock) {
            pruneExpiredLocked(nowMs)
            if (seenEventIds.containsKey(eventId) || seenNonces.containsKey(nonce)) {
                return false
            }
            while (entries.size >= maxEntries.coerceIn(64, 1024)) {
                val oldest = entries.removeAt(0)
                seenEventIds.remove(oldest.eventId)
                seenNonces.remove(oldest.nonce)
            }
            seenEventIds[eventId] = expiresAtMs
            seenNonces[nonce] = expiresAtMs
            entries.add(Entry(eventId, nonce, expiresAtMs))
            saveToDiskLocked()
            return true
        }
    }

    private fun pruneExpiredLocked(nowMs: Long) {
        val prevSize = entries.size
        val it = entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (e.expiresAtMs <= nowMs) {
                it.remove()
                seenEventIds.remove(e.eventId)
                seenNonces.remove(e.nonce)
            }
        }
        if (entries.size != prevSize) {
            saveToDiskLocked()
        }
    }

    private fun loadFromDisk() {
        val file = persistenceFile ?: return
        if (!file.exists()) return
        try {
            val arr = org.json.JSONArray(file.readText(Charsets.UTF_8))
            val now = System.currentTimeMillis()
            synchronized(lock) {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val eventId = obj.optString("eventId", "")
                    val nonce = obj.optString("nonce", "")
                    val exp = obj.optLong("expiresAtMs", 0L)
                    if (exp > now && eventId.isNotBlank() && nonce.isNotBlank() &&
                        !seenEventIds.containsKey(eventId) && !seenNonces.containsKey(nonce)
                    ) {
                        seenEventIds[eventId] = exp
                        seenNonces[nonce] = exp
                        entries.add(Entry(eventId, nonce, exp))
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun saveToDiskLocked() {
        val file = persistenceFile ?: return
        try {
            file.parentFile?.mkdirs()
            val arr = org.json.JSONArray()
            for (e in entries) {
                arr.put(JSONObject().apply {
                    put("eventId", e.eventId)
                    put("nonce", e.nonce)
                    put("expiresAtMs", e.expiresAtMs)
                })
            }
            file.writeText(arr.toString(), Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }
}

/**
 * Bounded, time-limited cache preventing bidirectional clipboard echo loops and cross-transport duplicates.
 */
class ClipboardLoopGuard(
    private val dedupWindowMs: Long = 5_000L,
    private val maxEntries: Int = 64,
    private val persistenceFile: File? = null
) {
    private data class AppliedEntry(val eventId: String, val contentHash: String, val expiresAtMs: Long)

    private val lock = Any()
    private val appliedRemote = ArrayList<AppliedEntry>()
    private val recentSentHashes = HashMap<String, Long>()

    init {
        loadFromDisk()
    }

    companion object {
        fun computeSha256(text: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }

    fun tryBeginRemoteApply(eventId: String, text: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (text.isEmpty()) return false
        val hash = computeSha256(text)
        synchronized(lock) {
            pruneLocked(nowMs)
            val dup = appliedRemote.any {
                (eventId.isNotBlank() && it.eventId == eventId) || it.contentHash == hash
            }
            if (dup) return false

            while (appliedRemote.size >= maxEntries.coerceIn(16, 256)) {
                appliedRemote.removeAt(0)
            }
            appliedRemote.add(AppliedEntry(eventId, hash, nowMs + dedupWindowMs.coerceAtLeast(500L)))
            saveToDiskLocked()
            return true
        }
    }

    fun shouldSuppressOutgoing(text: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (text.isEmpty()) return true
        val hash = computeSha256(text)
        synchronized(lock) {
            pruneLocked(nowMs)
            if (appliedRemote.any { it.contentHash == hash }) return true
            val exp = recentSentHashes[hash]
            return exp != null && exp > nowMs
        }
    }

    fun recordOutgoingSent(text: String, nowMs: Long = System.currentTimeMillis()) {
        if (text.isEmpty()) return
        val hash = computeSha256(text)
        synchronized(lock) {
            pruneLocked(nowMs)
            recentSentHashes[hash] = nowMs + dedupWindowMs.coerceAtLeast(500L)
        }
    }

    private fun pruneLocked(nowMs: Long) {
        appliedRemote.removeAll { it.expiresAtMs <= nowMs }
        val it = recentSentHashes.entries.iterator()
        while (it.hasNext()) {
            if (it.next().value <= nowMs) it.remove()
        }
    }

    private fun loadFromDisk() {
        val file = persistenceFile ?: return
        if (!file.exists()) return
        try {
            val arr = org.json.JSONArray(file.readText(Charsets.UTF_8))
            val now = System.currentTimeMillis()
            synchronized(lock) {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val hash = obj.optString("contentHash", "")
                    val exp = obj.optLong("expiresAtMs", 0L)
                    if (exp > now && hash.isNotBlank()) {
                        appliedRemote.add(AppliedEntry(obj.optString("eventId", ""), hash, exp))
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun saveToDiskLocked() {
        val file = persistenceFile ?: return
        try {
            file.parentFile?.mkdirs()
            val arr = org.json.JSONArray()
            for (e in appliedRemote) {
                arr.put(JSONObject().apply {
                    put("eventId", e.eventId)
                    put("contentHash", e.contentHash)
                    put("expiresAtMs", e.expiresAtMs)
                })
            }
            file.writeText(arr.toString(), Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }
}
