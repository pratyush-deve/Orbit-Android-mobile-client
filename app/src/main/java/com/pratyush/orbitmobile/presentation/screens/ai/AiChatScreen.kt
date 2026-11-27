package com.pratyush.orbitmobile.presentation.screens.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pratyush.orbitmobile.core.designsystem.theme.OrbitColors
import com.pratyush.orbitmobile.presentation.screens.ai.components.AiComposer
import com.pratyush.orbitmobile.presentation.screens.ai.components.AiDrawerContent
import com.pratyush.orbitmobile.presentation.screens.ai.components.AiMessageItem
import com.pratyush.orbitmobile.presentation.screens.ai.components.AiPermissionCard
import kotlinx.coroutines.launch

@Composable
fun AiChatScreen(
    viewModel: AiChatViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Auto-scroll to bottom on new message or during streaming updates
    LaunchedEffect(uiState.messages.size, uiState.messages.lastOrNull()?.content?.length) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    // Display error messages
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF0C0C12)
            ) {
                AiDrawerContent(
                    conversations = uiState.filteredConversations,
                    currentConversationId = uiState.currentConversationId,
                    searchQuery = uiState.searchQuery,
                    permissionPolicy = uiState.permissionPolicy,
                    onSearchChanged = viewModel::onSearchQueryChanged,
                    onSelectConversation = { id ->
                        viewModel.selectConversation(id)
                        scope.launch { drawerState.close() }
                    },
                    onNewChat = {
                        viewModel.newChat()
                        scope.launch { drawerState.close() }
                    },
                    onDeleteConversation = viewModel::deleteConversation,
                    onSetPolicy = viewModel::setPermissionPolicy
                )
            }
        }
    ) {
        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF070709)),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                AiTopBar(
                    title = uiState.currentConversation?.title ?: "Antigravity AI",
                    isAcpAvailable = uiState.acpStatus.isAvailable,
                    isConnecting = uiState.acpStatus.isConnecting,
                    activeModel = uiState.acpStatus.activeModel,
                    onBackClick = onNavigateBack,
                    onMenuClick = {
                        scope.launch {
                            if (drawerState.isClosed) drawerState.open() else drawerState.close()
                        }
                    }
                )
            },
            bottomBar = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Pending Permission Card above composer if suspended
                    AnimatedVisibility(visible = uiState.pendingPermissionRequest != null) {
                        uiState.pendingPermissionRequest?.let { request ->
                            AiPermissionCard(
                                request = request,
                                onResolve = viewModel::resolvePermission,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    AiComposer(
                        inputText = uiState.inputText,
                        isStreaming = uiState.isStreaming,
                        isAcpAvailable = uiState.acpStatus.isAvailable,
                        onInputChanged = viewModel::onInputChanged,
                        onSendMessage = viewModel::sendMessage,
                        onCancelActiveGeneration = viewModel::cancelActiveGeneration
                    )
                }
            },
            containerColor = Color(0xFF070709)
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (uiState.messages.isEmpty()) {
                    // Welcome & Empty State
                    AiEmptyState(
                        isAcpAvailable = uiState.acpStatus.isAvailable,
                        onPromptSelected = { prompt ->
                            viewModel.onInputChanged(prompt)
                            viewModel.sendMessage()
                        }
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(
                            items = uiState.messages,
                            key = { _, msg -> msg.id }
                        ) { index, message ->
                            val isLatestAssistant = !message.role.equals("user", ignoreCase = true) &&
                                    index == uiState.messages.lastIndex &&
                                    uiState.isStreaming

                            AiMessageItem(
                                message = message,
                                isStreamingMessage = isLatestAssistant
                            )
                        }

                        // Bottom spacer
                        item {
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiTopBar(
    title: String,
    isAcpAvailable: Boolean,
    isConnecting: Boolean,
    activeModel: String,
    onBackClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF09090D))
            .border(1.dp, OrbitColors.BorderSubtle)
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = OrbitColors.TextPrimary
                )
            }

            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = "Open Drawer",
                    tint = OrbitColors.TextSecondary
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    maxLines = 1
                )
                Text(
                    text = activeModel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = OrbitColors.PurpleSecondary
                    )
                )
            }
        }

        // Live ACP Status Badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(
                    when {
                        isAcpAvailable -> OrbitColors.StatusOnline.copy(alpha = 0.15f)
                        isConnecting -> OrbitColors.StatusConnecting.copy(alpha = 0.15f)
                        else -> OrbitColors.StatusOffline.copy(alpha = 0.15f)
                    }
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isAcpAvailable -> OrbitColors.StatusOnline
                            isConnecting -> OrbitColors.StatusConnecting
                            else -> OrbitColors.StatusOffline
                        }
                    )
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = when {
                    isAcpAvailable -> "ACP Live"
                    isConnecting -> "Connecting"
                    else -> "Offline"
                },
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = when {
                        isAcpAvailable -> OrbitColors.StatusOnline
                        isConnecting -> OrbitColors.StatusConnecting
                        else -> OrbitColors.StatusOffline
                    }
                )
            )
        }
    }
}

@Composable
private fun AiEmptyState(
    isAcpAvailable: Boolean,
    onPromptSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color(0xFF1B122C))
                .border(1.dp, OrbitColors.PurpleAccentGlow.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = OrbitColors.PurpleAccentGlow,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Antigravity AI Assistant",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (isAcpAvailable) {
                "Direct bidirectional ACP link with your Windows host.\nAsk questions, manage code, or execute agent tools."
            } else {
                "Waiting for Windows ORBIT companion connection..."
            },
            style = MaterialTheme.typography.bodyMedium.copy(
                color = OrbitColors.TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        )

        if (isAcpAvailable) {
            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "SUGGESTED ACTIONS",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = OrbitColors.TextTertiary,
                    letterSpacing = 1.sp
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            val suggestions = listOf(
                "Check git status and pending changes",
                "Review recent project commits",
                "Explain project architecture overview",
                "Run test suite and verify build"
            )

            suggestions.forEach { prompt ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF13131C))
                        .border(1.dp, OrbitColors.BorderSubtle, RoundedCornerShape(10.dp))
                        .clickable { onPromptSelected(prompt) }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = prompt,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = OrbitColors.TextPrimary,
                            fontSize = 13.sp
                        )
                    )
                }
            }
        }
    }
}
