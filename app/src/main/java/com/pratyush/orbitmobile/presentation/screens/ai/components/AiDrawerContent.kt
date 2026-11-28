package com.pratyush.orbitmobile.presentation.screens.ai.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pratyush.orbitmobile.core.designsystem.theme.OrbitColors
import com.pratyush.orbitmobile.domain.model.AiConversation
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun AiDrawerContent(
    conversations: List<AiConversation>,
    currentConversationId: String?,
    searchQuery: String,
    permissionPolicy: String,
    onSearchChanged: (String) -> Unit,
    onSelectConversation: (String) -> Unit,
    onNewChat: () -> Unit,
    onRenameConversation: (String, String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onSetPolicy: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var conversationToRename by remember { mutableStateOf<AiConversation?>(null) }
    var conversationToDelete by remember { mutableStateOf<AiConversation?>(null) }
    var showPolicyDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(320.dp)
            .background(Color(0xFF0C0C12))
            .padding(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Conversations",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = OrbitColors.TextPrimary
                )
            )

            Text(
                text = "${conversations.size}",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = OrbitColors.PurpleSecondary,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(OrbitColors.PurplePrimary.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // New Chat Button
        Button(
            onClick = onNewChat,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = OrbitColors.PurplePrimary
            )
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "New Conversation",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search chats
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChanged,
            placeholder = {
                Text(
                    text = "Search chats...",
                    style = MaterialTheme.typography.bodySmall.copy(color = OrbitColors.TextTertiary)
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = OrbitColors.TextTertiary,
                    modifier = Modifier.size(16.dp)
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF14141E),
                unfocusedContainerColor = Color(0xFF14141E),
                focusedBorderColor = OrbitColors.PurplePrimary,
                unfocusedBorderColor = OrbitColors.BorderSubtle,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Grouped conversations list
        val groupedConversations = remember(conversations) {
            groupConversations(conversations)
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            groupedConversations.forEach { (section, items) ->
                item(key = "section_$section") {
                    Text(
                        text = section,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = OrbitColors.TextTertiary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)
                    )
                }

                items(items, key = { it.id }) { conv ->
                    val isSelected = conv.id == currentConversationId
                    ConversationDrawerItem(
                        conversation = conv,
                        isSelected = isSelected,
                        onClick = { onSelectConversation(conv.id) },
                        onRename = { conversationToRename = conv },
                        onDelete = { conversationToDelete = conv }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Divider(color = OrbitColors.BorderSubtle, thickness = 1.dp)
        Spacer(modifier = Modifier.height(8.dp))

        // Bottom Policy Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF14141C))
                .border(1.dp, OrbitColors.BorderSubtle, RoundedCornerShape(8.dp))
                .clickable { showPolicyDialog = true }
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = null,
                    tint = OrbitColors.PurpleSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Tool Permission Policy",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = OrbitColors.TextTertiary,
                            fontSize = 10.sp
                        )
                    )
                    Text(
                        text = when (permissionPolicy) {
                            "AutoApproveSafe" -> "Safe Auto-Approve"
                            "AlwaysAsk" -> "Always Confirm"
                            "AutoApproveAll" -> "Auto-Approve All"
                            else -> permissionPolicy
                        },
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = OrbitColors.PurpleSecondary
                        )
                    )
                }
            }
        }
    }

    // Rename conversation dialog
    conversationToRename?.let { conv ->
        var editedTitle by remember(conv.id) { mutableStateOf(conv.title) }
        val trimmedTitle = editedTitle.trim()
        val canSave = trimmedTitle.isNotEmpty() && trimmedTitle != conv.title

        AlertDialog(
            onDismissRequest = { conversationToRename = null },
            title = {
                Text(text = "Rename Conversation", color = Color.White)
            },
            text = {
                OutlinedTextField(
                    value = editedTitle,
                    onValueChange = { editedTitle = it },
                    label = {
                        Text("Title", color = OrbitColors.TextTertiary)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF14141E),
                        unfocusedContainerColor = Color(0xFF14141E),
                        focusedBorderColor = OrbitColors.PurplePrimary,
                        unfocusedBorderColor = OrbitColors.BorderSubtle,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameConversation(conv.id, trimmedTitle)
                        conversationToRename = null
                    },
                    enabled = canSave,
                    colors = ButtonDefaults.buttonColors(containerColor = OrbitColors.PurplePrimary)
                ) {
                    Text("Save", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { conversationToRename = null }) {
                    Text("Cancel", color = OrbitColors.TextSecondary)
                }
            },
            containerColor = Color(0xFF181822)
        )
    }

    // Delete confirmation dialog
    conversationToDelete?.let { conv ->
        AlertDialog(
            onDismissRequest = { conversationToDelete = null },
            title = {
                Text(text = "Delete Conversation?", color = Color.White)
            },
            text = {
                Text(
                    text = "Are you sure you want to delete \"${conv.title}\"? This will remove it from both your phone and Windows companion.",
                    color = OrbitColors.TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteConversation(conv.id)
                        conversationToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OrbitColors.StatusOffline)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { conversationToDelete = null }) {
                    Text("Cancel", color = OrbitColors.TextSecondary)
                }
            },
            containerColor = Color(0xFF181822)
        )
    }

    // Policy dialog
    if (showPolicyDialog) {
        AlertDialog(
            onDismissRequest = { showPolicyDialog = false },
            title = { Text(text = "Tool Authorization Policy", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Configure how ACP tool operations are approved from your mobile device:",
                        style = MaterialTheme.typography.bodySmall.copy(color = OrbitColors.TextSecondary)
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    PolicyOptionRow(
                        title = "Safe Auto-Approve (Recommended)",
                        description = "Automatically approves read_file, search_code, git status, safe edits. Suspends for sensitive commands.",
                        isSelected = permissionPolicy == "AutoApproveSafe",
                        onClick = {
                            onSetPolicy("AutoApproveSafe")
                            showPolicyDialog = false
                        }
                    )

                    PolicyOptionRow(
                        title = "Always Ask",
                        description = "Prompts on mobile for every tool execution.",
                        isSelected = permissionPolicy == "AlwaysAsk",
                        onClick = {
                            onSetPolicy("AlwaysAsk")
                            showPolicyDialog = false
                        }
                    )

                    PolicyOptionRow(
                        title = "Auto-Approve All",
                        description = "Permits all actions including shell commands automatically.",
                        isSelected = permissionPolicy == "AutoApproveAll",
                        onClick = {
                            onSetPolicy("AutoApproveAll")
                            showPolicyDialog = false
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPolicyDialog = false }) {
                    Text("Close", color = OrbitColors.TextSecondary)
                }
            },
            containerColor = Color(0xFF181822)
        )
    }
}

