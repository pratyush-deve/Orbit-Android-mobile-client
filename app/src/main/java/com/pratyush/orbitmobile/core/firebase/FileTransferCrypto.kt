package com.pratyush.orbitmobile.core.firebase

import org.json.JSONObject
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class RemoteTransferEnvelope(
    val transferId: String,
    val linkId: String,
    val senderUid: String,
    val targetUid: String,
    val senderDeviceId: String,
    val targetDeviceId: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val plaintextSha256: String,
    val encryptedSize: Long,
    val storagePath: String,
    val encryptionVersion: Int = FileTransferCrypto.PROTOCOL_VERSION,
    val issuedAtMs: Long,
    val expiresAtMs: Long,
    val signature: String = "",
    val status: String = "Available"
) {
    fun toMap(): Map<String, Any> = mapOf(
        "transferId" to transferId,
        "linkId" to linkId,
        "senderUid" to senderUid,
        "targetUid" to targetUid,
        "senderDeviceId" to senderDeviceId,
        "targetDeviceId" to targetDeviceId,
        "fileName" to fileName,
        "fileSize" to fileSize,
        "mimeType" to mimeType,
        "plaintextSha256" to plaintextSha256,
        "encryptedSize" to encryptedSize,
        "storagePath" to storagePath,
        "encryptionVersion" to encryptionVersion,
        "issuedAtMs" to issuedAtMs,
        "expiresAtMs" to expiresAtMs,
        "signature" to signature,
        "status" to status
    )

    fun toJsonString(): String = JSONObject(toMap()).toString()
}

data class EncryptStreamResult(
    val encryptedSize: Long,
    val plaintextSha256: String
)

data class DecryptStreamResult(
    val success: Boolean,
    val plaintextBytesWritten: Long,
    val computedPlaintextSha256: String?,
    val error: String? = null
)

/**
 * Interoperable HKDF-SHA256 + chunked AES-256-GCM + HMAC-SHA256 file-transfer protocol (v1).
 * Byte-for-byte compatible with ORBIT Windows FileTransferCrypto.cs.
 */
object FileTransferCrypto {
    const val PROTOCOL_VERSION = 1
    const val MAX_REMOTE_FILE_SIZE_BYTES = 50L * 1024L * 1024L // 50 MB centralized limit
    const val DEFAULT_CHUNK_SIZE = 512 * 1024 // 512 KB
    const val MAX_ALLOWED_CHUNK_SIZE = 4 * 1024 * 1024
    const val GCM_NONCE_BYTES = 12
    const val GCM_TAG_BYTES = 16
    const val TRANSFER_TTL_MS = 3_600_000L
    const val MAX_CLOCK_SKEW_MS = 60_000L

    const val ENC_KEY_INFO = "orbit-file-transfer-enc-v1"
    const val MAC_KEY_INFO = "orbit-file-transfer-mac-v1"

    private val MAGIC_BYTES = "ORFT".toByteArray(Charsets.US_ASCII)
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

    fun buildCanonicalMetadataString(env: RemoteTransferEnvelope): String {
        return listOf(
            env.transferId,
            env.linkId,
            env.senderUid,
            env.targetUid,
            env.senderDeviceId,
            env.targetDeviceId,
            env.fileName,
            env.fileSize.toString(),
            env.mimeType,
            env.plaintextSha256,
            env.encryptedSize.toString(),
            env.storagePath,
            env.encryptionVersion.toString(),
            env.issuedAtMs.toString(),
            env.expiresAtMs.toString()
        ).joinToString("|")
    }

