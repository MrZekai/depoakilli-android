package com.mrzekai.depoakilli.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DepoColors = darkColorScheme(
    primary = Lime400,
    onPrimary = Forest950,
    primaryContainer = Forest700,
    onPrimaryContainer = Lime300,
    secondary = Mint100,
    onSecondary = Forest950,
    background = Forest950,
    onBackground = WhiteSoft,
    surface = Forest900,
    onSurface = WhiteSoft,
    surfaceVariant = Forest800,
    onSurfaceVariant = Mint100,
    error = Red400,
    outline = Color(0xFF719187),
)

@Composable
fun DepoAkilliTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DepoColors,
        content = content,
    )
}
