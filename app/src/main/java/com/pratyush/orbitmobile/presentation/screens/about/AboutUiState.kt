package com.pratyush.orbitmobile.presentation.screens.about

import com.pratyush.orbitmobile.core.common.Constants

/**
 * Immutable UI state for About screen.
 */
data class AboutUiState(
    val appName: String = Constants.APP_NAME,
    val appVersion: String = Constants.APP_VERSION,
    val description: String = "ORBIT Mobile is an independent Android companion application for the ORBIT Windows application, built with Jetpack Compose, Material 3, and Kotlin Coroutines.",
    val companionTarget: String = "ORBIT Windows Companion"
)
