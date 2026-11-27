package com.pratyush.orbitmobile.presentation.screens.connection.qr

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CameraX ImageAnalysis analyzer optimized for fast, reliable QR code detection using ZXing.
 * Handles row-stride padding, camera sensor rotation, TRY_HARDER hints, and dual binarizers.
 */
class QrCodeAnalyzer(
    private val onQrCodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.CHARACTER_SET to "UTF-8"
        )
        setHints(hints)
    }

    private val hasScanned = AtomicBoolean(false)

    fun reset() {
        hasScanned.set(false)
    }

    override fun analyze(image: ImageProxy) {
        if (hasScanned.get()) {
            image.close()
            return
        }

        try {
            val yPlane = image.planes[0]
            val yBuffer: ByteBuffer = yPlane.buffer
            val rowStride = yPlane.rowStride
            val width = image.width
            val height = image.height

            // Extract Y-plane data properly respecting rowStride (avoids horizontal shearing)
            val yBytes = ByteArray(width * height)
            if (rowStride == width) {
                yBuffer.get(yBytes)
            } else {
                for (row in 0 until height) {
                    yBuffer.position(row * rowStride)
                    yBuffer.get(yBytes, row * width, width)
                }
            }

            // Align pixel buffer to upright portrait orientation according to sensor rotation
            val rotationDegrees = image.imageInfo.rotationDegrees
            val rotatedBytes = when (rotationDegrees) {
                90 -> rotateYUV90(yBytes, width, height)
                180 -> rotateYUV180(yBytes, width, height)
                270 -> rotateYUV270(yBytes, width, height)
                else -> yBytes
            }

            val (finalWidth, finalHeight) = if (rotationDegrees == 90 || rotationDegrees == 270) {
                height to width
            } else {
                width to height
            }

            val source = PlanarYUVLuminanceSource(
                rotatedBytes,
                finalWidth,
                finalHeight,
                0,
                0,
                finalWidth,
                finalHeight,
                false
            )

            // Primary attempt: HybridBinarizer (adaptive thresholding for typical lighting)
            var text: String? = null
            try {
                val bitmap = BinaryBitmap(HybridBinarizer(source))
                val result = reader.decodeWithState(bitmap)
                text = result?.text
            } catch (e: NotFoundException) {
                // Secondary attempt: GlobalHistogramBinarizer (better for high-contrast B&W screens)
                try {
                    val fallbackBitmap = BinaryBitmap(GlobalHistogramBinarizer(source))
                    val fallbackResult = reader.decodeWithState(fallbackBitmap)
                    text = fallbackResult?.text
                } catch (e2: Exception) {
                    // Not detected in this frame
                }
            } catch (e: Exception) {
                // Frame decode error ignored
            } finally {
                reader.reset()
            }

            if (!text.isNullOrBlank()) {
                if (hasScanned.compareAndSet(false, true)) {
                    onQrCodeScanned(text)
                }
            }
        } finally {
            image.close()
        }
    }

    private fun rotateYUV90(data: ByteArray, width: Int, height: Int): ByteArray {
        val rotated = ByteArray(data.size)
        var i = 0
        for (x in 0 until width) {
            for (y in height - 1 downTo 0) {
                rotated[i++] = data[y * width + x]
            }
        }
        return rotated
    }

    private fun rotateYUV180(data: ByteArray, width: Int, height: Int): ByteArray {
        val rotated = ByteArray(data.size)
        val size = width * height
        for (i in 0 until size) {
            rotated[size - 1 - i] = data[i]
        }
        return rotated
    }

    private fun rotateYUV270(data: ByteArray, width: Int, height: Int): ByteArray {
        val rotated = ByteArray(data.size)
        var i = 0
        for (x in width - 1 downTo 0) {
            for (y in 0 until height) {
                rotated[i++] = data[y * width + x]
            }
        }
        return rotated
    }
}
