package com.mrzekai.depoakilli.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DepoColors = lightColorScheme(
    primary = Lime400,
    onPrimary = Color.White,
    primaryContainer = Forest700,
    onPrimaryContainer = WhiteSoft,
    secondary = Teal500,
    onSecondary = Color.White,
    background = Forest950,
    onBackground = WhiteSoft,
    surface = Forest900,
    onSurface = WhiteSoft,
    surfaceVariant = Forest800,
    onSurfaceVariant = Mint100,
    error = Red400,
    outline = Color(0xFF8291A7),
)

@Composable
fun DepoAkilliTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DepoColors,
        content = content,
    )
}
