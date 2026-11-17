package com.mrzekai.depoakilli.ui

import androidx.compose.ui.graphics.Color
import com.mrzekai.depoakilli.ui.theme.AdLabel
import com.mrzekai.depoakilli.ui.theme.Black
import com.mrzekai.depoakilli.ui.theme.BlackChip
import com.mrzekai.depoakilli.ui.theme.Coral
import com.mrzekai.depoakilli.ui.theme.CoralInk
import com.mrzekai.depoakilli.ui.theme.Ink
import com.mrzekai.depoakilli.ui.theme.InkSoft
import com.mrzekai.depoakilli.ui.theme.Lime
import com.mrzekai.depoakilli.ui.theme.OnBlack
import com.mrzekai.depoakilli.ui.theme.OnBlackSoft

/**
 * Text/background pairs of the Faz 5 design. A validator check forbids white
 * content on `ButtonDefaults.buttonColors`, so dark-on-bright buttons use
 * [OnBrightAction]. Every pair below is checked for WCAG AA (≥ 4.5:1) by
 * ContrastTest.
 */
internal object ContrastTokens {
    /** Text on Lime and on the "Ad" label. */
    val OnBrightAction = Ink

    /** Every bright background an action or label can use. */
    val BrightActionBackgrounds = listOf(Lime, AdLabel)

    /** (text, background) pairs of the design (section 1 of the Faz 5 spec). */
    val DesignPairs: List<Pair<Color, Color>> = listOf(
        Ink to Lime,
        InkSoft to Lime,
        OnBlack to Black,
        OnBlackSoft to Black,
        Lime to Black,
        CoralInk to Coral,
    )

    /** A disabled action (B3): soft text on a dark chip, on any page. */
    val DisabledPair: Pair<Color, Color> = OnBlackSoft to BlackChip

    /**
     * Pairs the design never uses (B3): white text on Lime is unreadable, so
     * text placed straight on a Lime page is Ink. ContrastTest checks the
     * sources for it.
     */
    val ForbiddenPairs: List<Pair<Color, Color>> = listOf(OnBlack to Lime)

    /** WCAG 2.x relative-luminance contrast ratio. */
    fun contrastRatio(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val (light, dark) = if (la >= lb) la to lb else lb to la
        return (light + 0.05) / (dark + 0.05)
    }

    private fun luminance(color: Color): Double {
        fun channel(c: Float): Double {
            val v = c.toDouble()
            return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }
}
