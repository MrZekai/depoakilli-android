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

/** Faz 5D (D3): nothing changed in the last 30 days is suggested; documents need 180 days. */
const val SUGGEST_MIN_AGE_DAYS = 30L
const val SUGGEST_DOCUMENT_MIN_AGE_DAYS = 180L
private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

/** Days since the file last changed; an unknown date counts as old. */
fun CleanableItem.ageDays(nowMillis: Long = System.currentTimeMillis()): Long =
    if (modifiedAtMillis <= 0L) Long.MAX_VALUE else ((nowMillis - modifiedAtMillis).coerceAtLeast(0L) / DAY_MILLIS)

/** A video, song or photo saved in Downloads: the user's own, so asked about (D3). */
fun CleanableItem.isDownloadMedia(): Boolean =
    assessment.group == CleanupGroup.DOWNLOAD_FILE &&
        (mimeType.startsWith("video/") || mimeType.startsWith("audio/") || mimeType.startsWith("image/"))

/** Not recently changed: old enough for the app to suggest or select on its own (D3). */
fun CleanableItem.isOldEnoughToSuggest(nowMillis: Long = System.currentTimeMillis()): Boolean {
    val age = ageDays(nowMillis)
    val minimum = if (assessment.group == CleanupGroup.DOWNLOAD_DOCUMENT) SUGGEST_DOCUMENT_MIN_AGE_DAYS else SUGGEST_MIN_AGE_DAYS
    return age >= minimum
}

/**
 * Faz 5C/5D tier 2 ("My pick"), the one place its rule lives: a review file of
 * a suggested group, not in the camera folder, not a video/music/photo in
 * Downloads, not changed in the last 30 days (documents 180), not shown to be
 * different by a full check. Kept files and excluded folders never reach the
 * plan (UserChoiceFilter), so they are never suggested either.
 */
fun CleanableItem.isSuggestedPick(nowMillis: Long = System.currentTimeMillis()): Boolean =
    !assessment.group.auto &&
        assessment.group.suggested &&
        !StoragePathRules.isCameraPath(relativePath) &&
        !isDownloadMedia() &&
        !verifiedDifferent &&
        isOldEnoughToSuggest(nowMillis)

/** Tier 3 ("I'll ask you"): a review file the user decides on one by one. */
fun CleanableItem.isAskTier(nowMillis: Long = System.currentTimeMillis()): Boolean =
    !assessment.group.auto && !isSuggestedPick(nowMillis)

/**
 * Faz 5D (D1): a selection the app makes on its own ("My pick", "Select my
 * picks", preselection) never takes camera files or unverified look-alikes.
 * A "Select all" the user taps is not limited by this.
 */
fun CleanableItem.isAppSelectable(): Boolean =
    (assessment.group.auto || !StoragePathRules.isCameraPath(relativePath)) &&
        assessment.group != CleanupGroup.DUPLICATE_UNVERIFIED &&
        !verifiedDifferent

/** Files of a findings row in the swipe review: tier 3 only, biggest first (C2, C5). */
fun swipeItemsFor(group: CleanupGroup, items: List<CleanableItem>): List<CleanableItem> =
    items.filter { it.rowGroup == group && it.isAskTier() }
        .sortedByDescending(CleanableItem::sizeBytes)

/**
 * The findings row a group is listed under (C5): "Old videos" and "Large old
 * videos" are one row; the swipe review shows both, biggest first.
 */
val CleanupGroup.rowGroup: CleanupGroup
    get() = if (this == CleanupGroup.LARGE_VIDEO) CleanupGroup.OLD_VIDEO else this

/** The findings row of a file: as its group, with Downloads media in their own row (D3). */
val CleanableItem.rowGroup: CleanupGroup
    get() = if (isDownloadMedia()) CleanupGroup.DOWNLOAD_MEDIA else assessment.group.rowGroup

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

    /** Tier 1 files (preselected Auto groups), listed or not. */
    val autoItems: List<CleanableItem> get() = autoRows.flatMap(PlanGroup::items)

    /** Tier 2 ("My pick", C2): suggested together, confirmed once, always to Trash. */
    val suggestedItems: List<CleanableItem>
        get() = reviewRows.flatMap(PlanGroup::items).filter(CleanableItem::isSuggestedPick)
    val suggestedBytes: Long get() = suggestedItems.sumOf(CleanableItem::sizeBytes)

    /**
     * Tier 3 ("I'll ask you", C2): the review rows without the suggested files,
     * with "Old videos" and "Large old videos" in one row (C5), biggest first.
     */
    val askRows: List<PlanGroup>
        get() {
            val groupRows = reviewRows.filterNot(PlanGroup::smallReview)
                .flatMap(PlanGroup::items)
                .filter(CleanableItem::isAskTier)
                .groupBy { it.rowGroup }
                .map { (group, items) -> planGroup(group, items) }
            val small = smallReview?.let { row ->
                row.items.filter(CleanableItem::isAskTier).takeIf { it.isNotEmpty() }?.let { items ->
                    val sorted = items.sortedByDescending(CleanableItem::sizeBytes)
                    row.copy(
                        items = sorted,
                        bytes = sorted.sumOf(CleanableItem::sizeBytes),
                        selectedBytes = sorted.filter(CleanableItem::selected).sumOf(CleanableItem::sizeBytes),
                    )
                }
            }
            return (groupRows + listOfNotNull(small)).sortedByDescending(PlanGroup::bytes)
        }
    val askBytes: Long get() = askRows.sumOf(PlanGroup::bytes)
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

internal fun planGroup(group: CleanupGroup, items: List<CleanableItem>): PlanGroup {
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
