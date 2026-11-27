package com.pratyush.orbitmobile.presentation.screens.connection.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pratyush.orbitmobile.core.designsystem.components.OrbitButton
import com.pratyush.orbitmobile.core.designsystem.components.OrbitCard
import com.pratyush.orbitmobile.core.designsystem.theme.OrbitColors

/**
 * Card for entering host IP and port manually.
 */
@Composable
fun ConnectionManualInputCard(
    hostInput: String,
    portInput: String,
    isConnected: Boolean,
    isConnecting: Boolean,
    errorMessage: String?,
    onHostChanged: (String) -> Unit,
    onPortChanged: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    OrbitCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Manual Configuration",
            style = MaterialTheme.typography.titleMedium,
            color = OrbitColors.TextPrimary
        )

        OutlinedTextField(
            value = hostInput,
            onValueChange = onHostChanged,
            label = { Text("Desktop IP Address") },
            placeholder = { Text("e.g. 192.168.1.100") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = OrbitColors.PurplePrimary,
                unfocusedBorderColor = OrbitColors.BorderSubtle,
                focusedTextColor = OrbitColors.TextPrimary,
                unfocusedTextColor = OrbitColors.TextPrimary
            )
        )

        OutlinedTextField(
            value = portInput,
            onValueChange = onPortChanged,
            label = { Text("Port") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = OrbitColors.PurplePrimary,
                unfocusedBorderColor = OrbitColors.BorderSubtle,
                focusedTextColor = OrbitColors.TextPrimary,
                unfocusedTextColor = OrbitColors.TextPrimary
            )
        )

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = OrbitColors.StatusOffline
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isConnected) {
                OrbitButton(
                    text = "Disconnect",
                    onClick = onDisconnect,
                    isSecondary = true,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                OrbitButton(
                    text = "Connect",
                    onClick = onConnect,
                    isLoading = isConnecting,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
