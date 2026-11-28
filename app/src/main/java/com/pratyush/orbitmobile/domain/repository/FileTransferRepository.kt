package com.pratyush.orbitmobile.domain.repository

import android.net.Uri
import com.pratyush.orbitmobile.core.common.OrbitResult
import com.pratyush.orbitmobile.domain.model.FileTransferState
import com.pratyush.orbitmobile.domain.model.ReceivedRemoteFile
import com.pratyush.orbitmobile.domain.model.SelectedFileItem
import kotlinx.coroutines.flow.StateFlow

interface FileTransferRepository {
    val selectedFiles: StateFlow<List<SelectedFileItem>>
    val transferState: StateFlow<FileTransferState>

    fun addFiles(uris: List<Uri>): Int
    fun removeFile(fileId: String)
    fun clearFiles()

    fun initiateTransfer(): Boolean = false
    suspend fun startTransfer(): OrbitResult<Unit>
    fun cancelTransfer()
    fun resetTransferState()
    fun isTransferActive(): Boolean =
        transferState.value is FileTransferState.Preparing ||
            transferState.value is FileTransferState.Transferring

    suspend fun fetchReceivedFiles(): OrbitResult<List<ReceivedRemoteFile>>
}
