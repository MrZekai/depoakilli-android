package com.mrzekai.depoakilli.model

import com.mrzekai.depoakilli.data.CleaningThresholds

/** One row of the cleanup plan. [group] is null only for the merged small Auto row. */
data class PlanGroup(
    val group: CleanupGroup?,
    val items: List<CleanableItem>,
    val bytes: Long,
    val selectedBytes: Long,
    val auto: Boolean,
    val deleteMode: DeleteMode,
    val groupSelectable: Boolean,
) {
    val selectedCount: Int get() = items.count(CleanableItem::selected)
    val allSelected: Boolean get() = items.isNotEmpty() && items.all(CleanableItem::selected)
}

data class CleanupPlan(
    val included: List<PlanGroup> = emptyList(),
    val review: List<PlanGroup> = emptyList(),
    val smallItems: PlanGroup? = null,
    val truncated: Boolean = false,
) {
    private val autoRows: List<PlanGroup> get() = included + listOfNotNull(smallItems)
    private val allRows: List<PlanGroup> get() = autoRows + review

    val recommendedBytes: Long get() = autoRows.sumOf(PlanGroup::bytes)
    val recommendedGroupCount: Int get() = autoRows.size
    val selectedBytes: Long get() = allRows.sumOf(PlanGroup::selectedBytes)
    val selectedGroupCount: Int get() = allRows.count { it.selectedBytes > 0L || it.selectedCount > 0 }
    val selectedItems: List<CleanableItem> get() = allRows.flatMap { row -> row.items.filter(CleanableItem::selected) }
    val isAlreadyTidy: Boolean get() = recommendedBytes < CleaningThresholds.PLAN_MIN_BYTES
}

/**
 * Pure plan builder: groups by [CleanupGroup], largest first (items inside a
 * group largest first). Auto groups under [CleaningThresholds.GROUP_MIN_BYTES]
 * are merged into one "small items" row; small Review groups are left to the
 * tool screens.
 */
fun buildPlan(items: List<CleanableItem>, truncated: Boolean = false): CleanupPlan {
    val rows = items.groupBy { it.assessment.group }
        .map { (group, groupItems) -> planGroup(group, groupItems) }
        .sortedByDescending(PlanGroup::bytes)
    val (autoRows, reviewRows) = rows.partition(PlanGroup::auto)
    val (bigAuto, smallAuto) = autoRows.partition { it.bytes >= CleaningThresholds.GROUP_MIN_BYTES }
    val small = smallAuto.takeIf { it.isNotEmpty() }?.let { rowsToMerge ->
        val merged = rowsToMerge.flatMap(PlanGroup::items).sortedByDescending(CleanableItem::sizeBytes)
        PlanGroup(
            group = null,
            items = merged,
            bytes = merged.sumOf(CleanableItem::sizeBytes),
            selectedBytes = merged.filter(CleanableItem::selected).sumOf(CleanableItem::sizeBytes),
            auto = true,
            deleteMode = if (rowsToMerge.all { it.deleteMode == DeleteMode.PERMANENT }) DeleteMode.PERMANENT else DeleteMode.TRASH,
            groupSelectable = true,
        )
    }
    return CleanupPlan(
        included = bigAuto,
        review = reviewRows.filter { it.bytes >= CleaningThresholds.GROUP_MIN_BYTES },
        smallItems = small,
        truncated = truncated,
    )
}

private fun planGroup(group: CleanupGroup, items: List<CleanableItem>): PlanGroup {
    val sorted = items.sortedByDescending(CleanableItem::sizeBytes)
    return PlanGroup(
        group = group,
        items = sorted,
        bytes = sorted.sumOf(CleanableItem::sizeBytes),
        selectedBytes = sorted.filter(CleanableItem::selected).sumOf(CleanableItem::sizeBytes),
        auto = group.auto,
        deleteMode = group.deleteMode,
        groupSelectable = group.groupSelectable,
    )
}
