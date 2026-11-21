package com.mrzekai.depoakilli.ui

import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.model.CleanupGroup
import com.mrzekai.depoakilli.model.isAppSelectable
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

    /**
     * Faz 5D (D1): [bulk] marks a selection the app makes on its own ("My pick",
     * "Select my picks"); it skips camera files and unverified look-alikes. A
     * "Select all" the user taps passes `bulk = false` and selects every file.
     */
    fun setItemsSelected(items: List<CleanableItem>, ids: Set<String>, selected: Boolean, bulk: Boolean): List<CleanableItem> =
        items.map { item ->
            val excluded = bulk && selected && !item.isAppSelectable()
            if (item.id in ids && item.selected != selected && !excluded) item.copy(selected = selected) else item
        }

    /**
     * What a "Select all" box does (D1): with none or some selected it selects
     * all; only when all are selected does it clear them.
     */
    fun selectAllTarget(items: List<CleanableItem>): Boolean = !selectAllCandidates(items).all(CleanableItem::selected)

    /** Files a "Select all" acts on: everything except look-alikes shown to be different. */
    fun selectAllCandidates(items: List<CleanableItem>): List<CleanableItem> = items.filterNot(CleanableItem::verifiedDifferent)

    /** A user "Select all" over [items] needs the full check first when it would select look-alikes. */
    fun needsVerification(items: List<CleanableItem>): Boolean =
        selectAllTarget(items) &&
            selectAllCandidates(items).any { it.assessment.group == CleanupGroup.DUPLICATE_UNVERIFIED && !it.selected }

    /**
     * "Select all" on look-alikes after the full check (D1): exact copies are
     * selected, a file that turned out different is marked and left alone,
     * and in every set the original stays unselected.
     */
    fun selectVerifiedCopies(items: List<CleanableItem>, results: Map<String, CleanableItem?>): List<CleanableItem> {
        val verifiedIds = results.filterValues { it != null }.keys
        val updated = items.map { current ->
            when {
                current.id !in results -> current
                else -> results[current.id]?.copy(selected = true)
                    ?: current.copy(verifiedDifferent = true, selected = false)
            }
        }
        val originals = updated.asSequence()
            .filter { it.selected && it.id in verifiedIds }
            .mapNotNull(CleanableItem::protectedDuplicateUri)
            .toSet()
        return updated.map { if (it.selected && it.uri in originals) it.copy(selected = false) else it }
    }

    /**
     * K3: how many of the checked files ended up selected as exact copies,
     * so "Select all" can always say in words what happened.
     */
    fun exactCopiesSelected(items: List<CleanableItem>, results: Map<String, CleanableItem?>): Int =
        items.count { it.id in results && results[it.id] != null && it.selected }

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
