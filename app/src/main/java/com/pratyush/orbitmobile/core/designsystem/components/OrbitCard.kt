package com.pratyush.orbitmobile.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.pratyush.orbitmobile.core.designsystem.theme.OrbitColors
import com.pratyush.orbitmobile.core.designsystem.theme.OrbitShapes

/**
 * Reusable dark surface card with subtle purple/subtle outline border.
 */
@Composable
fun OrbitCard(
    modifier: Modifier = Modifier,
    shape: Shape = OrbitShapes.medium,
    hasGlowBorder: Boolean = false,
    glowColor: androidx.compose.ui.graphics.Color = OrbitColors.PurpleAccentGlow.copy(alpha = 0.45f),
    borderColor: androidx.compose.ui.graphics.Color = OrbitColors.BorderSubtle,
    contentPadding: androidx.compose.ui.unit.Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = if (hasGlowBorder) modifier.orbitGlowBorder(shape = shape, glowColor = glowColor) else modifier,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = OrbitColors.SurfaceCard,
            contentColor = OrbitColors.TextPrimary
        ),
        border = if (!hasGlowBorder) BorderStroke(1.dp, borderColor) else null
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}
