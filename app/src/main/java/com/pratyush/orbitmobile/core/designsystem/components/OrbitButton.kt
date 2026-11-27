package com.pratyush.orbitmobile.core.designsystem.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pratyush.orbitmobile.core.designsystem.theme.OrbitColors
import com.pratyush.orbitmobile.core.designsystem.theme.OrbitShapes

/**
 * Primary action button styled with purple brand color and loading state.
 */
@Composable
fun OrbitButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    isSecondary: Boolean = false
) {
    if (isSecondary) {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .height(48.dp),
            enabled = enabled && !isLoading,
            shape = OrbitShapes.small,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = OrbitColors.PurpleSecondary
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = OrbitColors.PurpleSecondary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    } else {
        Button(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .height(48.dp),
            enabled = enabled && !isLoading,
            shape = OrbitShapes.small,
            colors = ButtonDefaults.buttonColors(
                containerColor = OrbitColors.PurplePrimary,
                contentColor = Color.White,
                disabledContainerColor = OrbitColors.SurfaceCardElevated,
                disabledContentColor = OrbitColors.TextTertiary
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
