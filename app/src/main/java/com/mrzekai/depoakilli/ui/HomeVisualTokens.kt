package com.mrzekai.depoakilli.ui

import androidx.compose.ui.unit.dp
import com.mrzekai.depoakilli.ui.theme.Black
import com.mrzekai.depoakilli.ui.theme.BlackChip
import com.mrzekai.depoakilli.ui.theme.BlackLine
import com.mrzekai.depoakilli.ui.theme.Ink
import com.mrzekai.depoakilli.ui.theme.InkSoft
import com.mrzekai.depoakilli.ui.theme.Lime
import com.mrzekai.depoakilli.ui.theme.OnBlack
import com.mrzekai.depoakilli.ui.theme.OnBlackSoft

/**
 * Page-level tokens of the "Make Room" design (Faz 5): Lime pages, black
 * surfaces, one accent. No gradients and no navy tones anywhere.
 */
internal object HomeVisualTokens {
    val Page = Lime
    val OnPage = Ink
    val OnPageSoft = InkSoft

    val Surface = Black
    val SurfaceElevated = BlackChip
    val Divider = BlackLine

    val TextPrimary = OnBlack
    val TextSecondary = OnBlackSoft

    val Accent = Lime

    val PageHorizontalPadding = 16.dp
    val SectionSpacing = 14.dp
    val CardRadius = 24.dp
    val CompactRadius = 20.dp
}
