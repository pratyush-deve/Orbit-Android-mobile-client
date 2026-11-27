package com.pratyush.orbitmobile.core.firebase

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.pratyush.orbitmobile.core.common.OrbitResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Phase 4.1 Android -> Windows remote file transfer client:
 * - Encrypts SAF InputStream incrementally via FileTransferCrypto (ORFT v1, AES-256-GCM)
 * - Uploads encrypted payload to Firebase Cloud Storage via resumable REST upload
 * - Publishes HMAC-signed metadata to RTDB /links/{linkId}/transfers/events/{transferId}
 * - Monitors Windows acknowledgement on /links/{linkId}/transfers/acks/{transferId}
 * - Cleans up temp files and unfinished cloud blobs on failure/cancellation.
 */
class FirebaseFileTransferClient(
    private val context: Context,
    private val identityManager: FirebaseIdentityManager,
    private val linkStore: RemoteLinkStore,
    private val presenceManager: FirebasePresenceManager,
    private val clientDeviceId: String,
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance(FirebasePresenceManager.RTDB_URL),
    private val storageApiBaseUrl: String = STORAGE_API_BASE_URL
) {
    companion object {
        private const val TAG = "OrbitRemoteFileClient"
        const val STORAGE_BUCKET = "orbit-4c44d.firebasestorage.app"
        const val STORAGE_API_BASE_URL = "https://firebasestorage.googleapis.com/v0/b/$STORAGE_BUCKET/o"
        private const val UPLOAD_CHUNK_SIZE = 512 * 1024 // 512 KB resumable PUT chunks
        private const val ACK_TIMEOUT_MS = 120_000L
        private const val MAX_UPLOAD_RETRIES = 2
    }

    val isRemoteAvailable: Boolean
        get() = linkStore.loadLink() != null && presenceManager.peerPresence.value?.online == true

    suspend fun sendFileRemote(
        transferId: String,
        uri: Uri,
        fileName: String,
        fileSize: Long,
        mimeType: String,
        isCancelled: () -> Boolean,
        onProgress: (bytesTransferred: Long, totalBytes: Long) -> Unit
    ): OrbitResult<Unit> = withContext(Dispatchers.IO) {
        if (fileSize < 0 || fileSize > FileTransferCrypto.MAX_REMOTE_FILE_SIZE_BYTES) {
            return@withContext OrbitResult.Error(
                IllegalArgumentException(
                    "File '$fileName' (${fileSize / (1024 * 1024)} MB) exceeds remote limit of ${FileTransferCrypto.MAX_REMOTE_FILE_SIZE_BYTES / (1024 * 1024)} MB."
                )
            )
        }

        val link = linkStore.loadLink()
            ?: return@withContext OrbitResult.Error(IllegalStateException("No remote link configured."))

        if (link.peerUid.isBlank()) {
            return@withContext OrbitResult.Error(IllegalStateException("Remote peer UID is missing; re-pair device."))
        }

        val senderUid = identityManager.ensureSignedIn()
            ?: return@withContext OrbitResult.Error(IllegalStateException("Firebase authentication unavailable."))
        val idToken = identityManager.getValidIdToken()
            ?: return@withContext OrbitResult.Error(IllegalStateException("Firebase ID token unavailable."))

        val storagePath = "links/${link.linkId}/transfers/$transferId/$senderUid/${link.peerUid}/payload.bin"
        val encTempFile = File(context.cacheDir, "orbit_enc_$transferId.tmp")

        val eventsRef = database.getReference("links").child(link.linkId).child("transfers").child("events").child(transferId)
        val acksRef = database.getReference("links").child(link.linkId).child("transfers").child("acks").child(transferId)

        var uploadedToStorage = false
        var eventPublished = false

        try {
            // 1. Encrypt SAF stream incrementally to temp file (never loads full file into memory)
            val encryptResult = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(encTempFile).use { outputStream ->
                    FileTransferCrypto.encryptStream(
                        plaintextInput = inputStream,
                        encryptedOutput = outputStream,
                        sharedSecretBase64 = link.sharedSecret,
                        transferId = transferId,
                        plaintextFileSize = fileSize,
                        isCancelled = isCancelled
                    )
                }
            } ?: return@withContext OrbitResult.Error(IllegalStateException("Cannot open selected file URI."))

            if (isCancelled()) {
                return@withContext OrbitResult.Error(CancellationException("Transfer cancelled."))
            }

            val encryptedSize = encryptResult.encryptedSize

            // 2. Resumable upload to Firebase Storage
            val uploaded = uploadResumableWithRetry(
                encFile = encTempFile,
                storagePath = storagePath,
                idToken = idToken,
                plaintextFileSize = fileSize,
                isCancelled = isCancelled,
                onProgress = onProgress
            )
            if (!uploaded) {
                return@withContext OrbitResult.Error(
                    if (isCancelled()) CancellationException("Transfer cancelled.")
                    else IllegalStateException("Failed to upload encrypted file to Firebase Storage.")
                )
            }
            uploadedToStorage = true

            // 3. Attach ack listener BEFORE publishing event to avoid race
            val ackDeferred = CompletableDeferred<Pair<String, String?>>()
            val ackListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) return
                    val status = snapshot.child("status").getValue(String::class.java) ?: return
                    val error = snapshot.child("error").getValue(String::class.java)
                    if (status == "Completed" || status == "Failed" || status == "Rejected" || status == "Expired") {
                        ackDeferred.complete(status to error)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    ackDeferred.complete("Failed" to error.message)
                }
            }
            acksRef.addValueEventListener(ackListener)

            try {
                val nowMs = System.currentTimeMillis()
                val unsignedEnv = RemoteTransferEnvelope(
                    transferId = transferId,
                    linkId = link.linkId,
                    senderUid = senderUid,
                    targetUid = link.peerUid,
                    senderDeviceId = clientDeviceId,
                    targetDeviceId = link.peerDeviceId,
                    fileName = fileName,
                    fileSize = fileSize,
                    mimeType = mimeType,
                    plaintextSha256 = encryptResult.plaintextSha256,
                    encryptedSize = encryptedSize,
                    storagePath = storagePath,
                    encryptionVersion = FileTransferCrypto.PROTOCOL_VERSION,
                    issuedAtMs = nowMs,
                    expiresAtMs = nowMs + FileTransferCrypto.TRANSFER_TTL_MS,
                    status = "Available"
                )
                val signature = FileTransferCrypto.signEnvelope(link.sharedSecret, unsignedEnv)
                val signedEnv = unsignedEnv.copy(signature = signature)

                eventsRef.setValue(signedEnv.toMap()).await()
                eventPublished = true
                Log.i(TAG, "Published remote transfer event $transferId ($fileName, $fileSize bytes)")

                val ackResult = withTimeoutOrNull(ACK_TIMEOUT_MS) {
                    while (!ackDeferred.isCompleted) {
                        if (isCancelled()) {
                            return@withTimeoutOrNull "Cancelled" to "Cancelled by user"
                        }
                        delay(250)
                    }
                    ackDeferred.await()
                }

                if (ackResult == null) {
                    return@withContext OrbitResult.Error(Exception("Windows PC did not acknowledge remote transfer in time."))
                }

                val (status, error) = ackResult
                if (status == "Completed") {
                    onProgress(fileSize, fileSize)
                    return@withContext OrbitResult.Success(Unit)
                } else {
                    if (uploadedToStorage) {
                        deleteStorageObjectBestEffort(storagePath, idToken)
                    }
                    return@withContext OrbitResult.Error(Exception(error ?: "Remote transfer ended with status: $status"))
                }
            } finally {
                acksRef.removeEventListener(ackListener)
            }
        } catch (e: CancellationException) {
            if (eventPublished) {
                runCatching { eventsRef.child("status").setValue("Cancelled").await() }
            }
            if (uploadedToStorage) {
                deleteStorageObjectBestEffort(storagePath, idToken)
            }
            return@withContext OrbitResult.Error(e)
        } catch (e: InterruptedException) {
            if (uploadedToStorage) {
                deleteStorageObjectBestEffort(storagePath, idToken)
            }
            return@withContext OrbitResult.Error(CancellationException(e.message ?: "Cancelled"))
        } catch (e: Exception) {
            Log.e(TAG, "Remote transfer error for $transferId: ${e.message}", e)
            if (eventPublished) {
                runCatching { eventsRef.child("status").setValue("Failed").await() }
            }
            if (uploadedToStorage) {
                deleteStorageObjectBestEffort(storagePath, idToken)
            }
            return@withContext OrbitResult.Error(e)
        } finally {
            runCatching { if (encTempFile.exists()) encTempFile.delete() }
        }
    }

    private suspend fun uploadResumableWithRetry(
        encFile: File,
        storagePath: String,
        idToken: String,
        plaintextFileSize: Long,
        isCancelled: () -> Boolean,
        onProgress: (Long, Long) -> Unit
    ): Boolean {
        for (attempt in 0..MAX_UPLOAD_RETRIES) {
            if (isCancelled()) return false
            try {
                if (uploadResumable(encFile, storagePath, idToken, plaintextFileSize, isCancelled, onProgress)) {
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Storage upload attempt ${attempt + 1} failed: ${e.message}")
            }
            if (attempt < MAX_UPLOAD_RETRIES) {
                delay(1000L * (attempt + 1))
            }
        }
        return false
    }

    private fun uploadResumable(
        encFile: File,
        storagePath: String,
        idToken: String,
        plaintextFileSize: Long,
        isCancelled: () -> Boolean,
        onProgress: (Long, Long) -> Unit
    ): Boolean {
        val totalEncBytes = encFile.length()
        val encodedName = URLEncoder.encode(storagePath, "UTF-8")
        val initUrl = URL("$storageApiBaseUrl?uploadType=resumable&name=$encodedName")

        val initConn = (initUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Firebase $idToken")
            setRequestProperty("X-Goog-Upload-Protocol", "resumable")
            setRequestProperty("X-Goog-Upload-Command", "start")
            setRequestProperty("X-Goog-Upload-Header-Content-Length", totalEncBytes.toString())
            setRequestProperty("X-Goog-Upload-Header-Content-Type", "application/octet-stream")
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }

        val sessionUri = try {
            initConn.outputStream.use { it.write("{\"contentType\":\"application/octet-stream\"}".toByteArray()) }
            val code = initConn.responseCode
            if (code !in 200..299) {
                Log.w(TAG, "Resumable upload init failed: HTTP $code")
                return false
            }
            initConn.getHeaderField("X-Goog-Upload-URL") ?: initConn.getHeaderField("Location")
        } finally {
            initConn.disconnect()
        }

        if (sessionUri.isNullOrBlank()) {
            Log.w(TAG, "Resumable upload session URI missing.")
            return false
        }

        FileInputStream(encFile).use { fis ->
            val buffer = ByteArray(UPLOAD_CHUNK_SIZE)
            var offset = 0L

            while (offset < totalEncBytes) {
                if (isCancelled()) return false

                val read = fis.read(buffer)
                if (read <= 0) break
                val isLast = offset + read >= totalEncBytes
                val command = if (isLast) "upload, finalize" else "upload"

                val chunkConn = (URL(sessionUri).openConnection() as HttpURLConnection).apply {
                    requestMethod = "PUT"
                    doOutput = true
                    connectTimeout = 15_000
                    readTimeout = 60_000
                    setFixedLengthStreamingMode(read)
                    setRequestProperty("X-Goog-Upload-Offset", offset.toString())
                    setRequestProperty("X-Goog-Upload-Command", command)
                    setRequestProperty("Content-Type", "application/octet-stream")
                }

                try {
                    chunkConn.outputStream.use { it.write(buffer, 0, read) }
                    val code = chunkConn.responseCode
                    if (isLast && code !in 200..299) {
                        Log.w(TAG, "Final resumable chunk failed: HTTP $code")
                        return false
                    }
                    if (!isLast && code !in 200..399) {
                        Log.w(TAG, "Intermediate resumable chunk failed: HTTP $code")
                        return false
                    }
                } finally {
                    chunkConn.disconnect()
                }

                offset += read
                val scaledProgress = if (totalEncBytes > 0) {
                    ((offset.toDouble() / totalEncBytes.toDouble()) * plaintextFileSize).toLong().coerceIn(0L, plaintextFileSize)
                } else plaintextFileSize
                onProgress(scaledProgress, plaintextFileSize)
            }
        }
        return true
    }

    private fun deleteStorageObjectBestEffort(storagePath: String, idToken: String) {
        try {
            val encodedName = URLEncoder.encode(storagePath, "UTF-8")
            val url = URL("$storageApiBaseUrl/$encodedName")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Authorization", "Firebase $idToken")
            }
            try {
                conn.responseCode
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
        }
    }
}
