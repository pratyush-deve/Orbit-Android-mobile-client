package com.pratyush.orbitmobile.core.designsystem.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pratyush.orbitmobile.core.designsystem.theme.OrbitColors

/**
 * Centrally styled TopAppBar with pitch-black background.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrbitTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = OrbitColors.TextPrimary
            )
        },
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = OrbitColors.PitchBlack,
            titleContentColor = OrbitColors.TextPrimary,
            navigationIconContentColor = OrbitColors.TextPrimary,
            actionIconContentColor = OrbitColors.TextPrimary
        )
    )
}
