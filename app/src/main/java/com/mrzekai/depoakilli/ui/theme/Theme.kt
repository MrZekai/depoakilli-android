package com.mrzekai.depoakilli.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.sp

/**
 * The single theme of the app (Faz 5): Lime pages, black surfaces. There is
 * no separate light/dark variant. Material defaults (cards, sheets, dialogs)
 * pick up the black surface with light text; text placed directly on the
 * Lime page uses [Ink].
 */
private val AppColors = lightColorScheme(
    primary = Lime,
    onPrimary = Ink,
    primaryContainer = BlackChip,
    onPrimaryContainer = OnBlack,
    secondary = Lime,
    onSecondary = Ink,
    secondaryContainer = BlackChip,
    onSecondaryContainer = OnBlack,
    tertiary = Lime,
    onTertiary = Ink,
    background = Lime,
    onBackground = Ink,
    surface = Black,
    onSurface = OnBlack,
    surfaceVariant = BlackChip,
    onSurfaceVariant = OnBlackSoft,
    surfaceContainerLowest = Black,
    surfaceContainerLow = Black,
    surfaceContainer = Black,
    surfaceContainerHigh = Black,
    surfaceContainerHighest = Black,
    surfaceBright = BlackChip,
    surfaceDim = Black,
    inverseSurface = OnBlack,
    inverseOnSurface = Black,
    inversePrimary = InkSoft,
    error = Coral,
    onError = CoralInk,
    errorContainer = Coral,
    onErrorContainer = CoralInk,
    outline = BlackLine,
    outlineVariant = BlackLine,
    scrim = Black,
)

@Composable
fun DepoAkilliTheme(content: @Composable () -> Unit) {
    val language = LocalConfiguration.current.locales[0]?.language.orEmpty()
    // Material's labelSmall is 11 sp; appTypography keeps every text at 12 sp or larger.
    val typography: Typography = remember(language) {
        val base = appTypography(appFontFamily(language))
        base.copy(labelSmall = base.labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp))
    }
    MaterialTheme(
        colorScheme = AppColors,
        typography = typography,
        content = content,
    )
}
