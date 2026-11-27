package com.pratyush.orbitmobile.presentation.screens.filesystem.send

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.collection.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pratyush.orbitmobile.domain.model.FileCategory
import com.pratyush.orbitmobile.domain.model.FileTransferState
import com.pratyush.orbitmobile.domain.model.SelectedFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SendFilesScreen(
    viewModel: SendFilesViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedFiles by viewModel.selectedFiles.collectAsState()
    val transferState by viewModel.transferState.collectAsState()
    val totalCount by viewModel.totalCount.collectAsState()
    val totalSizeFormatted by viewModel.totalSizeFormatted.collectAsState()

    var showPlusMenu by remember { mutableStateOf(false) }
    var previewingFile by remember { mutableStateOf<SelectedFileItem?>(null) }

    // Activity Result Launchers
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.onFilesSelected(uris)
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.onFilesSelected(uris)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090810))
            .statusBarsPadding()
            .navigationBarsPadding()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (showPlusMenu) showPlusMenu = false
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            // Top App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFF1A1829), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = "Send Files to Laptop",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                )
            }

            // Selection Header (Count + Clear All)
            if (selectedFiles.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$totalCount ${if (totalCount == 1) "File" else "Files"} Selected",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    )

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { viewModel.onClearAll() }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Clear All",
                            tint = Color(0xFFA855F7),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Clear All",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color(0xFFA855F7),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        )
                    }
                }
            }

            // Main Content: Empty State OR Selected Files List
            if (selectedFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        // Dashed Outline Container with Floating Icons
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .border(
                                    width = 1.5.dp,
                                    brush = Brush.horizontalGradient(
                                        listOf(Color(0x66A855F7), Color(0x33A855F7))
                                    ),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .background(Color(0x0DA855F7), RoundedCornerShape(20.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                // Upload Cloud Icon
                                Icon(
                                    imageVector = Icons.Outlined.CloudUpload,
                                    contentDescription = null,
                                    tint = Color(0xFFC084FC),
                                    modifier = Modifier.size(54.dp)
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                // Mini Category Icons
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Image,
                                        contentDescription = null,
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Icon(
                                        imageVector = Icons.Outlined.Description,
                                        contentDescription = null,
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Icon(
                                        imageVector = Icons.Outlined.VideoLibrary,
                                        contentDescription = null,
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "No Files Selected",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Add files from your gallery or file\nsystem to send to your laptop.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color(0xFF94A3B8),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                        )
                    }
                }
            } else {
                // List of Selected Files
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 90.dp)
                ) {
                    items(selectedFiles, key = { it.id }) { file ->
                        SelectedFileRow(
                            file = file,
                            onRemove = { viewModel.onRemoveFile(file.id) },
                            onPreviewImage = { previewingFile = it }
                        )
                    }
                }
            }

            // Fixed Bottom Send Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                if (selectedFiles.isEmpty()) {
                    // Disabled button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .background(Color(0xFF191724), RoundedCornerShape(26.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                tint = Color(0xFF555268),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Send to Laptop",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = Color(0xFF555268),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                            )
                        }
                    }
                } else {
                    // Active Glowing Send Button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(
                                brush = Brush.horizontalGradient(
                                    listOf(Color(0xFFA855F7), Color(0xFF7C3AED))
                                )
                            )
                            .clickable { viewModel.onStartSend() },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Send to Laptop ($totalCount ${if (totalCount == 1) "File" else "Files"} · $totalSizeFormatted)",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            )
                        }
                    }
                }
            }
        }

        // Floating Action Button (+) with Popup Menu (Positioned bottom right, above bottom bar)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 80.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.End
            ) {
                // Popup Menu
                AnimatedVisibility(
                    visible = showPlusMenu,
                    enter = fadeIn() + scaleIn(initialScale = 0.8f),
                    exit = fadeOut() + scaleOut(targetScale = 0.8f)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .width(170.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, Color(0xFF383155), RoundedCornerShape(16.dp))
                            .background(Color(0xFF1B182B))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Gallery Option
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    showPlusMenu = false
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                    )
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Image,
                                contentDescription = null,
                                tint = Color(0xFFC084FC),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Gallery",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                            )
                        }

                        // File System Option
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    showPlusMenu = false
                                    documentPickerLauncher.launch(arrayOf("*/*"))
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Description,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "File System",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                            )
                        }
                    }
                }

                // Plus FAB
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                listOf(Color(0xFFA855F7), Color(0xFF7C3AED))
                            )
                        )
                        .clickable { showPlusMenu = !showPlusMenu },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Files",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        // Transfer Progress & State Modals
        when (val state = transferState) {
            is FileTransferState.Preparing -> {
                TransferProgressDialog(
                    title = "Preparing Transfer",
                    subtitle = "Calculating hash for ${state.fileName}...",
                    progress = 0f,
                    detail = "Starting connection...",
                    onCancel = { viewModel.onCancelTransfer() }
                )
            }

            is FileTransferState.Transferring -> {
                val detail = "${SelectedFileItem.formatBytes(state.bytesTransferred)} / ${SelectedFileItem.formatBytes(state.totalBytes)} • ${SelectedFileItem.formatBytes(state.speedBytesPerSec)}/s"
                TransferProgressDialog(
                    title = "Sending Files (${state.currentFileIndex} of ${state.totalFiles})",
                    subtitle = state.currentFileName,
                    progress = state.overallProgress,
                    detail = detail,
                    onCancel = { viewModel.onCancelTransfer() }
                )
            }

            is FileTransferState.Completed -> {
                AlertDialog(
                    onDismissRequest = { viewModel.onDismissResult() },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(44.dp)
                        )
                    },
                    title = {
                        Text(
                            text = if (state.failedFiles.isEmpty()) "Transfer Complete!" else "Transfer Finished",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${state.totalFiles} files (${SelectedFileItem.formatBytes(state.totalBytes)}) successfully transferred to your laptop.",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                            )

                            if (state.failedFiles.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "${state.failedFiles.size} files failed. They remain in your selection to retry.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color(0xFFEF4444),
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.onDismissResult() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA855F7))
                        ) {
                            Text("Done", color = Color.White)
                        }
                    },
                    containerColor = Color(0xFF1E1B2E),
                    shape = RoundedCornerShape(20.dp)
                )
            }

            is FileTransferState.Failed -> {
                AlertDialog(
                    onDismissRequest = { viewModel.onDismissResult() },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(44.dp)
                        )
                    },
                    title = {
                        Text(
                            text = "Transfer Failed",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    },
                    text = {
                        Text(
                            text = state.error,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color(0xFFCBD5E1),
                                fontSize = 13.sp
                            )
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.onDismissResult() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                        ) {
                            Text("Dismiss", color = Color.White)
                        }
                    },
                    containerColor = Color(0xFF1E1B2E),
                    shape = RoundedCornerShape(20.dp)
                )
            }

            is FileTransferState.Cancelled -> {
                AlertDialog(
                    onDismissRequest = { viewModel.onDismissResult() },
                    title = {
                        Text("Transfer Cancelled", color = Color.White, fontWeight = FontWeight.Bold)
                    },
                    text = {
                        Text("The file transfer was cancelled.", color = Color(0xFFCBD5E1))
                    },
                    confirmButton = {
                        TextButton(onClick = { viewModel.onDismissResult() }) {
                            Text("OK", color = Color(0xFFA855F7))
                        }
                    },
                    containerColor = Color(0xFF1E1B2E),
                    shape = RoundedCornerShape(20.dp)
                )
            }

            FileTransferState.Idle -> Unit
        }

        previewingFile?.let { imageFile ->
            ImagePreviewModal(
                file = imageFile,
                onDismiss = { previewingFile = null }
            )
        }
    }
}

