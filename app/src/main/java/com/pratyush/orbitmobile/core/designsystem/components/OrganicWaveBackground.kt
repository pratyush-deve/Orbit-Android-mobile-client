package com.pratyush.orbitmobile.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

enum class WaveType {
    TOP_RIGHT,
    BOTTOM_RIGHT,
    HEADER_DOUBLE_WAVE
}

/**
 * Draws the smooth, organic fluid wave decoration inside cards.
 */
@Composable
fun OrganicWaveBackground(
    waveColor: Color,
    waveType: WaveType = WaveType.TOP_RIGHT,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        when (waveType) {
            WaveType.TOP_RIGHT -> {
                // Smooth organic fluid blob in the top-right corner
                val path = Path().apply {
                    moveTo(width * 0.40f, 0f)
                    cubicTo(
                        width * 0.55f, height * 0.35f,
                        width * 0.70f, height * 0.55f,
                        width, height * 0.48f
                    )
                    lineTo(width, 0f)
                    close()
                }
                drawPath(path = path, color = waveColor)
            }
            WaveType.BOTTOM_RIGHT -> {
                // Smooth organic wave in the bottom-right corner (for Clipboard card)
                val path = Path().apply {
                    moveTo(width, height * 0.35f)
                    cubicTo(
                        width * 0.75f, height * 0.45f,
                        width * 0.50f, height * 0.75f,
                        width * 0.60f, height
                    )
                    lineTo(width, height)
                    close()
                }
                drawPath(path = path, color = waveColor)
            }
            WaveType.HEADER_DOUBLE_WAVE -> {
                // Layer 1 (lighter inner wave)
                val innerPath = Path().apply {
                    moveTo(width * 0.70f, height)
                    cubicTo(
                        width * 0.78f, height * 0.60f,
                        width * 0.88f, height * 0.55f,
                        width, height * 0.40f
                    )
                    lineTo(width, height)
                    close()
                }
                drawPath(path = innerPath, color = waveColor.copy(alpha = 0.5f))

                // Layer 2 (front wave)
                val frontPath = Path().apply {
                    moveTo(width * 0.80f, height)
                    cubicTo(
                        width * 0.86f, height * 0.75f,
                        width * 0.92f, height * 0.70f,
                        width, height * 0.55f
                    )
                    lineTo(width, height)
                    close()
                }
                drawPath(path = frontPath, color = waveColor)
            }
        }
    }
}
