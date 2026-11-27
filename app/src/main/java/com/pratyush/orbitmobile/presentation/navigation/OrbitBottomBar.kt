package com.pratyush.orbitmobile.presentation.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pratyush.orbitmobile.core.designsystem.theme.OrbitColors
import com.pratyush.orbitmobile.core.navigation.AppRoute

/**
 * Floating capsule / pill-shaped bottom navigation component inspired by Pixel Material You.
 * Detached from screen edges, with dark translucent surface.
 * Selected item is enclosed in a unified rounded pill surrounding BOTH icon and label.
 */
@Composable
fun OrbitBottomBar(
    currentRoute: String?,
    destinations: List<AppRoute> = remember { AppRoute.bottomDestinations },
    onNavigateToRoute: (AppRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = OrbitColors.ClipboardTheme
    val validDestinations = remember(destinations) { destinations.filterNotNull() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
            color = theme.NavPillBg,
            border = androidx.compose.foundation.BorderStroke(1.dp, theme.NavPillBorder),
            shadowElevation = 12.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                validDestinations.forEach { routeItem ->
                    androidx.compose.runtime.key(routeItem.route) {
                        val isSelected = currentRoute == routeItem.route
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onNavigateToRoute(routeItem) }
                                )
                                .padding(vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            FloatingNavItem(
                                item = routeItem,
                                isSelected = isSelected
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingNavItem(
    item: AppRoute,
    isSelected: Boolean
) {
    val theme = OrbitColors.ClipboardTheme

    val targetCapsuleBg = when {
        !isSelected -> Color.Transparent
        item == AppRoute.Controls -> Color(0xFF583C7E)
        else -> theme.NavSelectedPill
    }

    val capsuleBackground by animateColorAsState(
        targetValue = targetCapsuleBg,
        animationSpec = tween(220),
        label = "nav_capsule_bg"
    )

    val iconTint by animateColorAsState(
        targetValue = if (isSelected) Color.White else theme.NavUnselectedTint,
        animationSpec = tween(220),
        label = "nav_icon_tint"
    )

    val labelColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else theme.NavUnselectedTint,
        animationSpec = tween(220),
        label = "nav_label_color"
    )

    // Unified pill capsule enclosing BOTH icon and label
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(capsuleBackground)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = item.title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.5.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = labelColor
                ),
                maxLines = 1
            )
        }
    }
}
