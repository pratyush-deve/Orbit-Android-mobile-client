package com.pratyush.orbitmobile.domain.model

import android.net.Uri

enum class FileCategory {
    IMAGE,
    VIDEO,
    AUDIO,
    PDF,
    SPREADSHEET,
    DOCUMENT,
    PRESENTATION,
    ARCHIVE,
    OTHER
}

data class SelectedFileItem(
    val id: String,
    val uri: Uri,
    val name: String,
    val size: Long,
    val mimeType: String,
    val category: FileCategory
) {
    val formattedSize: String
        get() = formatBytes(size)

    val isImage: Boolean
        get() = category == FileCategory.IMAGE || name.lowercase().let {
            it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") ||
            it.endsWith(".webp") || it.endsWith(".gif") || it.endsWith(".bmp")
        }

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
            val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
            return if (digitGroups == 0) "$bytes B" else String.format(java.util.Locale.US, "%.1f %s", value, units[digitGroups])
        }

        fun determineCategory(name: String, mimeType: String?): FileCategory {
            val lowerMime = (mimeType ?: "").lowercase()
            val lowerName = name.lowercase()

            return when {
                lowerMime.startsWith("image/") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ||
                        lowerName.endsWith(".png") || lowerName.endsWith(".webp") || lowerName.endsWith(".gif") ||
                        lowerName.endsWith(".bmp") || lowerName.endsWith(".svg") -> FileCategory.IMAGE

                lowerMime.startsWith("video/") || lowerName.endsWith(".mp4") || lowerName.endsWith(".mkv") ||
                        lowerName.endsWith(".mov") || lowerName.endsWith(".avi") || lowerName.endsWith(".webm") -> FileCategory.VIDEO

                lowerMime.startsWith("audio/") || lowerName.endsWith(".mp3") || lowerName.endsWith(".wav") ||
                        lowerName.endsWith(".m4a") || lowerName.endsWith(".flac") || lowerName.endsWith(".aac") -> FileCategory.AUDIO

                lowerMime == "application/pdf" || lowerName.endsWith(".pdf") -> FileCategory.PDF

                lowerMime.contains("spreadsheet") || lowerMime.contains("excel") || lowerMime.contains("csv") ||
                        lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls") || lowerName.endsWith(".xlsm") ||
                        lowerName.endsWith(".csv") || lowerName.endsWith(".ods") -> FileCategory.SPREADSHEET

                lowerMime.contains("presentation") || lowerMime.contains("powerpoint") ||
                        lowerName.endsWith(".pptx") || lowerName.endsWith(".ppt") || lowerName.endsWith(".odp") -> FileCategory.PRESENTATION

                lowerMime.contains("word") || lowerMime.contains("document") || lowerMime.contains("text/") ||
                        lowerName.endsWith(".doc") || lowerName.endsWith(".docx") || lowerName.endsWith(".txt") ||
                        lowerName.endsWith(".rtf") || lowerName.endsWith(".odt") || lowerName.endsWith(".md") ||
                        lowerName.endsWith(".json") || lowerName.endsWith(".xml") -> FileCategory.DOCUMENT

                lowerMime.contains("zip") || lowerMime.contains("tar") || lowerMime.contains("rar") ||
                        lowerMime.contains("7z") || lowerMime.contains("compressed") || lowerName.endsWith(".zip") ||
                        lowerName.endsWith(".rar") || lowerName.endsWith(".7z") || lowerName.endsWith(".tar") ||
                        lowerName.endsWith(".gz") || lowerName.endsWith(".bz2") -> FileCategory.ARCHIVE

                else -> FileCategory.OTHER
            }
        }
    }
}

sealed class FileTransferState {
    data object Idle : FileTransferState()
    data class Preparing(val fileName: String) : FileTransferState()
    data class Transferring(
        val currentFileIndex: Int,
        val totalFiles: Int,
        val currentFileName: String,
        val currentFileProgress: Float, // 0.0 - 1.0
        val overallProgress: Float,      // 0.0 - 1.0
        val bytesTransferred: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Long
    ) : FileTransferState()
    data class Completed(
        val totalFiles: Int,
        val totalBytes: Long,
        val failedFiles: List<SelectedFileItem> = emptyList()
    ) : FileTransferState()
    data class Failed(val error: String, val failedFiles: List<SelectedFileItem> = emptyList()) : FileTransferState()
    data object Cancelled : FileTransferState()
}

data class ReceivedRemoteFile(
    val id: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val receivedAtUtc: Long,
    val temporaryPath: String
)
