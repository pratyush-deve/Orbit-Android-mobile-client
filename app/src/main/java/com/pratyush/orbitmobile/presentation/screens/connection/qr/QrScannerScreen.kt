package com.pratyush.orbitmobile.presentation.screens.connection.qr

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pratyush.orbitmobile.core.designsystem.components.OrbitButton
import com.pratyush.orbitmobile.core.designsystem.theme.OrbitColors

/**
 * Dedicated QR scanning screen component.
 * Features live CameraX preview, permission handling, animated glowing viewfinder,
 * and pairing confirmation callbacks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    onQrScanned: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToManual: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var isTorchOn by remember { mutableStateOf(false) }
    var cameraControlRef by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }
    val analyzerRef = remember {
        QrCodeAnalyzer { qrText ->
            onQrScanned(qrText)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = OrbitColors.PitchBlack,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Scan ORBIT QR Code",
                        style = MaterialTheme.typography.titleMedium,
                        color = OrbitColors.TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = OrbitColors.TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isTorchOn = !isTorchOn
                        cameraControlRef?.enableTorch(isTorchOn)
                    }) {
                        Icon(
                            imageVector = Icons.Default.FlashOn,
                            contentDescription = "Flash",
                            tint = if (isTorchOn) Color(0xFFFACC15) else OrbitColors.TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = OrbitColors.PitchBlack
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (hasCameraPermission) {
                // Live CameraX Preview with HD 720p Resolution and Continuous Autofocus
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()

                            val resolutionSelector = androidx.camera.core.resolutionselector.ResolutionSelector.Builder()
                                .setResolutionStrategy(
                                    androidx.camera.core.resolutionselector.ResolutionStrategy(
                                        android.util.Size(1280, 720),
                                        androidx.camera.core.resolutionselector.ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                    )
                                )
                                .build()

                            val preview = Preview.Builder()
                                .setResolutionSelector(resolutionSelector)
                                .build()
                                .also {
                                    it.surfaceProvider = previewView.surfaceProvider
                                }

                            val imageAnalysis = androidx.camera.core.ImageAnalysis.Builder()
                                .setResolutionSelector(resolutionSelector)
                                .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also {
                                    it.setAnalyzer(
                                        ContextCompat.getMainExecutor(ctx),
                                        analyzerRef
                                    )
                                }

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                            try {
                                cameraProvider.unbindAll()
                                val camera = cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageAnalysis
                                )
                                cameraControlRef = camera.cameraControl

                                // Continuous center autofocus & auto-exposure metering
                                val factory = androidx.camera.core.SurfaceOrientedMeteringPointFactory(1f, 1f)
                                val centerPoint = factory.createPoint(0.5f, 0.42f)
                                val focusAction = androidx.camera.core.FocusMeteringAction.Builder(
                                    centerPoint,
                                    androidx.camera.core.FocusMeteringAction.FLAG_AF or androidx.camera.core.FocusMeteringAction.FLAG_AE
                                ).setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS).build()
                                camera.cameraControl.startFocusAndMetering(focusAction)

                                // Tap to focus on touch position
                                previewView.setOnTouchListener { v, event ->
                                    if (event.action == android.view.MotionEvent.ACTION_UP) {
                                        val tapPoint = factory.createPoint(event.x / v.width, event.y / v.height)
                                        val tapAction = androidx.camera.core.FocusMeteringAction.Builder(
                                            tapPoint,
                                            androidx.camera.core.FocusMeteringAction.FLAG_AF or androidx.camera.core.FocusMeteringAction.FLAG_AE
                                        ).setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS).build()
                                        camera.cameraControl.startFocusAndMetering(tapAction)
                                    }
                                    true
                                }
                            } catch (e: Exception) {
                                // Handled gracefully if camera hardware is unavailable
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Viewfinder Mask with Animated Scan Laser
                QrViewfinderOverlay(
                    modifier = Modifier.fillMaxSize()
                )

                // Scanning Guidance Overlay
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(28.dp))

                    // Primary Guidance Pill
                    Box(
                        modifier = Modifier
                            .background(
                                color = OrbitColors.PitchBlack.copy(alpha = 0.75f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = OrbitColors.PurpleAccentGlow.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Align the QR code within the frame",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OrbitColors.TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Secondary Proximity Guidance Pill
                    Box(
                        modifier = Modifier
                            .padding(bottom = 120.dp)
                            .background(
                                color = OrbitColors.PitchBlack.copy(alpha = 0.70f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Move closer if the QR appears blurry",
                            style = MaterialTheme.typography.bodySmall,
                            color = OrbitColors.TextSecondary
                        )
                    }
                }
            } else {
                // Permission Request Fallback View
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CameraAlt,
                        contentDescription = null,
                        tint = OrbitColors.PurpleAccentGlow,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Camera Permission Required",
                        style = MaterialTheme.typography.titleLarge,
                        color = OrbitColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "ORBIT Mobile requires camera access to scan the pairing QR code displayed on your ORBIT Windows PC.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OrbitColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    OrbitButton(
                        text = "Grant Camera Permission",
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                    )
                }
            }

            // Bottom Floating Controls
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(Color.Transparent, OrbitColors.PitchBlack.copy(alpha = 0.95f))
                        )
                    )
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Point camera at the QR code in ORBIT Windows",
                    style = MaterialTheme.typography.bodySmall,
                    color = OrbitColors.TextSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(onClick = onNavigateToManual) {
                    Text(
                        text = "Enter IP Address Manually",
                        style = MaterialTheme.typography.labelMedium,
                        color = OrbitColors.PurpleAccentGlow
                    )
                }
            }
        }
    }
}

/**
 * Custom viewfinder overlay with glowing corners and an animated laser line.
 */
