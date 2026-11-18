package com.mrzekai.depoakilli.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

private val DepoColors = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = Color.White,
    primaryContainer = Forest700,
    onPrimaryContainer = Color.White,
    secondary = Teal500,
    onSecondary = Color(0xFF001F1A),
    background = Forest950,
    onBackground = WhiteSoft,
    surface = Forest900,
    onSurface = WhiteSoft,
    surfaceVariant = Forest800,
    onSurfaceVariant = Mint100,
    error = Red400,
    outline = Color(0xFF33456E),
)

/** Material's labelSmall is 11 sp; the app keeps every text at 12 sp or larger. */
private val DepoTypography = Typography().let { base ->
    base.copy(labelSmall = base.labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp))
}

@Composable
fun DepoAkilliTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DepoColors,
        typography = DepoTypography,
        content = content,
    )
}