    fun signEnvelope(sharedSecretBase64: String, env: RemoteTransferEnvelope): String {
        val (_, macKey) = deriveKeys(sharedSecretBase64)
        val canonical = buildCanonicalMetadataString(env)
        val mac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(macKey, "HmacSHA256"))
        }
        return Base64.getEncoder().encodeToString(mac.doFinal(canonical.toByteArray(Charsets.UTF_8)))
    }

    fun verifyEnvelopeSignature(sharedSecretBase64: String, env: RemoteTransferEnvelope): Boolean {
        if (sharedSecretBase64.isBlank() || env.signature.isBlank()) return false
        return try {
            val (_, macKey) = deriveKeys(sharedSecretBase64)
            val canonical = buildCanonicalMetadataString(env)
            val provided = Base64.getDecoder().decode(env.signature)
            val mac = Mac.getInstance("HmacSHA256").apply {
                init(SecretKeySpec(macKey, "HmacSHA256"))
            }
            val expected = mac.doFinal(canonical.toByteArray(Charsets.UTF_8))
            MessageDigest.isEqual(expected, provided)
        } catch (_: Exception) {
            false
        }
    }

    fun buildChunkAad(
        version: Int,
        transferId: String,
        chunkIndex: Int,
        isFinal: Boolean,
        plaintextLength: Int,
        plaintextFileSize: Long
    ): ByteArray {
        val finalFlag = if (isFinal) 1 else 0
        return "v$version|$transferId|$chunkIndex|$finalFlag|$plaintextLength|$plaintextFileSize"
            .toByteArray(Charsets.UTF_8)
    }

    fun encryptStream(
        plaintextInput: InputStream,
        encryptedOutput: OutputStream,
        sharedSecretBase64: String,
        transferId: String,
        plaintextFileSize: Long,
        chunkSize: Int = DEFAULT_CHUNK_SIZE,
        onPlaintextBytesProcessed: ((Long) -> Unit)? = null,
        isCancelled: (() -> Boolean)? = null
    ): EncryptStreamResult {
        require(plaintextFileSize in 0..MAX_REMOTE_FILE_SIZE_BYTES) {
            "File size $plaintextFileSize exceeds limit $MAX_REMOTE_FILE_SIZE_BYTES"
        }
        require(chunkSize in 1..MAX_ALLOWED_CHUNK_SIZE) { "Invalid chunk size $chunkSize" }

        val (encKey, _) = deriveKeys(sharedSecretBase64)
        val transferIdBytes = transferId.toByteArray(Charsets.UTF_8)
        require(transferIdBytes.isNotEmpty() && transferIdBytes.size <= 256) { "Invalid transferId" }

        val keySpec = SecretKeySpec(encKey, "AES")
        val digest = MessageDigest.getInstance("SHA-256")
        var bytesWritten = 0L

        // Header prefix: Magic(4) + Version(1) + TransferIdLen(2 BE)
        val prefixBuf = ByteBuffer.allocate(7).order(ByteOrder.BIG_ENDIAN).apply {
            put(MAGIC_BYTES)
            put(PROTOCOL_VERSION.toByte())
            putShort(transferIdBytes.size.toShort())
        }
        encryptedOutput.write(prefixBuf.array())
        encryptedOutput.write(transferIdBytes)
        bytesWritten += 7 + transferIdBytes.size

        // Header suffix: FileSize(8 BE) + MaxChunkSize(4 BE)
        val suffixBuf = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN).apply {
            putLong(plaintextFileSize)
            putInt(chunkSize)
        }
        encryptedOutput.write(suffixBuf.array())
        bytesWritten += 12

        val plainBuf = ByteArray(chunkSize)
        val nonce = ByteArray(GCM_NONCE_BYTES)
        val chunkHeaderBuf = ByteBuffer.allocate(9).order(ByteOrder.BIG_ENDIAN)

        var remaining = plaintextFileSize
        var chunkIndex = 0
        var totalPlaintextProcessed = 0L

        do {
            if (isCancelled?.invoke() == true) {
                throw InterruptedException("Transfer cancelled during encryption.")
            }

            val toRead = minOf(chunkSize.toLong(), remaining).toInt()
            var readTotal = 0
            while (readTotal < toRead) {
                val r = plaintextInput.read(plainBuf, readTotal, toRead - readTotal)
                if (r <= 0) throw EOFException("Unexpected end of plaintext stream.")
                readTotal += r
            }

            remaining -= readTotal
            val isFinal = remaining == 0L

            if (readTotal > 0) {
                digest.update(plainBuf, 0, readTotal)
                totalPlaintextProcessed += readTotal
            }

            secureRandom.nextBytes(nonce)
            val aad = buildChunkAad(PROTOCOL_VERSION, transferId, chunkIndex, isFinal, readTotal, plaintextFileSize)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BYTES * 8, nonce))
                updateAAD(aad)
            }
            val cipherAndTag = cipher.doFinal(plainBuf, 0, readTotal)

            chunkHeaderBuf.clear()
            chunkHeaderBuf.putInt(chunkIndex)
            chunkHeaderBuf.put(if (isFinal) 1.toByte() else 0.toByte())
            chunkHeaderBuf.putInt(readTotal)

            encryptedOutput.write(chunkHeaderBuf.array())
            encryptedOutput.write(nonce)
            encryptedOutput.write(cipherAndTag)

            bytesWritten += 9 + GCM_NONCE_BYTES + cipherAndTag.size
            chunkIndex++
            onPlaintextBytesProcessed?.invoke(totalPlaintextProcessed)
        } while (remaining > 0L)

        val shaHex = digest.digest().joinToString("") { "%02x".format(it) }
        return EncryptStreamResult(bytesWritten, shaHex)
    }

    fun decryptStream(
        encryptedInput: InputStream,
        plaintextOutput: OutputStream,
        sharedSecretBase64: String,
        expectedTransferId: String,
        expectedFileSize: Long,
        expectedPlaintextSha256: String
    ): DecryptStreamResult {
        return try {
            val (encKey, _) = deriveKeys(sharedSecretBase64)
            val keySpec = SecretKeySpec(encKey, "AES")

            val prefix = ByteArray(7)
            if (!readExact(encryptedInput, prefix, prefix.size)) {
                return DecryptStreamResult(false, 0, null, "Truncated header prefix")
            }
            if (!prefix.copyOfRange(0, 4).contentEquals(MAGIC_BYTES)) {
                return DecryptStreamResult(false, 0, null, "Invalid magic bytes")
            }
            val version = prefix[4].toInt() and 0xFF
            if (version != PROTOCOL_VERSION) {
                return DecryptStreamResult(false, 0, null, "Unsupported version $version")
            }

            val transferIdLen = ByteBuffer.wrap(prefix, 5, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
            if (transferIdLen <= 0 || transferIdLen > 256) {
                return DecryptStreamResult(false, 0, null, "Invalid transferId length")
            }

            val transferIdBytes = ByteArray(transferIdLen)
            if (!readExact(encryptedInput, transferIdBytes, transferIdLen)) {
                return DecryptStreamResult(false, 0, null, "Truncated transferId")
            }
            val headerTransferId = String(transferIdBytes, Charsets.UTF_8)
            if (headerTransferId != expectedTransferId) {
                return DecryptStreamResult(false, 0, null, "TransferId mismatch")
            }

            val suffix = ByteArray(12)
            if (!readExact(encryptedInput, suffix, 12)) {
                return DecryptStreamResult(false, 0, null, "Truncated header suffix")
            }
            val suffixBb = ByteBuffer.wrap(suffix).order(ByteOrder.BIG_ENDIAN)
            val headerFileSize = suffixBb.long
            val maxChunkSize = suffixBb.int
            if (headerFileSize != expectedFileSize || headerFileSize < 0 || headerFileSize > MAX_REMOTE_FILE_SIZE_BYTES) {
                return DecryptStreamResult(false, 0, null, "File size mismatch or out of bounds")
            }
            if (maxChunkSize <= 0 || maxChunkSize > MAX_ALLOWED_CHUNK_SIZE) {
                return DecryptStreamResult(false, 0, null, "Chunk size out of bounds")
            }

            val digest = MessageDigest.getInstance("SHA-256")
            val chunkHeader = ByteArray(9)
            val nonce = ByteArray(GCM_NONCE_BYTES)
            var expectedChunkIndex = 0
            var totalPlaintext = 0L
            var sawFinal = false

            while (!sawFinal) {
                if (!readExact(encryptedInput, chunkHeader, 9)) {
                    return DecryptStreamResult(false, totalPlaintext, null, "Truncated chunk header")
                }
                val chBb = ByteBuffer.wrap(chunkHeader).order(ByteOrder.BIG_ENDIAN)
                val chunkIndex = chBb.int
                val flag = chBb.get().toInt() and 0xFF
                val plainLen = chBb.int

                if (chunkIndex != expectedChunkIndex) {
                    return DecryptStreamResult(false, totalPlaintext, null, "Chunk order mismatch")
                }
                if (plainLen < 0 || plainLen > maxChunkSize || (flag != 0 && flag != 1)) {
                    return DecryptStreamResult(false, totalPlaintext, null, "Invalid chunk metadata")
                }
                val isFinal = flag == 1

                if (!readExact(encryptedInput, nonce, GCM_NONCE_BYTES)) {
                    return DecryptStreamResult(false, totalPlaintext, null, "Truncated nonce")
                }

                val cipherAndTag = ByteArray(plainLen + GCM_TAG_BYTES)
                if (!readExact(encryptedInput, cipherAndTag, cipherAndTag.size)) {
                    return DecryptStreamResult(false, totalPlaintext, null, "Truncated ciphertext/tag")
                }

                val aad = buildChunkAad(version, expectedTransferId, chunkIndex, isFinal, plainLen, expectedFileSize)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BYTES * 8, nonce))
                    updateAAD(aad)
                }
                val plain = cipher.doFinal(cipherAndTag)
                if (plain.isNotEmpty()) {
                    plaintextOutput.write(plain)
                    digest.update(plain)
                    totalPlaintext += plain.size
                }
                if (totalPlaintext > expectedFileSize) {
                    return DecryptStreamResult(false, totalPlaintext, null, "Decrypted bytes exceed declared size")
                }

                sawFinal = isFinal
                expectedChunkIndex++
            }

            if (encryptedInput.read() != -1) {
                return DecryptStreamResult(false, totalPlaintext, null, "Trailing bytes after final chunk")
            }
            if (totalPlaintext != expectedFileSize) {
                return DecryptStreamResult(false, totalPlaintext, null, "Total size mismatch")
            }

            val shaHex = digest.digest().joinToString("") { "%02x".format(it) }
            if (expectedPlaintextSha256.isNotBlank() && !shaHex.equals(expectedPlaintextSha256.trim(), ignoreCase = true)) {
                return DecryptStreamResult(false, totalPlaintext, shaHex, "Plaintext SHA-256 mismatch")
            }

            DecryptStreamResult(true, totalPlaintext, shaHex)
        } catch (ex: Exception) {
            DecryptStreamResult(false, 0, null, ex.message ?: "Decryption error")
        }
    }

    private fun readExact(input: InputStream, buf: ByteArray, len: Int): Boolean {
        var total = 0
        while (total < len) {
            val r = input.read(buf, total, len - total)
            if (r <= 0) return false
            total += r
        }
        return true
    }
}
