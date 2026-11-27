package com.pratyush.orbitmobile.core.firebase

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom

class FileTransferCryptoTest {

    private val sharedSecretBase64 = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="

    @Test
    fun deriveKeys_domainSeparatedFromClipboard() {
        val (fileEnc, fileMac) = FileTransferCrypto.deriveKeys(sharedSecretBase64)
        val (clipEnc, clipMac) = ClipboardCrypto.deriveKeys(sharedSecretBase64)

        assertFalse(fileEnc.contentEquals(clipEnc))
        assertFalse(fileMac.contentEquals(clipMac))
        assertFalse(fileEnc.contentEquals(fileMac))
    }

    @Test
    fun encryptDecrypt_roundTrip_emptySmallAndMultiChunk() {
        val sizes = listOf(0, 17, 600 * 1024)
        for (size in sizes) {
            val plain = ByteArray(size).also { if (size > 0) SecureRandom().nextBytes(it) }
            val transferId = "kt-tx-$size"
            val encOut = ByteArrayOutputStream()

            val encRes = FileTransferCrypto.encryptStream(
                plaintextInput = ByteArrayInputStream(plain),
                encryptedOutput = encOut,
                sharedSecretBase64 = sharedSecretBase64,
                transferId = transferId,
                plaintextFileSize = plain.size.toLong()
            )
            assertEquals(encOut.size().toLong(), encRes.encryptedSize)

            val decOut = ByteArrayOutputStream()
            val decRes = FileTransferCrypto.decryptStream(
                encryptedInput = ByteArrayInputStream(encOut.toByteArray()),
                plaintextOutput = decOut,
                sharedSecretBase64 = sharedSecretBase64,
                expectedTransferId = transferId,
                expectedFileSize = plain.size.toLong(),
                expectedPlaintextSha256 = encRes.plaintextSha256
            )

            assertTrue(decRes.error ?: "", decRes.success)
            assertEquals(plain.size.toLong(), decRes.plaintextBytesWritten)
            assertEquals(encRes.plaintextSha256, decRes.computedPlaintextSha256)
            assertArrayEquals(plain, decOut.toByteArray())
        }
    }

    @Test
    fun decryptStream_corruptedChunk_rejected() {
        val plain = "Corrupted chunk test".toByteArray(Charsets.UTF_8)
        val transferId = "kt-corrupt"
        val encOut = ByteArrayOutputStream()
        val encRes = FileTransferCrypto.encryptStream(
            ByteArrayInputStream(plain), encOut, sharedSecretBase64, transferId, plain.size.toLong()
        )
        val bytes = encOut.toByteArray()
        bytes[bytes.size - 5] = (bytes[bytes.size - 5].toInt() xor 0xFF).toByte()

        val decRes = FileTransferCrypto.decryptStream(
            ByteArrayInputStream(bytes), ByteArrayOutputStream(),
            sharedSecretBase64, transferId, plain.size.toLong(), encRes.plaintextSha256
        )
        assertFalse(decRes.success)
    }

    @Test
    fun decryptStream_wrongKey_rejected() {
        val plain = "Wrong key test".toByteArray(Charsets.UTF_8)
        val transferId = "kt-wrong-key"
        val encOut = ByteArrayOutputStream()
        val encRes = FileTransferCrypto.encryptStream(
            ByteArrayInputStream(plain), encOut, sharedSecretBase64, transferId, plain.size.toLong()
        )
        val wrongSecret = java.util.Base64.getEncoder().encodeToString(ByteArray(32))
        val decRes = FileTransferCrypto.decryptStream(
            ByteArrayInputStream(encOut.toByteArray()), ByteArrayOutputStream(),
            wrongSecret, transferId, plain.size.toLong(), encRes.plaintextSha256
        )
        assertFalse(decRes.success)
    }

    @Test
    fun decryptStream_swappedChunkOrder_rejected() {
        val plain = ByteArray(128 * 1024).also { SecureRandom().nextBytes(it) }
        val transferId = "kt-reorder"
        val encOut = ByteArrayOutputStream()
        val encRes = FileTransferCrypto.encryptStream(
            ByteArrayInputStream(plain), encOut, sharedSecretBase64, transferId, plain.size.toLong(), chunkSize = 64 * 1024
        )
        val bytes = encOut.toByteArray()
        val headerLen = 7 + transferId.toByteArray(Charsets.UTF_8).size + 12
        val chunkSize = 9 + 12 + (64 * 1024) + 16
        val reordered = ByteArray(bytes.size)
        System.arraycopy(bytes, 0, reordered, 0, headerLen)
        System.arraycopy(bytes, headerLen + chunkSize, reordered, headerLen, chunkSize)
        System.arraycopy(bytes, headerLen, reordered, headerLen + chunkSize, chunkSize)

        val decRes = FileTransferCrypto.decryptStream(
            ByteArrayInputStream(reordered), ByteArrayOutputStream(),
            sharedSecretBase64, transferId, plain.size.toLong(), encRes.plaintextSha256
        )
        assertFalse(decRes.success)
        assertEquals("Chunk order mismatch", decRes.error)
    }

    @Test
    fun decryptStream_sha256Mismatch_rejected() {
        val plain = "Checksum verification".toByteArray(Charsets.UTF_8)
        val transferId = "kt-sha"
        val encOut = ByteArrayOutputStream()
        FileTransferCrypto.encryptStream(
            ByteArrayInputStream(plain), encOut, sharedSecretBase64, transferId, plain.size.toLong()
        )
        val decRes = FileTransferCrypto.decryptStream(
            ByteArrayInputStream(encOut.toByteArray()), ByteArrayOutputStream(),
            sharedSecretBase64, transferId, plain.size.toLong(), "0".repeat(64)
        )
        assertFalse(decRes.success)
    }

    @Test(expected = IllegalArgumentException::class)
    fun encryptStream_oversizedFile_rejected() {
        FileTransferCrypto.encryptStream(
            ByteArrayInputStream(ByteArray(0)),
            ByteArrayOutputStream(),
            sharedSecretBase64,
            "kt-big",
            FileTransferCrypto.MAX_REMOTE_FILE_SIZE_BYTES + 1
        )
    }

    @Test
    fun crossPlatformVector_matchesCSharpSignatureAndAad() {
        val env = RemoteTransferEnvelope(
            transferId = "vec-tx-001",
            linkId = "link-abc",
            senderUid = "android-uid-1",
            targetUid = "win-uid-1",
            senderDeviceId = "android-dev-1",
            targetDeviceId = "win-dev-1",
            fileName = "hello.txt",
            fileSize = 5L,
            mimeType = "text/plain",
            plaintextSha256 = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            encryptedSize = 62L,
            storagePath = "links/link-abc/transfers/vec-tx-001/android-uid-1/win-uid-1/payload.bin",
            encryptionVersion = 1,
            issuedAtMs = 1700000000000L,
            expiresAtMs = 1700003600000L
        )
        val sig = FileTransferCrypto.signEnvelope(sharedSecretBase64, env)
        val signed = env.copy(signature = sig)
        assertTrue(FileTransferCrypto.verifyEnvelopeSignature(sharedSecretBase64, signed))
        assertEquals(
            "v1|vec-tx-001|0|1|5|5",
            String(FileTransferCrypto.buildChunkAad(1, "vec-tx-001", 0, true, 5, 5L), Charsets.UTF_8)
        )
        assertEquals("ZC+2TkMB1J6AMbem9q7zLIwq/3K6AqNAyixTkck7u88=", sig)
    }
}
