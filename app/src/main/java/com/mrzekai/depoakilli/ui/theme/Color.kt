package com.mrzekai.depoakilli.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// "Make Room" design language (Faz 5). Lime pages, black cards, one accent.
// Every text/background pair below is checked for WCAG AA by ContrastTest.
// ---------------------------------------------------------------------------

/** Page ground (Home, Tools, Trash, Settings, result); accent on black surfaces. */
val Lime = Color(0xFF2BE878)

/** Main text and icons on Lime. */
val Ink = Color(0xFF04210F)

/** Secondary text on Lime. */
val InkSoft = Color(0xFF0B4A25)

/** Cards, bottom menu, bottom sheets and the giant button. */
val Black = Color(0xFF07090C)

/** Dividers on black surfaces. */
val BlackLine = Color(0xFF1B2026)

/** Icon ground and the selected-tab pill on black surfaces. */
val BlackChip = Color(0xFF1F2A22)

/** Main text on black surfaces. */
val OnBlack = Color(0xFFF7F9FF)

/** Secondary text on black surfaces. */
val OnBlackSoft = Color(0xFF9AA3B2)

/** Only for deletes that cannot be undone. */
val Coral = Color(0xFFFF6B5E)

/** Text on Coral. */
val CoralInk = Color(0xFF2A0703)

/** Ground of the "Ad" label. */
val AdLabel = Color(0xFFFFB21A)

// Chart colours (Storage analysis only) are defined next to their chart in
// StorageAnalyzerScreen.kt; no other screen uses colours outside this file.

/** Selected plan on the paywall (Faz 2). */
val PlanSelected = Color(0xFF12161C)

/** Lines and plan borders on black (Faz 2 paywall). */
val CardLine = Color(0xFF2A313B)
