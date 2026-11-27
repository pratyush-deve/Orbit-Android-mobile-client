package com.pratyush.orbitmobile.presentation.screens.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pratyush.orbitmobile.core.designsystem.components.OrbitCard
import com.pratyush.orbitmobile.core.designsystem.theme.OrbitColors
import com.pratyush.orbitmobile.core.designsystem.theme.OrbitShapes

/**
 * Warning banner displayed when the desktop connection is dropped during an active session.
 */
@Composable
fun DisconnectedWarningBanner(
    onReconnect: () -> Unit,
    isReconnecting: Boolean = false,
    modifier: Modifier = Modifier
) {
    OrbitCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = OrbitColors.StatusConnecting,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Connection Lost",
                        style = MaterialTheme.typography.titleSmall,
                        color = OrbitColors.StatusConnecting
                    )
                    Text(
                        text = "Laptop unreachable over local Wi-Fi",
                        style = MaterialTheme.typography.bodySmall,
                        color = OrbitColors.TextSecondary
                    )
                }
            }

            FilledTonalButton(
                onClick = onReconnect,
                enabled = !isReconnecting,
                shape = OrbitShapes.small,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = OrbitColors.PurpleDarkContainer,
                    contentColor = OrbitColors.PurpleAccentGlow
                )
            ) {
                if (isReconnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = OrbitColors.PurpleAccentGlow
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Reconnecting...", style = MaterialTheme.typography.labelMedium)
                } else {
                    Text(text = "Reconnect", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
