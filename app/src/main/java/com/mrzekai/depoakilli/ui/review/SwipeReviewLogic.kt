package com.mrzekai.depoakilli.ui.review

import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.model.CleanupGroup

/**
 * Pure state of a swipe review (T1.11). Swiping only marks files; nothing is
 * deleted until the user confirms the preview sheet after "Finish".
 */
data class SwipeReviewState(
    val itemIds: List<String>,
    val sizes: Map<String, Long>,
    val decisions: List<Decision> = emptyList(),
    /** Number of break cards the user has passed with "Continue". */
    val breaksPassed: Int = 0,
) {
    data class Decision(val id: String, val delete: Boolean)

    val reviewedCount: Int get() = decisions.size
    val total: Int get() = itemIds.size
    val finished: Boolean get() = decisions.size >= itemIds.size

    /** A break card is due after every [SWIPE_BREAK_EVERY] decisions while items remain. */
    val breakPending: Boolean
        get() = decisions.isNotEmpty() &&
            decisions.size % SWIPE_BREAK_EVERY == 0 &&
            decisions.size < itemIds.size &&
            breaksPassed < decisions.size / SWIPE_BREAK_EVERY

    val currentId: String? get() = if (finished || breakPending) null else itemIds[decisions.size]
    val markedIds: Set<String> get() = decisions.asSequence().filter(Decision::delete).mapTo(linkedSetOf(), Decision::id)
    val markedBytes: Long get() = markedIds.sumOf { sizes[it] ?: 0L }

    fun mark(delete: Boolean): SwipeReviewState {
        val id = currentId ?: return this
        return copy(decisions = decisions + Decision(id, delete))
    }

    fun undo(): SwipeReviewState {
        if (decisions.isEmpty()) return this
        val remaining = decisions.dropLast(1)
        return copy(decisions = remaining, breaksPassed = minOf(breaksPassed, remaining.size / SWIPE_BREAK_EVERY))
    }

    fun continueAfterBreak(): SwipeReviewState =
        if (breakPending) copy(breaksPassed = decisions.size / SWIPE_BREAK_EVERY) else this

    companion object {
        const val SWIPE_BREAK_EVERY = 8

        /** Groups whose plan row opens the swipe review by default. */
        val SWIPE_GROUPS = setOf(
            CleanupGroup.LARGE_VIDEO,
            CleanupGroup.SCREEN_RECORDING,
            CleanupGroup.SCREENSHOT,
            CleanupGroup.WHATSAPP_VIDEO,
            CleanupGroup.WHATSAPP_SENT,
            CleanupGroup.WHATSAPP_OLD_VIDEO,
            CleanupGroup.WHATSAPP_OLD_IMAGE,
            CleanupGroup.DOWNLOAD_FILE,
        )

        fun start(items: List<CleanableItem>): SwipeReviewState {
            val sorted = items.sortedByDescending(CleanableItem::sizeBytes)
            return SwipeReviewState(
                itemIds = sorted.map(CleanableItem::id),
                sizes = sorted.associate { it.id to it.sizeBytes },
            )
        }

        /** Positions (number of decisions made) at which a break card appears. */
        fun breakPositions(total: Int): List<Int> =
            (SWIPE_BREAK_EVERY until total step SWIPE_BREAK_EVERY).toList()
    }
}
