package com.pratyush.orbitmobile.presentation.screens.connection.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pratyush.orbitmobile.core.designsystem.components.OrbitButton
import com.pratyush.orbitmobile.core.designsystem.components.OrbitCard
import com.pratyush.orbitmobile.core.designsystem.theme.OrbitColors
import com.pratyush.orbitmobile.core.designsystem.theme.OrbitShapes

/**
 * Prominent primary pairing card for scanning the ORBIT Windows desktop QR code.
 */
@Composable
fun ScanQrActionCard(
    onScanQrClicked: () -> Unit,
    modifier: Modifier = Modifier,
    isConnecting: Boolean = false,
    errorMessage: String? = null
) {
    OrbitCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = !isConnecting) { onScanQrClicked() },
        hasGlowBorder = true
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.QrCodeScanner,
                contentDescription = null,
                tint = OrbitColors.PurpleAccentGlow,
                modifier = Modifier.size(44.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Pair via QR Code",
                    style = MaterialTheme.typography.titleLarge,
                    color = OrbitColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isConnecting) {
                        "Connecting to ORBIT Windows... Please approve pairing on your PC."
                    } else {
                        "Scan the QR code shown in ORBIT on your PC"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isConnecting) OrbitColors.PurpleSecondary else OrbitColors.TextSecondary
                )
            }
        }

        if (!errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = OrbitColors.StatusOffline.copy(alpha = 0.12f),
                        shape = OrbitShapes.small
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = OrbitColors.StatusOffline,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = OrbitColors.StatusOffline,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OrbitButton(
            text = if (isConnecting) "CONNECTING TO PC..." else "SCAN QR CODE",
            onClick = onScanQrClicked,
            isLoading = isConnecting,
            enabled = !isConnecting
        )
    }
}
