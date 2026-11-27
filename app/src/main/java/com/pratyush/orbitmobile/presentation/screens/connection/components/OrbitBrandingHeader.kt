package com.pratyush.orbitmobile.presentation.screens.connection.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AllInclusive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.pratyush.orbitmobile.core.designsystem.theme.OrbitColors

/**
 * Brand header showcasing ORBIT identity, title, and clear companion description.
 */
@Composable
fun OrbitBrandingHeader(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Glowing Orbital Emblem
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(
                    brush = Brush.radialGradient(
                        listOf(OrbitColors.PurpleDarkContainer, OrbitColors.PitchBlack)
                    ),
                    shape = CircleShape
                )
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        listOf(OrbitColors.PurpleAccentGlow, OrbitColors.PurplePrimary)
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.AllInclusive,
                contentDescription = "ORBIT Emblem",
                tint = OrbitColors.PurpleAccentGlow,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Connect to your PC",
            style = MaterialTheme.typography.headlineMedium,
            color = OrbitColors.TextPrimary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Pair with ORBIT Windows to control your laptop, synchronize clipboard, and access remote tools.",
            style = MaterialTheme.typography.bodyMedium,
            color = OrbitColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}
