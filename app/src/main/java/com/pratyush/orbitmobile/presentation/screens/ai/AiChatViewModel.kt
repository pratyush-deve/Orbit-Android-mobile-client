package com.pratyush.orbitmobile.presentation.screens.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pratyush.orbitmobile.core.common.OrbitResult
import com.pratyush.orbitmobile.domain.repository.AiChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel managing state and user interactions for the shared Antigravity AI Chat interface.
 */
class AiChatViewModel(
    private val aiChatRepository: AiChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiChatUiState())
    val uiState: StateFlow<AiChatUiState> = _uiState.asStateFlow()

    init {
        // Collect reactive state flows from domain repository
        viewModelScope.launch {
            combine(
                combine(aiChatRepository.acpStatus, aiChatRepository.conversations, aiChatRepository.currentConversationId) { status, conversations, currentId ->
                    Triple(status, conversations, currentId)
                },
                combine(aiChatRepository.currentMessages, aiChatRepository.isStreaming, aiChatRepository.pendingPermissionRequest) { messages, isStreaming, pendingPerm ->
                    Triple(messages, isStreaming, pendingPerm)
                }
            ) { (status, conversations, currentId), (messages, isStreaming, pendingPerm) ->
                val activeConv = conversations.firstOrNull { it.id == currentId }
                _uiState.value.copy(
                    acpStatus = status,
                    conversations = conversations,
                    currentConversationId = currentId,
                    currentConversation = activeConv,
                    messages = messages,
                    isStreaming = isStreaming,
                    pendingPermissionRequest = pendingPerm
                )
            }.collect { updatedState ->
                _uiState.value = updatedState
            }
        }

        // Initial sync
        viewModelScope.launch {
            aiChatRepository.refreshStatus()
            aiChatRepository.refreshConversations()
        }
    }

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty() || _uiState.value.isStreaming) return

        _uiState.update { it.copy(inputText = "") }

        viewModelScope.launch {
            val result = aiChatRepository.sendMessage(text)
            if (result is OrbitResult.Error) {
                _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }

    fun selectConversation(conversationId: String) {
        viewModelScope.launch {
            aiChatRepository.selectConversation(conversationId)
        }
    }

    fun newChat() {
        viewModelScope.launch {
            val result = aiChatRepository.createNewConversation()
            if (result is OrbitResult.Error) {
                _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            val result = aiChatRepository.deleteConversation(conversationId)
            if (result is OrbitResult.Error) {
                _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }

    fun resolvePermission(optionId: String) {
        viewModelScope.launch {
            val result = aiChatRepository.resolvePermission(optionId)
            if (result is OrbitResult.Error) {
                _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }

    fun setPermissionPolicy(policy: String) {
        _uiState.update { it.copy(permissionPolicy = policy) }
        viewModelScope.launch {
            aiChatRepository.setPermissionPolicy(policy)
        }
    }

    fun cancelActiveGeneration() {
        viewModelScope.launch {
            aiChatRepository.cancelActiveGeneration()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun refresh() {
        viewModelScope.launch {
            aiChatRepository.refreshStatus()
            aiChatRepository.refreshConversations()
        }
    }
}
