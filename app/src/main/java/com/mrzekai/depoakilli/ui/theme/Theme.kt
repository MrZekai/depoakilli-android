package com.mrzekai.depoakilli.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DepoColors = lightColorScheme(
    primary = ElectricBlue,
    onPrimary = Color.White,
    primaryContainer = Forest700,
    onPrimaryContainer = Color(0xFF0A235C),
    secondary = Teal500,
    onSecondary = Color.White,
    background = Forest950,
    onBackground = WhiteSoft,
    surface = Forest900,
    onSurface = WhiteSoft,
    surfaceVariant = Forest800,
    onSurfaceVariant = Mint100,
    error = Red400,
    outline = Color(0xFFB8C3D5),
)

@Composable
fun DepoAkilliTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DepoColors,
        content = content,
    )
}
