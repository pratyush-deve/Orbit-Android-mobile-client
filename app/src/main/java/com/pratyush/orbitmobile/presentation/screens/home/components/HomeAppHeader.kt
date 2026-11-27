package com.pratyush.orbitmobile.presentation.screens.home.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pratyush.orbitmobile.core.designsystem.components.OrbitLogo

/**
 * Top brand header matching the design template.
 * Planet logo with orbital ring, app title, companion subtitle, and 3-dot overflow button.
 */
@Composable
fun HomeAppHeader(
    onOverflowClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Planet vector logo with orbital ring
            OrbitLogo(size = 40.dp)

            Column {
                Text(
                    text = "ORBIT Mobile",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 20.sp,
                        color = Color.White
                    )
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = "Your ORBIT Windows companion",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                )
            }
        }

        // Circular 3-dot overflow action button
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color(0xFF1E1E28), CircleShape)
                .clickable { onOverflowClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Options",
                tint = Color(0xFFE2E8F0),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
