package com.pratyush.orbitmobile.core.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pratyush.orbitmobile.R

/**
 * Official ORBIT mascot logo branding component.
 */
@Composable
fun OrbitLogo(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    Image(
        painter = painterResource(id = R.drawable.orbit_logo),
        contentDescription = "ORBIT Logo",
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit
    )
}
