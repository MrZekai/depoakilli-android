package com.mrzekai.depoakilli.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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

@Composable
fun DepoAkilliTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DepoColors,
        content = content,
    )
}
