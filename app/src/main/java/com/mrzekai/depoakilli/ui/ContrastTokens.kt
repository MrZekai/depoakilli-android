package com.mrzekai.depoakilli.ui

import androidx.compose.ui.graphics.Color

/**
 * Text/icon colour on bright action buttons (green, cyan, purple, amber
 * gradients, destructive red). A validator check forbids white content on
 * `ButtonDefaults.buttonColors`, so new buttons must use this token. Every pair below is checked for WCAG AA (≥4.5:1) by ContrastTest.
 */
internal object ContrastTokens {
    val OnBrightAction = Color(0xFF03122B)

    /** Every bright background a primary action button can use. */
    val BrightActionBackgrounds = listOf(
        Color(0xFF0DCB72), // WhatsApp clean gradient start
        Color(0xFF19DCCB), // WhatsApp cyan
        Color(0xFF16D86A), // WhatsApp clean gradient end
        Color(0xFF28E58A), // plan accent
        Color(0xFF16C7FF), // smart cyan
        Color(0xFF20C8FF), // tool cyan
        Color(0xFF24E58A), // tool green
        Color(0xFF9A63FF), // tool purple
        Color(0xFFFFB21A), // tool amber
        Color(0xFFDA57FF), // tool magenta
        Color(0xFFFF5D68), // destructive red (Swipe "Delete", Trash "Empty")
    )

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
