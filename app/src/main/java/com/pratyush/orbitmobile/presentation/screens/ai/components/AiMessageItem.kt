package com.pratyush.orbitmobile.presentation.screens.ai.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pratyush.orbitmobile.core.designsystem.theme.OrbitColors
import com.pratyush.orbitmobile.domain.model.AiMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AiMessageItem(
    message: AiMessage,
    isStreamingMessage: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isUser = message.role.equals("user", ignoreCase = true)

    val formattedTime = remember(message.timestamp) {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        sdf.format(Date(message.timestamp))
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (isUser) {
            UserMessageBubble(message = message, formattedTime = formattedTime)
        } else {
            AssistantMessageBubble(
                message = message,
                formattedTime = formattedTime,
                isStreaming = isStreamingMessage
            )
        }
    }
}

@Composable
private fun UserMessageBubble(
    message: AiMessage,
    formattedTime: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth(0.85f),
        horizontalAlignment = Alignment.End
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = OrbitColors.TextTertiary,
                    fontSize = 10.sp
                )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(OrbitColors.PurplePrimary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = OrbitColors.PurpleSecondary,
                    modifier = Modifier.size(11.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp))
                .background(Color(0xFF281E46))
                .border(1.dp, OrbitColors.PurplePrimary.copy(alpha = 0.35f), RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White,
                    lineHeight = 20.sp
                )
            )
        }
    }
}

@Composable
private fun AssistantMessageBubble(
    message: AiMessage,
    formattedTime: String,
    isStreaming: Boolean,
    modifier: Modifier = Modifier
) {
    var thoughtExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(0.92f),
        horizontalAlignment = Alignment.Start
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E1035)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = OrbitColors.PurpleAccentGlow,
                    modifier = Modifier.size(12.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = "Antigravity",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = OrbitColors.TextPrimary
                )
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = "CLI",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = OrbitColors.PurpleSecondary
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(OrbitColors.PurplePrimary.copy(alpha = 0.15f))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = formattedTime,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = OrbitColors.TextTertiary,
                    fontSize = 10.sp
                )
            )
        }

        // Assistant container
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp))
                .background(Color(0xFF12121A))
                .border(1.dp, OrbitColors.BorderSubtle, RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Optional Thinking process section
            if (!message.thought.isNullOrBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0D0D14))
                        .border(1.dp, Color(0xFF1E1E2C), RoundedCornerShape(8.dp))
                        .clickable { thoughtExpanded = !thoughtExpanded }
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Psychology,
                                contentDescription = null,
                                tint = OrbitColors.PurpleSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Thinking Process",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = OrbitColors.PurpleSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }

                        Icon(
                            imageVector = if (thoughtExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = OrbitColors.TextTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    AnimatedVisibility(visible = thoughtExpanded) {
                        Text(
                            text = message.thought,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = OrbitColors.TextSecondary,
                                lineHeight = 16.sp
                            ),
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }

            // Tool activities (keyed by toolCallId to preserve card state and avoid duplicate items)
            if (message.toolActivities.isNotEmpty()) {
                message.toolActivities.forEach { tool ->
                    key(tool.toolCallId) {
                        AiToolActivityCard(tool = tool)
                    }
                }
            }

            // Main Message Content (Rich Markdown blocks)
            AiMarkdownRenderer(
                content = message.content,
                isStreaming = isStreaming
            )
        }
    }
}
