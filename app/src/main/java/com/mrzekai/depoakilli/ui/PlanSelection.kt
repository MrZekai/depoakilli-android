package com.mrzekai.depoakilli.ui

import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.model.CleanupGroup
import com.mrzekai.depoakilli.model.isBulkSelectable

/** Pure selection rules of the cleanup plan, shared by the ViewModel and tests. */
internal object PlanSelection {
    /**
     * Selects or clears a whole group; groups that are not group-selectable are
     * left unchanged and camera review items are never selected this way (B3).
     */
    fun setGroupSelected(items: List<CleanableItem>, group: CleanupGroup, selected: Boolean): List<CleanableItem> {
        if (!group.groupSelectable) return items
        return items.map { item ->
            val excluded = selected && !item.isBulkSelectable()
            if (item.assessment.group == group && item.selected != selected && !excluded) item.copy(selected = selected) else item
        }
    }

    /** Bulk selection skips items that are not bulk-selectable; explicit selection does not. */
    fun setItemsSelected(items: List<CleanableItem>, ids: Set<String>, selected: Boolean, bulk: Boolean): List<CleanableItem> =
        items.map { item ->
            val excluded = bulk && selected && !item.isBulkSelectable()
            if (item.id in ids && item.selected != selected && !excluded) item.copy(selected = selected) else item
        }

    fun toggleGroup(items: List<CleanableItem>, group: CleanupGroup): List<CleanableItem> =
        setGroupSelected(
            items,
            group,
            items.any { it.assessment.group == group && it.isBulkSelectable() && !it.selected },
        )

    /**
     * Large look-alike groups the "Deep duplicate check" can verify:
     * group count and the most space they could free (size × extra copies).
     */
    fun deepCheckCandidates(items: List<CleanableItem>): Pair<Int, Long> {
        val groups = items.asSequence()
            .filter { it.assessment.group == CleanupGroup.DUPLICATE_UNVERIFIED }
            .filter { it.sizeBytes > com.mrzekai.depoakilli.data.CleaningThresholds.QUICK_FULL_HASH_BYTES }
            .filter { it.protectedDuplicateUri != null }
            .groupBy { it.protectedDuplicateUri }
        return groups.size to groups.values.sumOf { copies -> copies.sumOf(CleanableItem::sizeBytes) }
    }

    /** Applies a batch of verification results (null = not an exact copy). */
    fun applyVerifications(items: List<CleanableItem>, results: Map<String, CleanableItem?>): List<CleanableItem> =
        items.mapNotNull { current ->
            if (current.id !in results) current else results[current.id]?.copy(selected = current.selected)
        }

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