@Composable
private fun PolicyOptionRow(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) OrbitColors.PurplePrimary.copy(alpha = 0.15f) else Color(0xFF101018))
            .border(
                1.dp,
                if (isSelected) OrbitColors.PurplePrimary else OrbitColors.BorderSubtle,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) OrbitColors.PurpleSecondary else Color.White
                )
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = OrbitColors.TextTertiary,
                    fontSize = 11.sp
                )
            )
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = OrbitColors.PurpleSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ConversationDrawerItem(
    conversation: AiConversation,
    isSelected: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDesktop = conversation.origin.equals("desktop", ignoreCase = true)
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) OrbitColors.PurplePrimary.copy(alpha = 0.2f) else Color.Transparent
            )
            .border(
                1.dp,
                if (isSelected) OrbitColors.PurplePrimary.copy(alpha = 0.4f) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = if (isDesktop) Icons.Filled.Computer else Icons.Filled.PhoneAndroid,
                contentDescription = null,
                tint = if (isSelected) OrbitColors.PurpleSecondary else OrbitColors.TextTertiary,
                modifier = Modifier.size(16.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column {
                Text(
                    text = conversation.title.ifBlank { "Untitled Chat" },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Color.White else OrbitColors.TextPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "Conversation options",
                    tint = OrbitColors.TextTertiary,
                    modifier = Modifier.size(16.dp)
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                containerColor = Color(0xFF181822)
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Rename",
                            color = OrbitColors.TextPrimary
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = null,
                            tint = OrbitColors.PurpleSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onRename()
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Delete",
                            color = OrbitColors.StatusOffline
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = null,
                            tint = OrbitColors.StatusOffline,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    }
                )
            }
        }
    }
}

private fun groupConversations(conversations: List<AiConversation>): Map<String, List<AiConversation>> {
    val result = linkedMapOf<String, MutableList<AiConversation>>()

    val now = Calendar.getInstance()
    val today = now.get(Calendar.DAY_OF_YEAR)
    val thisYear = now.get(Calendar.YEAR)

    conversations.forEach { conv ->
        val cal = Calendar.getInstance().apply { timeInMillis = conv.updatedAt }
        val day = cal.get(Calendar.DAY_OF_YEAR)
        val year = cal.get(Calendar.YEAR)

        val section = when {
            year == thisYear && day == today -> "Today"
            year == thisYear && day == today - 1 -> "Yesterday"
            else -> "Previous"
        }

        result.getOrPut(section) { mutableListOf() }.add(conv)
    }

    return result
}
