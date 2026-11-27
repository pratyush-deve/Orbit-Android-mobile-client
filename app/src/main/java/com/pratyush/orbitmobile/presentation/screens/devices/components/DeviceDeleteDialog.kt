package com.pratyush.orbitmobile.presentation.screens.devices.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.pratyush.orbitmobile.core.designsystem.theme.OrbitColors
import com.pratyush.orbitmobile.domain.model.Device

/**
 * Confirmation dialog before removing a paired laptop.
 */
@Composable
fun DeviceDeleteDialog(
    device: Device,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Remove Paired PC?",
                style = MaterialTheme.typography.titleMedium,
                color = OrbitColors.TextPrimary
            )
        },
        text = {
            Text(
                text = "Are you sure you want to remove \"${device.name}\"? You will need to scan its QR code again to reconnect.",
                style = MaterialTheme.typography.bodyMedium,
                color = OrbitColors.TextSecondary
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = OrbitColors.StatusOffline
                )
            ) {
                Text("Remove")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = OrbitColors.TextSecondary)
            }
        },
        containerColor = OrbitColors.SurfaceCardElevated
    )
}
