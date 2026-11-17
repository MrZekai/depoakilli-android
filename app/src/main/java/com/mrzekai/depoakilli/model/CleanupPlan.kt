package com.mrzekai.depoakilli.model

import com.mrzekai.depoakilli.data.CleaningThresholds
import com.mrzekai.depoakilli.data.StoragePathRules

/** One row of the cleanup plan. [group] is null only for the merged small Auto row. */
/**
 * Whether "select all" style actions may select [this] (T2.4, B3): never items
 * of groups that are not group-selectable, and never review items from the
 * camera folder; those are chosen one by one or in the swipe review.
 */
fun CleanableItem.isBulkSelectable(): Boolean =
    assessment.group.groupSelectable &&
        (assessment.group.auto || !StoragePathRules.isCameraPath(relativePath))

data class PlanGroup(
    val group: CleanupGroup?,
    val items: List<CleanableItem>,
    val bytes: Long,
    val selectedBytes: Long,
    val auto: Boolean,
    val deleteMode: DeleteMode,
    val groupSelectable: Boolean,
    /** Merged review groups each smaller than [CleaningThresholds.GROUP_MIN_BYTES] (V7). */
    val smallReview: Boolean = false,
) {
    val selectedCount: Int get() = items.count(CleanableItem::selected)
    val allSelected: Boolean get() = items.isNotEmpty() && items.all(CleanableItem::selected)

    /** Camera items a group checkbox leaves out (B3). */
    val oneByOneCount: Int get() = if (auto) 0 else items.count { StoragePathRules.isCameraPath(it.relativePath) }

    /** Checkbox state: every item the group checkbox can select is selected. */
    val allBulkSelected: Boolean get() = items.filter(CleanableItem::isBulkSelectable).let { bulk ->
        bulk.isNotEmpty() && bulk.all(CleanableItem::selected)
    }
}

data class CleanupPlan(
    val included: List<PlanGroup> = emptyList(),
    val review: List<PlanGroup> = emptyList(),
    val smallItems: PlanGroup? = null,
    val truncated: Boolean = false,
    /** Review groups too small for their own row, merged into one "Smaller groups" row. */
    val smallReview: PlanGroup? = null,
) {
    private val autoRows: List<PlanGroup> get() = included + listOfNotNull(smallItems)
    private val reviewRows: List<PlanGroup> get() = review + listOfNotNull(smallReview)
    private val allRows: List<PlanGroup> get() = autoRows + reviewRows

    val recommendedBytes: Long get() = autoRows.sumOf(PlanGroup::bytes)

    /** Every review group, small ones included (V7). */
    val reviewBytes: Long get() = reviewRows.sumOf(PlanGroup::bytes)

    /**
     * "Up to X can be freed": only listed, deletable files. App cache is never
     * part of it (it is not measured before Android clears it).
     */
    val potentialBytes: Long get() = recommendedBytes + reviewBytes

    /** Row the "Review" button opens when nothing is preselected. */
    val firstReviewRow: PlanGroup? get() = review.firstOrNull() ?: smallReview
    val recommendedGroupCount: Int get() = autoRows.size
    val selectedBytes: Long get() = allRows.sumOf(PlanGroup::selectedBytes)
    val selectedGroupCount: Int get() = allRows.count { it.selectedBytes > 0L || it.selectedCount > 0 }
    val selectedItems: List<CleanableItem> get() = allRows.flatMap { row -> row.items.filter(CleanableItem::selected) }
    val isAlreadyTidy: Boolean get() = potentialBytes < CleaningThresholds.PLAN_MIN_BYTES
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
    val (bigReview, smallReviewRows) = reviewRows.partition { it.bytes >= CleaningThresholds.GROUP_MIN_BYTES }
    val smallReview = smallReviewRows.takeIf { it.isNotEmpty() }?.let { rowsToMerge ->
        val merged = rowsToMerge.flatMap(PlanGroup::items).sortedByDescending(CleanableItem::sizeBytes)
        PlanGroup(
            group = null,
            items = merged,
            bytes = merged.sumOf(CleanableItem::sizeBytes),
            selectedBytes = merged.filter(CleanableItem::selected).sumOf(CleanableItem::sizeBytes),
            auto = false,
            deleteMode = if (rowsToMerge.all { it.deleteMode == DeleteMode.PERMANENT }) DeleteMode.PERMANENT else DeleteMode.TRASH,
            // Mixed groups (documents, look-alikes): never selected as a whole.
            groupSelectable = false,
            smallReview = true,
        )
    }
    return CleanupPlan(
        included = bigAuto,
        review = bigReview,
        smallItems = small,
        truncated = truncated,
        smallReview = smallReview,
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
