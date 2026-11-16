package com.mrzekai.depoakilli.ui

import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.model.CleanupGroup

/** Pure selection rules of the cleanup plan, shared by the ViewModel and tests. */
internal object PlanSelection {
    /** Selects or clears a whole group; groups that are not group-selectable are left unchanged. */
    fun setGroupSelected(items: List<CleanableItem>, group: CleanupGroup, selected: Boolean): List<CleanableItem> {
        if (!group.groupSelectable) return items
        return items.map { item ->
            if (item.assessment.group == group && item.selected != selected) item.copy(selected = selected) else item
        }
    }

    fun toggleGroup(items: List<CleanableItem>, group: CleanupGroup): List<CleanableItem> =
        setGroupSelected(items, group, items.any { it.assessment.group == group && !it.selected })

    /** Replaces a verified copy, or drops it when the full check showed it is different. */
    fun applyVerification(items: List<CleanableItem>, id: String, verified: CleanableItem?): List<CleanableItem> =
        items.mapNotNull { current ->
            when {
                current.id != id -> current
                verified == null -> null
                else -> verified.copy(selected = current.selected)
            }
        }
}
