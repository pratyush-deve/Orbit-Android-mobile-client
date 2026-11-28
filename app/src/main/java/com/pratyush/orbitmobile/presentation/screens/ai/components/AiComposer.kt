package com.pratyush.orbitmobile.presentation.screens.ai.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.pratyush.orbitmobile.core.designsystem.theme.OrbitColors

@Composable
fun AiComposer(
    inputText: String,
    isStreaming: Boolean,
    isAcpAvailable: Boolean,
    onInputChanged: (String) -> Unit,
    onSendMessage: () -> Unit,
    onCancelActiveGeneration: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var wasFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0B0B10))
            .border(1.dp, OrbitColors.BorderSubtle)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChanged,
                placeholder = {
                    Text(
                        text = if (isAcpAvailable) "Message Antigravity CLI..." else "Connecting to Windows ACP...",
                        style = MaterialTheme.typography.bodyMedium.copy(color = OrbitColors.TextTertiary)
                    )
                },
                enabled = isAcpAvailable || isStreaming,
                maxLines = 5,
                shape = RoundedCornerShape(20.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Default
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF14141E),
                    unfocusedContainerColor = Color(0xFF14141E),
                    disabledContainerColor = Color(0xFF101018),
                    focusedBorderColor = OrbitColors.PurplePrimary,
                    unfocusedBorderColor = OrbitColors.BorderSubtle,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
                    .onFocusChanged { focusState ->
                        if (wasFocused && !focusState.isFocused) {
                            keyboardController?.hide()
                        }
                        wasFocused = focusState.isFocused
                    }
            )

            if (isStreaming) {
                // Stop Generation button
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(OrbitColors.StatusOffline.copy(alpha = 0.2f))
                        .border(1.dp, OrbitColors.StatusOffline, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = onCancelActiveGeneration) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = "Stop generation",
                            tint = OrbitColors.StatusOffline,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            } else {
                // Send button
                val canSend = isAcpAvailable && inputText.isNotBlank()
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (canSend) OrbitColors.PurplePrimary else Color(0xFF1E1E28)),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = {
                            onSendMessage()
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        },
                        enabled = canSend
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send message",
                            tint = if (canSend) Color.White else OrbitColors.TextTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
