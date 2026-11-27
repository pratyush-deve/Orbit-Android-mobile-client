package com.pratyush.orbitmobile.presentation.screens.systemmonitor.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import kotlin.math.exp
import kotlin.math.floor

/**
 * High-performance, continuously fluid live hardware graph.
 *
 * Implements:
 * 1. Smooth horizontal scrolling decoupled from discrete telemetry arrival ticks.
 * 2. Catmull-Rom cubic Bezier splines with de Casteljau endpoint clipping to prevent snapping.
 * 3. Exponential tracking that gracefully absorbs network polling jitter.
 * 4. Stale/offline settling to the last valid sample before freezing.
 * 5. Cohesive moving vertical guide bars and glowing beacon indicators.
 * 6. Dual-line rendering for Wi-Fi download and upload.
 */
@Composable
fun LiveHardwareGraph(
    history: List<Float>,
    primaryColor: Color,
    modifier: Modifier = Modifier,
    secondaryHistory: List<Float>? = null,
    secondaryColor: Color? = null,
    isLive: Boolean = true,
    minValue: Float = 0f,
    maxValue: Float = 100f
) {
    // Pulse animation for beacon dot when live
    val infiniteTransition = rememberInfiniteTransition(label = "graphLivePulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.40f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Internal state managing continuous timeline and sample buffer
    val graphState = remember { SmoothGraphState() }

    // Sync incoming telemetry samples
    LaunchedEffect(history, secondaryHistory) {
        graphState.onTelemetryUpdate(history, secondaryHistory)
    }

    // High-performance display frame loop (60 / 120 FPS)
    // Updates internal headPosition and invalidates ONLY the Canvas draw phase
    val drawTrigger = remember { mutableLongStateOf(0L) }
    LaunchedEffect(isLive) {
        var lastFrameTimeNanos = 0L
        while (true) {
            withFrameNanos { frameTimeNanos ->
                if (lastFrameTimeNanos != 0L) {
                    val dt = ((frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f).coerceIn(0.001f, 0.1f)
                    val active = graphState.advanceFrame(dt, isLive)
                    if (active || isLive) {
                        drawTrigger.longValue = frameTimeNanos
                    }
                }
                lastFrameTimeNanos = frameTimeNanos
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        // Read drawTrigger inside Canvas draw scope to subscribe to frame updates without recomposing
        @Suppress("UNUSED_VARIABLE")
        val trigger = drawTrigger.longValue

        val width = size.width
        val height = size.height
        if (width <= 0 || height <= 0) return@Canvas

        val head = graphState.headPosition
        val primarySamples = graphState.primarySamples
        val secondarySamples = graphState.secondarySamples

        if (primarySamples.isEmpty()) return@Canvas

        clipRect(left = 0f, top = 0f, right = width, bottom = height) {
            // 1. Draw Secondary Curve (e.g. Wi-Fi Upload) if present
            if (secondarySamples.isNotEmpty() && secondaryColor != null) {
                drawFluidSpline(
                    samples = secondarySamples,
                    head = head,
                    color = secondaryColor,
                    width = width,
                    height = height,
                    minValue = minValue,
                    maxValue = maxValue,
                    isLive = isLive,
                    pulseAlpha = pulseAlpha,
                    drawFaintBars = false
                )
            }

            // 2. Draw Primary Curve (CPU, RAM, GPU, or Wi-Fi Download)
            drawFluidSpline(
                samples = primarySamples,
                head = head,
                color = primaryColor,
                width = width,
                height = height,
                minValue = minValue,
                maxValue = maxValue,
                isLive = isLive,
                pulseAlpha = pulseAlpha,
                drawFaintBars = true
            )
        }
    }
}

/**
 * State holder that buffers samples and continuously interpolates the horizontal scroll head.
 */
private class SmoothGraphState {
    val primarySamples = ArrayList<Float>(50)
    val secondarySamples = ArrayList<Float>(50)

    var headPosition by mutableFloatStateOf(0f)
    var targetPosition = 0f
    var isSettled = false

    fun onTelemetryUpdate(history: List<Float>, secondaryHistory: List<Float>?) {
        if (history.isEmpty()) return

        if (primarySamples.isEmpty()) {
            primarySamples.addAll(history)
            if (!secondaryHistory.isNullOrEmpty()) {
                secondarySamples.addAll(secondaryHistory)
            }
            headPosition = maxOf(0f, (primarySamples.size - 1).toFloat())
            targetPosition = headPosition
            isSettled = true
            return
        }

        // Append the newest arriving sample
        primarySamples.add(history.last())
        if (!secondaryHistory.isNullOrEmpty()) {
            secondarySamples.add(secondaryHistory.last())
        }

        targetPosition = (primarySamples.size - 1).toFloat()
        isSettled = false

        // Keep buffer bounded (50 samples) while maintaining exact mathematical pixel continuity
        val maxBufferSize = 45
        while (primarySamples.size > maxBufferSize) {
            primarySamples.removeAt(0)
            if (secondarySamples.isNotEmpty()) {
                secondarySamples.removeAt(0)
            }
            headPosition = maxOf(0f, headPosition - 1f)
            targetPosition = maxOf(0f, targetPosition - 1f)
        }
    }

    /**
     * Advances the head position using exponential pursuit of target.
     * Returns true if head position was updated, false if completely settled.
     */
    fun advanceFrame(dt: Float, isLive: Boolean): Boolean {
        val diff = targetPosition - headPosition
        if (kotlin.math.abs(diff) > 0.0005f) {
            // Adaptive pursuit speed: ~2.2/sec provides gentle trailing effect (~0.8s lag)
            val pursuitSpeed = 2.2f
            val factor = 1f - exp(-pursuitSpeed * dt)
            headPosition += diff * factor
            isSettled = false
            return true
        } else {
            if (!isSettled) {
                headPosition = targetPosition
                isSettled = true
                return true
            }
            return false
        }
    }
}

/**
 * Renders a Catmull-Rom cubic Bezier curve that continuously scrolls from right to left,
 * with de Casteljau subdivision at the right edge so the curve smoothly meets the beacon point.
 */
private fun DrawScope.drawFluidSpline(
    samples: List<Float>,
    head: Float,
    color: Color,
    width: Float,
    height: Float,
    minValue: Float,
    maxValue: Float,
    isLive: Boolean,
    pulseAlpha: Float,
    drawFaintBars: Boolean
) {
    val count = samples.size
    if (count == 0) return

    val topPadding = height * 0.10f
    val bottomPadding = height * 0.12f
    val availableHeight = height - topPadding - bottomPadding
    val range = maxOf(1f, maxValue - minValue)

    // Number of historical intervals visible across the card width
    val visibleSlots = 20f
    val stepX = width / visibleSlots

    fun getSampleVal(index: Int): Float {
        return when {
            index < 0 -> samples.first()
            index >= count -> samples.last()
            else -> samples[index]
        }
    }

    fun calcY(value: Float): Float {
        val normalized = ((value - minValue) / range).coerceIn(0f, 1f)
        return (height - bottomPadding - (normalized * availableHeight)).coerceIn(topPadding, height - bottomPadding)
    }

    fun getPoint(index: Int): Offset {
        val x = width - (head - index) * stepX
        val y = calcY(getSampleVal(index))
        return Offset(x, y)
    }

    val kMax = floor(head).toInt()
    val kMin = floor(head - visibleSlots).toInt() - 1

    val splinePath = Path()
    var pathStarted = false

    // Build the segments that are fully behind the right edge
    for (j in kMin until kMax) {
        val p0 = getPoint(j - 1)
        val p1 = getPoint(j)
        val p2 = getPoint(j + 1)
        val p3 = getPoint(j + 2)

        val cp1x = p1.x + (p2.x - p0.x) / 6f
        val cp1y = (p1.y + (p2.y - p0.y) / 6f).coerceIn(topPadding, height - bottomPadding)
        val cp2x = p2.x - (p3.x - p1.x) / 6f
        val cp2y = (p2.y - (p3.y - p1.y) / 6f).coerceIn(topPadding, height - bottomPadding)

        if (!pathStarted) {
            splinePath.moveTo(p1.x, p1.y)
            pathStarted = true
        }

        splinePath.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
    }

    // Final segment crossing into the right edge (x = width)
    val tau = (head - kMax).coerceIn(0f, 1f)
    var latestPoint = Offset(width, calcY(getSampleVal(kMax)))

    val p0 = getPoint(kMax - 1)
    val p1 = getPoint(kMax)
    val p2 = getPoint(kMax + 1)
    val p3 = getPoint(kMax + 2)

    val cp1 = Offset(
        p1.x + (p2.x - p0.x) / 6f,
        (p1.y + (p2.y - p0.y) / 6f).coerceIn(topPadding, height - bottomPadding)
    )
    val cp2 = Offset(
        p2.x - (p3.x - p1.x) / 6f,
        (p2.y - (p3.y - p1.y) / 6f).coerceIn(topPadding, height - bottomPadding)
    )

    if (tau > 0.0001f) {
        // de Casteljau's algorithm to cut the cubic Bezier curve at tau
        val q0 = lerpOffset(p1, cp1, tau)
        val q1 = lerpOffset(cp1, cp2, tau)
        val q2 = lerpOffset(cp2, p2, tau)
        val r0 = lerpOffset(q0, q1, tau)
        val r1 = lerpOffset(q1, q2, tau)
        val sPoint = lerpOffset(r0, r1, tau)

        if (!pathStarted) {
            splinePath.moveTo(p1.x, p1.y)
            pathStarted = true
        }

        splinePath.cubicTo(
            q0.x, q0.y,
            r0.x, r0.y,
            width, sPoint.y
        )
        latestPoint = Offset(width, sPoint.y)
    } else {
        if (!pathStarted) {
            splinePath.moveTo(p1.x, p1.y)
        }
        latestPoint = Offset(width, p1.y)
    }

    // 1. Subtle gradient fill below curve
    val fillPath = Path().apply {
        addPath(splinePath)
        lineTo(width, height)
        lineTo(0f, height)
        close()
    }

    drawPath(
        path = fillPath,
        brush = Brush.verticalGradient(
            colors = listOf(
                color.copy(alpha = if (isLive) 0.32f else 0.12f),
                color.copy(alpha = if (isLive) 0.08f else 0.03f),
                Color.Transparent
            ),
            startY = topPadding,
            endY = height
        )
    )

    // 2. Faint vertical guide bars under the graph moving cohesively with the wave
    if (drawFaintBars && isLive) {
        val barSpacing = 16.dp.toPx()
        val scrollOffset = (head * stepX) % barSpacing
        var barX = width - scrollOffset
        val barColor = color.copy(alpha = 0.15f)

        while (barX >= 0f) {
            if (barX <= width) {
                val u = head - (width - barX) / stepX
                val sampleIdx = floor(u).toInt()
                val sampleTau = (u - sampleIdx).coerceIn(0f, 1f)

                val bp0 = getPoint(sampleIdx - 1)
                val bp1 = getPoint(sampleIdx)
                val bp2 = getPoint(sampleIdx + 1)
                val bp3 = getPoint(sampleIdx + 2)

                val bcp1y = (bp1.y + (bp2.y - bp0.y) / 6f).coerceIn(topPadding, height - bottomPadding)
                val bcp2y = (bp2.y - (bp3.y - bp1.y) / 6f).coerceIn(topPadding, height - bottomPadding)

                val targetY = evalBezierY(bp1.y, bcp1y, bcp2y, bp2.y, sampleTau)

                drawLine(
                    color = barColor,
                    start = Offset(barX, targetY),
                    end = Offset(barX, height - 4.dp.toPx()),
                    strokeWidth = 1.dp.toPx()
                )
            }
            barX -= barSpacing
        }
    }

    // 3. Soft glow around the main curve
    drawPath(
        path = splinePath,
        color = color.copy(alpha = if (isLive) 0.35f else 0.15f),
        style = Stroke(
            width = 5.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )

    // 4. Main crisp stroke
    drawPath(
        path = splinePath,
        color = color.copy(alpha = if (isLive) 0.95f else 0.50f),
        style = Stroke(
            width = 2.2.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )

    // 5. Illuminated marker at latest data point
    val haloRadius = if (isLive) 6.5.dp.toPx() else 4.dp.toPx()
    val dotRadius = if (isLive) 3.2.dp.toPx() else 2.5.dp.toPx()

    // Outer glowing halo
    drawCircle(
        color = color.copy(alpha = if (isLive) pulseAlpha else 0.25f),
        radius = haloRadius,
        center = latestPoint
    )

    // Bright core dot
    drawCircle(
        color = Color.White,
        radius = dotRadius,
        center = latestPoint
    )
}

private fun lerpOffset(start: Offset, end: Offset, fraction: Float): Offset {
    return Offset(
        start.x + (end.x - start.x) * fraction,
        start.y + (end.y - start.y) * fraction
    )
}

private fun evalBezierY(p1y: Float, cp1y: Float, cp2y: Float, p2y: Float, tau: Float): Float {
    val oneMinusTau = 1f - tau
    return (oneMinusTau * oneMinusTau * oneMinusTau * p1y) +
            (3f * oneMinusTau * oneMinusTau * tau * cp1y) +
            (3f * oneMinusTau * tau * tau * cp2y) +
            (tau * tau * tau * p2y)
}