@Composable
private fun QrViewfinderOverlay(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "LaserTransition")
    val laserPosition by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LaserScan"
    )

    Canvas(modifier = modifier) {
        val boxSize = size.minDimension * 0.70f
        val left = (size.width - boxSize) / 2f
        val top = (size.height - boxSize) / 2.6f
        val right = left + boxSize
        val bottom = top + boxSize

        // Dim background
        drawRect(color = Color.Black.copy(alpha = 0.65f))

        // Clear square cutout
        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(left, top),
            size = Size(boxSize, boxSize),
            cornerRadius = CornerRadius(20.dp.toPx()),
            blendMode = BlendMode.Clear
        )

        // Corner reticles
        val cornerLength = 32.dp.toPx()
        val cornerStroke = 4.dp.toPx()
        val purpleGlow = Color(0xFFC084FC)

        // Top-Left
        drawLine(purpleGlow, Offset(left, top + cornerLength), Offset(left, top), cornerStroke)
        drawLine(purpleGlow, Offset(left, top), Offset(left + cornerLength, top), cornerStroke)

        // Top-Right
        drawLine(purpleGlow, Offset(right - cornerLength, top), Offset(right, top), cornerStroke)
        drawLine(purpleGlow, Offset(right, top), Offset(right, top + cornerLength), cornerStroke)

        // Bottom-Left
        drawLine(purpleGlow, Offset(left, bottom - cornerLength), Offset(left, bottom), cornerStroke)
        drawLine(purpleGlow, Offset(left, bottom), Offset(left + cornerLength, bottom), cornerStroke)

        // Bottom-Right
        drawLine(purpleGlow, Offset(right - cornerLength, bottom), Offset(right, bottom), cornerStroke)
        drawLine(purpleGlow, Offset(right, bottom), Offset(right, bottom - cornerLength), cornerStroke)

        // Animated Laser Scan Line
        val currentLaserY = top + (boxSize * laserPosition)
        drawLine(
            brush = Brush.horizontalGradient(
                listOf(
                    Color.Transparent,
                    purpleGlow.copy(alpha = 0.9f),
                    Color(0xFF8B5CF6),
                    purpleGlow.copy(alpha = 0.9f),
                    Color.Transparent
                )
            ),
            start = Offset(left + 8.dp.toPx(), currentLaserY),
            end = Offset(right - 8.dp.toPx(), currentLaserY),
            strokeWidth = 3.dp.toPx()
        )
    }
}
