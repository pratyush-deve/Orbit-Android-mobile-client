package com.pratyush.orbitmobile.presentation.screens.about.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pratyush.orbitmobile.core.designsystem.components.OrbitCard
import com.pratyush.orbitmobile.core.designsystem.theme.OrbitColors
import com.pratyush.orbitmobile.presentation.screens.about.AboutUiState

/**
 * Card displaying app information and architectural purpose.
 */
@Composable
fun AboutAppInfoCard(
    uiState: AboutUiState,
    modifier: Modifier = Modifier
) {
    OrbitCard(
        modifier = modifier.fillMaxWidth(),
        hasGlowBorder = true
    ) {
        Text(
            text = uiState.appName,
            style = MaterialTheme.typography.headlineSmall,
            color = OrbitColors.PurpleAccentGlow
        )
        Text(
            text = "Version ${uiState.appVersion}",
            style = MaterialTheme.typography.labelMedium,
            color = OrbitColors.TextSecondary
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = uiState.description,
            style = MaterialTheme.typography.bodyMedium,
            color = OrbitColors.TextPrimary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Role: ${uiState.companionTarget}",
            style = MaterialTheme.typography.bodySmall,
            color = OrbitColors.PurpleSecondary
        )
    }
}
