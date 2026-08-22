package com.mrzekai.depoakilli.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal object HomeVisualTokens {
    val PageTop = Color(0xFF071B5A)
    val PageMid = Color(0xFF06143A)
    val PageBottom = Color(0xFF030A1B)

    val Surface = Color(0xFF0C1834)
    val SurfaceElevated = Color(0xFF10203F)
    val SurfaceMuted = Color(0xFF0A1530)

    val TextPrimary = Color.White
    val TextSecondary = Color(0xFFB9C4DD)
    val TextMuted = Color(0xFF8794B1)

    val Cyan = Color(0xFF2DD8F3)
    val Teal = Color(0xFF2DE4B4)
    val Green = Color(0xFF20D875)
    val Blue = Color(0xFF1687FF)
    val Purple = Color(0xFF9C6BFF)
    val Amber = Color(0xFFFFB43C)

    val HeroBorder = Brush.linearGradient(
        listOf(
            Color(0xFF237BFF),
            Color(0xFF5436E8),
            Color(0xFF1CC7A1),
        ),
    )

    val HeroGradient = Brush.linearGradient(
        listOf(
            Color(0xFF103EB5),
            Color(0xFF27206F),
            Color(0xFF104B52),
        ),
    )

    val PrimaryGradient = Brush.horizontalGradient(
        listOf(
            Color(0xFF1269F7),
            Color(0xFF10BFD1),
            Color(0xFF24DA70),
        ),
    )

    val ExploreGradient = Brush.horizontalGradient(
        listOf(
            Color(0xFF102F86),
            Color(0xFF232178),
            Color(0xFF3A2EA8),
        ),
    )

    val PageGradient = Brush.verticalGradient(
        listOf(PageTop, PageMid, PageBottom),
    )

    val PageHorizontalPadding = 18.dp
    val SectionSpacing = 16.dp
    val CardRadius = 28.dp
    val CompactRadius = 20.dp
}
