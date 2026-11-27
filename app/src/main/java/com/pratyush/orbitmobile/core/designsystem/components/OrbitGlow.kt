package com.pratyush.orbitmobile.core.designsystem.components

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pratyush.orbitmobile.core.designsystem.theme.OrbitColors

/**
 * Modifier extension applying a subtle purple glow or gradient border.
 */
fun Modifier.orbitGlowBorder(
    width: Dp = 1.dp,
    shape: Shape = RoundedCornerShape(16.dp),
    glowColor: Color = OrbitColors.PurpleAccentGlow.copy(alpha = 0.45f)
): Modifier = composed {
    this.border(
        width = width,
        brush = Brush.linearGradient(
            listOf(
                glowColor,
                OrbitColors.BorderGlowing.copy(alpha = 0.25f),
                Color.Transparent
            )
        ),
        shape = shape
    )
}
