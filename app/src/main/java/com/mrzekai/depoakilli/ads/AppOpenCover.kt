package com.mrzekai.depoakilli.ads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrzekai.depoakilli.R

/**
 * The app's loading cover. The app open ad is only shown on top of this cover,
 * never over usable content; touches are consumed so nothing underneath is hit.
 */
@Composable
fun AppOpenCover() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030A1D))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                stringResource(R.string.app_name),
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
            )
            CircularProgressIndicator(modifier = Modifier.size(32.dp), color = Color(0xFF28E58A))
        }
    }
}
