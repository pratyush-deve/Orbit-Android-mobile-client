package com.pratyush.orbitmobile.presentation.screens.ai.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pratyush.orbitmobile.core.designsystem.theme.OrbitColors
import com.pratyush.orbitmobile.domain.model.AiToolActivity

@Composable
fun AiToolActivityCard(
    tool: AiToolActivity,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val statusColor = when (tool.status.lowercase()) {
        "completed", "done", "success" -> OrbitColors.StatusOnline
        "running", "in_progress" -> OrbitColors.StatusConnecting
        "failed", "error" -> OrbitColors.StatusOffline
        else -> OrbitColors.TextSecondary
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0E0E14))
            .border(1.dp, OrbitColors.BorderSubtle, RoundedCornerShape(8.dp))
            .clickable { expanded = !expanded }
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    when (tool.status.lowercase()) {
                        "running", "in_progress" -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                color = statusColor,
                                strokeWidth = 1.5.dp
                            )
                        }
                        "failed", "error" -> {
                            Icon(
                                imageVector = Icons.Filled.Error,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    val displayTitle = tool.title?.ifBlank { null } ?: tool.toolName
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = OrbitColors.TextPrimary
                        )
                    )
                    if (tool.parameters.isNotBlank()) {
                        Text(
                            text = tool.parameters.take(80) + if (tool.parameters.length > 80) "..." else "",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = OrbitColors.TextSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                }
            }

            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = OrbitColors.TextTertiary,
                modifier = Modifier.size(20.dp)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                if (tool.parameters.length > 80) {
                    Text(
                        text = "Parameters:",
                        style = MaterialTheme.typography.labelSmall.copy(color = OrbitColors.TextTertiary)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tool.parameters,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFFCBD5E1)
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                val outputText = tool.output
                if (!outputText.isNullOrBlank()) {
                    Text(
                        text = "Result:",
                        style = MaterialTheme.typography.labelSmall.copy(color = OrbitColors.TextTertiary)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF08080C))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = outputText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        )
                    }
                }
            }
        }
    }
}
