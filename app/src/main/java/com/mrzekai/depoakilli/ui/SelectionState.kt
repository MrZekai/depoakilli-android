package com.mrzekai.depoakilli.ui

import androidx.compose.ui.state.ToggleableState

/**
 * Section checkbox state derived from the items inside the section.
 *
 * A section whose items are only partly selected shows the indeterminate
 * state instead of an empty box, so a selection inside a collapsed section is
 * never hidden from the user (QA v43 #01, #14).
 */
internal fun sectionSelectionState(selectedCount: Int, totalCount: Int): ToggleableState = when {
    totalCount <= 0 || selectedCount <= 0 -> ToggleableState.Off
    selectedCount >= totalCount -> ToggleableState.On
    else -> ToggleableState.Indeterminate
}
