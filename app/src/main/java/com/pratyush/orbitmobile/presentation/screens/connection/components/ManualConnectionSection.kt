package com.pratyush.orbitmobile.presentation.screens.connection.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pratyush.orbitmobile.core.designsystem.components.OrbitButton
import com.pratyush.orbitmobile.core.designsystem.components.OrbitCard
import com.pratyush.orbitmobile.core.designsystem.theme.OrbitColors

/**
 * Secondary manual connection section allowing direct IP & Port entry.
 */
@Composable
fun ManualConnectionSection(
    hostInput: String,
    portInput: String,
    isConnecting: Boolean,
    errorMessage: String?,
    onHostChanged: (String) -> Unit,
    onPortChanged: (String) -> Unit,
    onConnectManual: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    OrbitCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.SettingsEthernet,
                    contentDescription = null,
                    tint = OrbitColors.PurpleSecondary
                )
                Column {
                    Text(
                        text = "Connect Manually via IP",
                        style = MaterialTheme.typography.titleMedium,
                        color = OrbitColors.TextPrimary
                    )
                    Text(
                        text = "Use this if QR code is not scanning",
                        style = MaterialTheme.typography.bodySmall,
                        color = OrbitColors.TextSecondary
                    )
                }
            }

            Icon(
                imageVector = if (isExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = OrbitColors.TextSecondary
            )
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = hostInput,
                    onValueChange = onHostChanged,
                    label = { Text("PC IP Address") },
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

                OrbitButton(
                    text = "Connect to PC",
                    onClick = onConnectManual,
                    isLoading = isConnecting,
                    isSecondary = true
                )
            }
        }
    }
}
