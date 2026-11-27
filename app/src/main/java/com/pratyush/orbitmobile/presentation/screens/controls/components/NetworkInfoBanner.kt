package com.pratyush.orbitmobile.presentation.screens.controls.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Local network information banner.
 * Warm bronze/amber tonal styling.
 */
@Composable
fun NetworkInfoBanner(
    modifier: Modifier = Modifier
) {
    val bannerBg = Color(0xFF261D15)
    val bannerBorder = Color(0xFF453018)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(bannerBg)
            .border(1.dp, bannerBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = "Info",
                tint = Color(0xFFFBBF24),
                modifier = Modifier.size(24.dp)
            )

            Text(
                text = "Controls work over your local network (LAN). Make sure your laptop is online and connected to ORBIT.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color(0xFFFDE68A),
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            )
        }
    }
}
