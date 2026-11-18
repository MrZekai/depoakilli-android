package com.mrzekai.depoakilli.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mrzekai.depoakilli.R

/**
 * Archivo (SIL OFL, docs/ARCHIVO_FONT_LICENSE.txt) as one variable font
 * (weights 600–900). It has no Cyrillic or Arabic letters, so Russian and
 * Arabic use the system font with the same weights.
 */
@OptIn(ExperimentalTextApi::class)
private fun archivo(weight: Int) = Font(
    R.font.archivo_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val ArchivoFamily: FontFamily = FontFamily(archivo(600), archivo(700), archivo(800), archivo(900))

/** Languages whose letters Archivo does not contain. */
private val SYSTEM_FONT_LANGUAGES = setOf("ru", "ar")

/** Archivo for Latin-script languages, the system font for Russian and Arabic. */
fun appFontFamily(language: String): FontFamily =
    if (language.lowercase() in SYSTEM_FONT_LANGUAGES) FontFamily.Default else ArchivoFamily

/** Weights of the design: giant figures and buttons 900, titles 800, body 600–700. */
object AppWeights {
    val Giant = FontWeight.W900
    val Title = FontWeight.W800
    val Strong = FontWeight.W700
    val Body = FontWeight.W600
}

/** Material typography in the app font; nothing below 12 sp. */
fun appTypography(family: FontFamily): Typography {
    val base = Typography()
    fun TextStyle.with(weight: FontWeight) = copy(fontFamily = family, fontWeight = weight)
    return base.copy(
        displayLarge = base.displayLarge.with(AppWeights.Giant),
        displayMedium = base.displayMedium.with(AppWeights.Giant),
        displaySmall = base.displaySmall.with(AppWeights.Giant),
        headlineLarge = base.headlineLarge.with(AppWeights.Title),
        headlineMedium = base.headlineMedium.with(AppWeights.Title),
        headlineSmall = base.headlineSmall.with(AppWeights.Title),
        titleLarge = base.titleLarge.with(AppWeights.Title),
        titleMedium = base.titleMedium.with(AppWeights.Title),
        titleSmall = base.titleSmall.with(AppWeights.Strong),
        bodyLarge = base.bodyLarge.with(AppWeights.Body),
        bodyMedium = base.bodyMedium.with(AppWeights.Body),
        bodySmall = base.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp).with(AppWeights.Body),
        labelLarge = base.labelLarge.with(AppWeights.Strong),
        labelMedium = base.labelMedium.with(AppWeights.Strong),
        labelSmall = base.labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp).with(AppWeights.Strong),
    )
}
