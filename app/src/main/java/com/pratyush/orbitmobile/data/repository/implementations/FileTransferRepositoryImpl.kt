package com.pratyush.orbitmobile.data.repository.implementations

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.pratyush.orbitmobile.core.common.OrbitResult
import com.pratyush.orbitmobile.core.firebase.FileTransferCrypto
import com.pratyush.orbitmobile.core.firebase.FirebaseFileTransferClient
import com.pratyush.orbitmobile.data.remote.RemoteDataSource
import com.pratyush.orbitmobile.domain.model.ConnectionState
import com.pratyush.orbitmobile.domain.model.FileCategory
import com.pratyush.orbitmobile.domain.model.FileTransferState
import com.pratyush.orbitmobile.domain.model.ReceivedRemoteFile
import com.pratyush.orbitmobile.domain.model.SelectedFileItem
import com.pratyush.orbitmobile.domain.model.TransferPhase
import com.pratyush.orbitmobile.domain.repository.ConnectionRepository
import com.pratyush.orbitmobile.domain.repository.FileTransferRepository
import com.pratyush.orbitmobile.services.RemoteFileTransferService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

class FileTransferRepositoryImpl(
    private val context: Context,
    private val connectionRepository: ConnectionRepository,
    private val remoteDataSource: RemoteDataSource,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val firebaseFileTransferClient: FirebaseFileTransferClient? = null,
    private val serviceStarter: (title: String, detail: String) -> Unit = { title, detail ->
        RemoteFileTransferService.start(context, title, detail)
    },
    private val serviceStopper: () -> Unit = {
        RemoteFileTransferService.stop(context)
    },
    private val streamOpener: (Uri) -> InputStream? = { uri ->
        context.contentResolver.openInputStream(uri)
    },
    private val uriResolver: ((Uri) -> SelectedFileItem?)? = null
) : FileTransferRepository {

    private val _selectedFiles = MutableStateFlow<List<SelectedFileItem>>(emptyList())
    override val selectedFiles: StateFlow<List<SelectedFileItem>> = _selectedFiles.asStateFlow()

    private val _transferState = MutableStateFlow<FileTransferState>(FileTransferState.Idle)
    override val transferState: StateFlow<FileTransferState> = _transferState.asStateFlow()

    private val transferLock = Any()
    private val stateLock = Any()
    private var transferJob: Job? = null
    @Volatile
    private var isCancelled = false

    companion object {
        private const val TAG = "FileTransferRepo"
        private const val CHUNK_SIZE = 512 * 1024 // 512 KB
    }

    override fun addFiles(uris: List<Uri>): Int {
        val currentList = _selectedFiles.value.toMutableList()
        val existingUris = currentList.map { it.uri.toString() }.toSet()
        var addedCount = 0

        for (uri in uris) {
            if (existingUris.contains(uri.toString())) {
                continue
            }

            val item = uriResolver?.invoke(uri) ?: resolveUri(uri)
            if (item != null) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                currentList.add(item)
                addedCount++
            }
        }

        if (addedCount > 0) {
            _selectedFiles.value = currentList
        }
        return addedCount
    }

    override fun removeFile(fileId: String) {
        _selectedFiles.value = _selectedFiles.value.filter { it.id != fileId }
    }

    override fun clearFiles() {
        _selectedFiles.value = emptyList()
    }

    internal fun setSelectedFilesForTest(files: List<SelectedFileItem>) {
        _selectedFiles.value = files
    }

    override fun isTransferActive(): Boolean = synchronized(transferLock) {
        transferJob?.isActive == true
    }

    private fun updateStateIfActive(currentJob: Job?, newState: FileTransferState) {
        synchronized(stateLock) {
            if (isCancelled) return
            if (currentJob != null && transferJob !== currentJob) return
            _transferState.value = newState
        }
    }

    override fun resetTransferState() {
        synchronized(stateLock) {
            if (!isTransferActive()) {
                _transferState.value = FileTransferState.Idle
            }
        }
    }

    override fun cancelTransfer() {
        synchronized(stateLock) {
            val active = transferJob?.isActive == true ||
                _transferState.value is FileTransferState.Preparing ||
                _transferState.value is FileTransferState.Transferring
            if (active) {
                isCancelled = true
                transferJob?.cancel()
                _transferState.value = FileTransferState.Cancelled
            }
        }
    }

    override fun initiateTransfer(): Boolean = launchTransferIfEligible() != null

    override suspend fun startTransfer(): OrbitResult<Unit> {
        val deferred = launchTransferIfEligible()
        if (deferred == null) {
            val state = _transferState.value
            val msg = when {
                isTransferActive() -> "A file transfer is already in progress"
                _selectedFiles.value.isEmpty() -> "No files selected"
                state is FileTransferState.Failed -> state.error
                else -> "Unable to start transfer"
            }
            return OrbitResult.Error(IllegalStateException(msg))
        }
        return deferred.await()
    }

    private fun launchTransferIfEligible(): CompletableDeferred<OrbitResult<Unit>>? {
        val resultDeferred = CompletableDeferred<OrbitResult<Unit>>()

        synchronized(transferLock) {
            if (transferJob?.isActive == true) {
                return null
            }

            val filesToSend = _selectedFiles.value
            if (filesToSend.isEmpty()) {
                return null
            }

            val lanConnected = connectionRepository.connectionState.value is ConnectionState.Connected &&
                connectionRepository.activeHost != null
            val remoteClient = firebaseFileTransferClient

            if (!lanConnected && (remoteClient == null || !remoteClient.isRemoteAvailable)) {
                val msg = "Laptop is unavailable (neither LAN nor remote connection is active)"
                synchronized(stateLock) {
                    isCancelled = false
                    _transferState.value = FileTransferState.Failed(msg, filesToSend)
                }
                return null
            }

            synchronized(stateLock) {
                isCancelled = false
                _transferState.value = FileTransferState.Preparing(filesToSend.first().name)
            }

            val serviceTitle = if (lanConnected) {
                "Sending ${filesToSend.size} file(s)"
            } else {
                "Sending ${filesToSend.size} file(s) remotely"
            }
            serviceStarter(serviceTitle, "Preparing transfer...")

            lateinit var job: Job
            job = scope.launch {
                val result = try {
                    if (lanConnected) {
                        executeLanTransfer(job, filesToSend)
                    } else {
                        executeRemoteTransfer(job, filesToSend, remoteClient!!)
                    }
                } catch (e: CancellationException) {
                    synchronized(stateLock) {
                        isCancelled = true
                        _transferState.value = FileTransferState.Cancelled
                    }
                    OrbitResult.Success(Unit)
                } catch (e: Exception) {
                    updateStateIfActive(job, FileTransferState.Failed(e.message ?: "Transfer failed", filesToSend))
                    OrbitResult.Error(e)
                } finally {
                    synchronized(transferLock) {
                        if (transferJob === job) {
                            transferJob = null
                            serviceStopper()
                        }
                    }
                }
                resultDeferred.complete(result)
            }
            transferJob = job
        }

        return resultDeferred
    }

    private suspend fun executeLanTransfer(
        currentJob: Job,
        filesToSend: List<SelectedFileItem>
    ): OrbitResult<Unit> = withContext(Dispatchers.IO) {
        val host = connectionRepository.activeHost
            ?: return@withContext OrbitResult.Error(IllegalStateException("Laptop is not connected"))
        val port = connectionRepository.activePort
        val token = connectionRepository.activeAuthToken ?: "anonymous"

        val totalFiles = filesToSend.size
        val totalBytesAll = filesToSend.sumOf { it.size }
        var bytesTransferredAll = 0L
        val failedFiles = mutableListOf<SelectedFileItem>()

        val transferStartTime = System.currentTimeMillis()
        var lastSpeedCalcTime = transferStartTime
        var lastSpeedCalcBytes = 0L
        var currentSpeed = 0L

        try {
            for ((index, file) in filesToSend.withIndex()) {
                if (isCancelled || !coroutineContext.isActive) {
                    synchronized(stateLock) {
                        isCancelled = true
                        _transferState.value = FileTransferState.Cancelled
                    }
                    return@withContext OrbitResult.Success(Unit)
                }

                updateStateIfActive(currentJob, FileTransferState.Preparing(file.name))

                val transferId = UUID.randomUUID().toString()

                // Calculate file SHA-256
                val fileSha256 = calculateSha256(file.uri)
                if (fileSha256 == null) {
                    Log.e(TAG, "Failed to read file for hashing: ${file.name}")
                    failedFiles.add(file)
                    continue
                }

                val totalChunks = if (file.size == 0L) 1 else ((file.size + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt()

                // Init transfer on Windows
                val initRes = remoteDataSource.initFileTransfer(
                    host = host,
                    port = port,
                    authToken = token,
                    transferId = transferId,
                    fileName = file.name,
                    fileSize = file.size,
                    mimeType = file.mimeType,
                    totalChunks = totalChunks,
                    chunkSize = CHUNK_SIZE,
                    sha256 = fileSha256
                )

                if (initRes is OrbitResult.Error) {
                    Log.e(TAG, "Init failed for ${file.name}: ${initRes.exception.message}")
                    failedFiles.add(file)
                    continue
                }

                // Stream chunks
                var fileSuccess = true
                var fileBytesSent = 0L
                val chunkBuffer = ByteArray(CHUNK_SIZE)

                try {
                    streamOpener(file.uri)?.use { inputStream ->
                        var chunkIndex = 0
                        var bytesRead: Int

                        while (inputStream.read(chunkBuffer).also { bytesRead = it } != -1) {
                            if (isCancelled || !coroutineContext.isActive) {
                                remoteDataSource.cancelFileTransfer(host, port, token, transferId)
                                synchronized(stateLock) {
                                    isCancelled = true
                                    _transferState.value = FileTransferState.Cancelled
                                }
                                return@withContext OrbitResult.Success(Unit)
                            }

                            val chunkDigest = MessageDigest.getInstance("SHA-256")
                            chunkDigest.update(chunkBuffer, 0, bytesRead)
                            val chunkSha256 = chunkDigest.digest().joinToString("") { "%02x".format(it) }

                            val chunkRes = remoteDataSource.sendFileChunk(
                                host = host,
                                port = port,
                                authToken = token,
                                transferId = transferId,
                                chunkIndex = chunkIndex,
                                chunkSha256 = chunkSha256,
                                chunkData = chunkBuffer,
                                offset = 0,
                                length = bytesRead
                            )

                            if (chunkRes is OrbitResult.Error) {
                                Log.e(TAG, "Chunk $chunkIndex failed: ${chunkRes.exception.message}")
                                remoteDataSource.cancelFileTransfer(host, port, token, transferId)
                                fileSuccess = false
                                break
                            }

                            fileBytesSent += bytesRead
                            bytesTransferredAll += bytesRead

                            // Calculate speed every 500ms
                            val now = System.currentTimeMillis()
                            val timeDiff = now - lastSpeedCalcTime
                            if (timeDiff >= 500) {
                                val bytesDiff = bytesTransferredAll - lastSpeedCalcBytes
                                currentSpeed = (bytesDiff * 1000L) / timeDiff.coerceAtLeast(1L)
                                lastSpeedCalcTime = now
                                lastSpeedCalcBytes = bytesTransferredAll
                            }

                            val currentProgress = if (file.size > 0) fileBytesSent.toFloat() / file.size else 1f
                            val overallProgress = if (totalBytesAll > 0) bytesTransferredAll.toFloat() / totalBytesAll else 1f

                            updateStateIfActive(
                                currentJob,
                                FileTransferState.Transferring(
                                    currentFileIndex = index + 1,
                                    totalFiles = totalFiles,
                                    currentFileName = file.name,
                                    currentFileProgress = currentProgress.coerceIn(0f, 1f),
                                    overallProgress = overallProgress.coerceIn(0f, 1f),
                                    bytesTransferred = bytesTransferredAll,
                                    totalBytes = totalBytesAll,
                                    speedBytesPerSec = currentSpeed
                                )
                            )

                            chunkIndex++
                        }

                        // Special case for 0-byte file
                        if (file.size == 0L && chunkIndex == 0) {
                            val emptyChunkDigest = MessageDigest.getInstance("SHA-256")
                            val emptySha256 = emptyChunkDigest.digest().joinToString("") { "%02x".format(it) }
                            remoteDataSource.sendFileChunk(host, port, token, transferId, 0, emptySha256, ByteArray(0), 0, 0)
                        }
                    } ?: run {
                        Log.e(TAG, "Cannot open stream for ${file.name}")
                        fileSuccess = false
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e(TAG, "Error streaming ${file.name}: ${e.message}")
                    remoteDataSource.cancelFileTransfer(host, port, token, transferId)
                    fileSuccess = false
                }

                if (fileSuccess) {
                    val overallProgress = if (totalBytesAll > 0) bytesTransferredAll.toFloat() / totalBytesAll else 1f
                    updateStateIfActive(
                        currentJob,
                        FileTransferState.Transferring(
                            currentFileIndex = index + 1,
                            totalFiles = totalFiles,
                            currentFileName = file.name,
                            currentFileProgress = 1f,
                            overallProgress = overallProgress.coerceIn(0f, 1f),
                            bytesTransferred = bytesTransferredAll,
                            totalBytes = totalBytesAll,
                            speedBytesPerSec = currentSpeed,
                            phase = TransferPhase.FINISHING_ON_PC
                        )
                    )
                    val completeRes = remoteDataSource.completeFileTransfer(
                        host = host,
                        port = port,
                        authToken = token,
                        transferId = transferId,
                        sha256 = fileSha256
                    )
                    if (completeRes is OrbitResult.Error) {
                        Log.e(TAG, "Complete failed for ${file.name}: ${completeRes.exception.message}")
                        failedFiles.add(file)
                    }
                } else {
                    failedFiles.add(file)
                }
            }

            if (isCancelled) {
                return@withContext OrbitResult.Success(Unit)
            }

            if (failedFiles.isEmpty()) {
                _selectedFiles.value = emptyList()
                updateStateIfActive(
                    currentJob,
                    FileTransferState.Completed(
                        totalFiles = totalFiles,
                        totalBytes = totalBytesAll,
                        primaryFileName = filesToSend.firstOrNull()?.name
                    )
                )
            } else {
                _selectedFiles.value = failedFiles
                updateStateIfActive(
                    currentJob,
                    FileTransferState.Completed(
                        totalFiles = totalFiles - failedFiles.size,
                        totalBytes = totalBytesAll,
                        failedFiles = failedFiles,
                        primaryFileName = filesToSend.firstOrNull()?.name
                    )
                )
            }

            OrbitResult.Success(Unit)
        } catch (e: CancellationException) {
            synchronized(stateLock) {
                isCancelled = true
                _transferState.value = FileTransferState.Cancelled
            }
            OrbitResult.Success(Unit)
        } catch (e: Exception) {
            updateStateIfActive(currentJob, FileTransferState.Failed(e.message ?: "Transfer failed", failedFiles))
            OrbitResult.Error(e)
        }
    }

    private suspend fun executeRemoteTransfer(
        currentJob: Job,
        filesToSend: List<SelectedFileItem>,
        remoteClient: FirebaseFileTransferClient
    ): OrbitResult<Unit> = withContext(Dispatchers.IO) {
        val totalFiles = filesToSend.size
        val totalBytesAll = filesToSend.sumOf { it.size }
        var bytesTransferredBase = 0L
        val failedFiles = mutableListOf<SelectedFileItem>()

        val transferStartTime = System.currentTimeMillis()
        var lastSpeedCalcTime = transferStartTime
        var lastSpeedCalcBytes = 0L
        var currentSpeed = 0L

        try {
            for ((index, file) in filesToSend.withIndex()) {
                if (isCancelled || !coroutineContext.isActive) {
                    synchronized(stateLock) {
                        isCancelled = true
                        _transferState.value = FileTransferState.Cancelled
                    }
                    return@withContext OrbitResult.Success(Unit)
                }

                if (file.size > FileTransferCrypto.MAX_REMOTE_FILE_SIZE_BYTES) {
                    Log.w(TAG, "File '${file.name}' (${file.size} bytes) exceeds remote limit.")
                    failedFiles.add(file)
                    continue
                }

                updateStateIfActive(currentJob, FileTransferState.Preparing(file.name, TransferPhase.ENCRYPTING))

                val transferId = UUID.randomUUID().toString()
                val res = remoteClient.sendFileRemote(
                    transferId = transferId,
                    uri = file.uri,
                    fileName = file.name,
                    fileSize = file.size,
                    mimeType = file.mimeType,
                    isCancelled = { isCancelled },
                    onProgress = { fileBytes, _ ->
                        val totalNow = bytesTransferredBase + fileBytes
                        val now = System.currentTimeMillis()
                        val timeDiff = now - lastSpeedCalcTime
                        if (timeDiff >= 500) {
                            val bytesDiff = totalNow - lastSpeedCalcBytes
                            currentSpeed = (bytesDiff * 1000L) / timeDiff.coerceAtLeast(1L)
                            lastSpeedCalcTime = now
                            lastSpeedCalcBytes = totalNow
                        }
                        val currentProgress = if (file.size > 0) fileBytes.toFloat() / file.size else 1f
                        val overallProgress = if (totalBytesAll > 0) totalNow.toFloat() / totalBytesAll else 1f
                        updateStateIfActive(
                            currentJob,
                            FileTransferState.Transferring(
                                currentFileIndex = index + 1,
                                totalFiles = totalFiles,
                                currentFileName = file.name,
                                currentFileProgress = currentProgress.coerceIn(0f, 1f),
                                overallProgress = overallProgress.coerceIn(0f, 1f),
                                bytesTransferred = totalNow,
                                totalBytes = totalBytesAll,
                                speedBytesPerSec = currentSpeed,
                                phase = TransferPhase.TRANSFERRING
                            )
                        )
                    },
                    onPhaseChanged = { phase ->
                        when (phase) {
                            TransferPhase.ENCRYPTING -> {
                                updateStateIfActive(currentJob, FileTransferState.Preparing(file.name, TransferPhase.ENCRYPTING))
                            }
                            TransferPhase.FINISHING_ON_PC -> {
                                val totalNow = bytesTransferredBase + file.size
                                val overallProgress = if (totalBytesAll > 0) totalNow.toFloat() / totalBytesAll else 1f
                                updateStateIfActive(
                                    currentJob,
                                    FileTransferState.Transferring(
                                        currentFileIndex = index + 1,
                                        totalFiles = totalFiles,
                                        currentFileName = file.name,
                                        currentFileProgress = 1f,
                                        overallProgress = overallProgress.coerceIn(0f, 1f),
                                        bytesTransferred = totalNow,
                                        totalBytes = totalBytesAll,
                                        speedBytesPerSec = currentSpeed,
                                        phase = TransferPhase.FINISHING_ON_PC
                                    )
                                )
                            }
                            else -> Unit
                        }
                    }
                )

                if (res is OrbitResult.Success) {
                    bytesTransferredBase += file.size
                } else {
                    if (isCancelled) {
                        synchronized(stateLock) {
                            isCancelled = true
                            _transferState.value = FileTransferState.Cancelled
                        }
                        return@withContext OrbitResult.Success(Unit)
                    }
                    failedFiles.add(file)
                }
            }

            if (isCancelled) {
                return@withContext OrbitResult.Success(Unit)
            }

            return@withContext if (failedFiles.isEmpty()) {
                _selectedFiles.value = emptyList()
                updateStateIfActive(
                    currentJob,
                    FileTransferState.Completed(
                        totalFiles = totalFiles,
                        totalBytes = totalBytesAll,
                        primaryFileName = filesToSend.firstOrNull()?.name
                    )
                )
                OrbitResult.Success(Unit)
            } else {
                _selectedFiles.value = failedFiles
                updateStateIfActive(
                    currentJob,
                    FileTransferState.Completed(
                        totalFiles = totalFiles - failedFiles.size,
                        totalBytes = totalBytesAll,
                        failedFiles = failedFiles,
                        primaryFileName = filesToSend.firstOrNull()?.name
                    )
                )
                OrbitResult.Error(IllegalStateException("${failedFiles.size} remote transfer(s) failed or exceeded 50 MB limit."))
            }
        } catch (e: CancellationException) {
            synchronized(stateLock) {
                isCancelled = true
                _transferState.value = FileTransferState.Cancelled
            }
            return@withContext OrbitResult.Success(Unit)
        } catch (e: Exception) {
            updateStateIfActive(currentJob, FileTransferState.Failed(e.message ?: "Remote transfer failed", failedFiles))
            return@withContext OrbitResult.Error(e)
        }
    }

    override suspend fun fetchReceivedFiles(): OrbitResult<List<ReceivedRemoteFile>> {
        val host = connectionRepository.activeHost ?: return OrbitResult.Error(IllegalStateException("Not connected"))
        val port = connectionRepository.activePort
        val token = connectionRepository.activeAuthToken ?: "anonymous"
        return remoteDataSource.fetchReceivedFiles(host, port, token)
    }

    private fun resolveUri(uri: Uri): SelectedFileItem? {
        return try {
            var name: String? = null
            var size = 0L

            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        name = it.getString(nameIndex)
                    }
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1 && !it.isNull(sizeIndex)) {
                        size = it.getLong(sizeIndex)
                    }
                }
            }

            if (name.isNullOrBlank()) {
                name = uri.lastPathSegment ?: "file_${System.currentTimeMillis()}"
            }
            name = name!!.substringAfterLast('/')

            var mimeType = context.contentResolver.getType(uri)
            if (mimeType.isNullOrBlank() || mimeType == "application/octet-stream") {
                val extFromFileName = name!!.substringAfterLast('.', "").lowercase()
                if (extFromFileName.isNotEmpty()) {
                    mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extFromFileName)
                }
            }
            if (mimeType.isNullOrBlank()) {
                mimeType = "application/octet-stream"
            }

            if (!name!!.contains('.')) {
                val ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                if (!ext.isNullOrBlank()) {
                    name = "$name.$ext"
                } else {
                    val customExt = when (mimeType.lowercase()) {
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx"
                        "application/vnd.ms-excel" -> "xls"
                        "text/csv" -> "csv"
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
                        "application/msword" -> "doc"
                        "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "pptx"
                        "application/vnd.ms-powerpoint" -> "ppt"
                        "application/pdf" -> "pdf"
                        "application/zip" -> "zip"
                        "application/x-7z-compressed" -> "7z"
                        "application/x-rar-compressed", "application/vnd.rar" -> "rar"
                        "image/jpeg" -> "jpg"
                        "image/png" -> "png"
                        "image/webp" -> "webp"
                        "image/gif" -> "gif"
                        "image/bmp" -> "bmp"
                        "video/mp4" -> "mp4"
                        "video/x-matroska" -> "mkv"
                        "audio/mpeg" -> "mp3"
                        "audio/wav", "audio/x-wav" -> "wav"
                        else -> null
                    }
                    if (customExt != null) {
                        name = "$name.$customExt"
                    }
                }
            }

            if (size <= 0L) {
                try {
                    context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                        if (afd.length > 0L) {
                            size = afd.length
                        }
                    }
                } catch (_: Exception) {}
            }

            if (size <= 0L) {
                try {
                    streamOpener(uri)?.use { stream ->
                        var count = 0L
                        val buf = ByteArray(16384)
                        var read: Int
                        while (stream.read(buf).also { read = it } != -1) {
                            count += read
                        }
                        if (count > 0L) {
                            size = count
                        }
                    }
                } catch (_: Exception) {}
            }

            val category = SelectedFileItem.determineCategory(name!!, mimeType)

            SelectedFileItem(
                id = UUID.randomUUID().toString(),
                uri = uri,
                name = name!!,
                size = size.coerceAtLeast(0L),
                mimeType = mimeType,
                category = category
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve uri: $uri", e)
            null
        }
    }

    private fun calculateSha256(uri: Uri): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            streamOpener(uri)?.use { stream ->
                val buffer = ByteArray(65536)
                var read: Int
                while (stream.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating SHA-256 for $uri", e)
            null
        }
    }
}
