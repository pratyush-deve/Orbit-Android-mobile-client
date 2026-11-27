package com.pratyush.orbitmobile.presentation.screens.settings

import com.pratyush.orbitmobile.domain.model.AppConfig

/**
 * Immutable UI state for Settings screen.
 */
data class SettingsUiState(
    val config: AppConfig = AppConfig(),
    val isLoading: Boolean = false,
    val feedbackMessage: String? = null
)
