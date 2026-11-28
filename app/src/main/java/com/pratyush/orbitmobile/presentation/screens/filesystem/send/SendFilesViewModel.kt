package com.pratyush.orbitmobile.presentation.screens.filesystem.send

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pratyush.orbitmobile.domain.model.FileTransferState
import com.pratyush.orbitmobile.domain.model.SelectedFileItem
import com.pratyush.orbitmobile.domain.repository.FileTransferRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SendFilesViewModel(
    private val fileTransferRepository: FileTransferRepository
) : ViewModel() {

    val selectedFiles: StateFlow<List<SelectedFileItem>> = fileTransferRepository.selectedFiles

    val transferState: StateFlow<FileTransferState> = fileTransferRepository.transferState

    val totalCount: StateFlow<Int> = selectedFiles
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalSizeBytes: StateFlow<Long> = selectedFiles
        .map { list -> list.sumOf { it.size } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val totalSizeFormatted: StateFlow<String> = totalSizeBytes
        .map { SelectedFileItem.formatBytes(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "0 B")

    fun onFilesSelected(uris: List<Uri>) {
        fileTransferRepository.addFiles(uris)
    }

    fun onRemoveFile(fileId: String) {
        fileTransferRepository.removeFile(fileId)
    }

    fun onClearAll() {
        fileTransferRepository.clearFiles()
    }

    fun onStartSend() {
        fileTransferRepository.initiateTransfer()
    }

    fun onCancelTransfer() {
        fileTransferRepository.cancelTransfer()
    }

    fun onDismissResult() {
        fileTransferRepository.resetTransferState()
    }
}