@Composable
fun SelectedFileRow(
    file: SelectedFileItem,
    onRemove: () -> Unit,
    onPreviewImage: ((SelectedFileItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFF262238), RoundedCornerShape(16.dp))
            .background(Color(0xFF141322))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Icon Badge OR Image Thumbnail
            if (file.isImage) {
                SelectedImageThumbnail(
                    uri = file.uri,
                    fallbackCategory = file.category,
                    onClick = { onPreviewImage?.invoke(file) }
                )
            } else {
                FileCategoryBadge(category = file.category)
            }

            Spacer(modifier = Modifier.width(14.dp))

            // File Name and Size
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (file.isImage) Modifier.clickable { onPreviewImage?.invoke(file) }
                        else Modifier
                    )
            ) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = file.formattedSize,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Remove Button (X)
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .size(28.dp)
                    .background(Color(0xFF221F35), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove File",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun SelectedImageThumbnail(
    uri: Uri,
    fallbackCategory: FileCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val thumbnailBitmap by produceState<Bitmap?>(initialValue = null, key1 = uri) {
        value = withContext(Dispatchers.IO) {
            AndroidImageThumbnailLoader.getThumbnail(context, uri)
        }
    }

    Box(
        modifier = modifier
            .size(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (thumbnailBitmap != null) {
            Image(
                bitmap = thumbnailBitmap!!.asImageBitmap(),
                contentDescription = "Image Thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            FileCategoryBadge(category = fallbackCategory)
        }
    }
}

@Composable
fun FileCategoryBadge(
    category: FileCategory,
    modifier: Modifier = Modifier
) {
    when (category) {
        FileCategory.IMAGE -> {
            Box(
                modifier = modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        brush = Brush.linearGradient(
                            listOf(Color(0xFFFB923C), Color(0xFFEA580C))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Image,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        FileCategory.PDF -> {
            Box(
                modifier = modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFEF4444)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "PDF",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp
                    )
                )
            }
        }

        FileCategory.SPREADSHEET -> {
            Box(
                modifier = modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF059669)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "XLS",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp
                    )
                )
            }
        }

        FileCategory.PRESENTATION -> {
            Box(
                modifier = modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFEA580C)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "PPT",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp
                    )
                )
            }
        }

        FileCategory.VIDEO -> {
            Box(
                modifier = modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        brush = Brush.linearGradient(
                            listOf(Color(0xFF818CF8), Color(0xFF4F46E5))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        FileCategory.AUDIO -> {
            Box(
                modifier = modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF59E0B)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.MusicNote,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        FileCategory.ARCHIVE -> {
            Box(
                modifier = modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF10B981)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.FolderZip,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        FileCategory.DOCUMENT -> {
            Box(
                modifier = modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0284C7)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "DOC",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp
                    )
                )
            }
        }

        FileCategory.OTHER -> {
            Box(
                modifier = modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF475569)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.InsertDriveFile,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun ImagePreviewModal(
    file: SelectedFileItem,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val previewBitmap by produceState<Bitmap?>(initialValue = null, key1 = file.uri) {
        value = withContext(Dispatchers.IO) {
            AndroidImageThumbnailLoader.getPreviewBitmap(context, file.uri)
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF5090810))
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0xFF1E1B2E), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp)
                    ) {
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${file.formattedSize}${if (previewBitmap != null) " • ${previewBitmap!!.width} × ${previewBitmap!!.height}" else ""}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFFC084FC),
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                // Image Container
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0F0E1A))
                        .border(1.dp, Color(0xFF2E2B44), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (previewBitmap != null) {
                        Image(
                            bitmap = previewBitmap!!.asImageBitmap(),
                            contentDescription = file.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFFA855F7),
                                modifier = Modifier.size(36.dp),
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Loading preview...",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Dismiss Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1B2E)),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("Close Preview", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

object AndroidImageThumbnailLoader {
    private val thumbnailCache = LruCache<String, Bitmap>(64)

    fun getThumbnail(context: Context, uri: Uri): Bitmap? {
        val key = uri.toString()
        thumbnailCache.get(key)?.let { return it }

        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            val maxDim = maxOf(options.outWidth, options.outHeight)
            if (maxDim <= 0) return null

            var sampleSize = 1
            while (maxDim / (sampleSize * 2) >= 100) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            }
            if (bitmap != null) {
                thumbnailCache.put(key, bitmap)
            }
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    fun getPreviewBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            val maxDim = maxOf(options.outWidth, options.outHeight)
            if (maxDim <= 0) return null

            var sampleSize = 1
            while (maxDim / (sampleSize * 2) >= 1200) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            }
        } catch (_: Exception) {
            null
        }
    }
}

@Composable
fun TransferProgressDialog(
    title: String,
    subtitle: String,
    progress: Float,
    detail: String,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Prevent dismiss by touch outside */ },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFFE2E8F0),
                        fontSize = 13.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(14.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFFA855F7),
                    trackColor = Color(0xFF28253B),
                    strokeCap = StrokeCap.Round
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFFC084FC),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = onCancel,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
            ) {
                Text("Cancel Transfer")
            }
        },
        containerColor = Color(0xFF1E1B2E),
        shape = RoundedCornerShape(20.dp)
    )
}
