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
) {
    data class Decision(val id: String, val delete: Boolean)

    val reviewedCount: Int get() = decisions.size
    val total: Int get() = itemIds.size
    val finished: Boolean get() = decisions.size >= itemIds.size

    val currentId: String? get() = if (finished) null else itemIds[decisions.size]
    val markedIds: Set<String> get() = decisions.asSequence().filter(Decision::delete).mapTo(linkedSetOf(), Decision::id)
    val markedBytes: Long get() = markedIds.sumOf { sizes[it] ?: 0L }

    fun mark(delete: Boolean): SwipeReviewState {
        val id = currentId ?: return this
        return copy(decisions = decisions + Decision(id, delete))
    }

    fun undo(): SwipeReviewState {
        if (decisions.isEmpty()) return this
        return copy(decisions = decisions.dropLast(1))
    }

    companion object {
        /** Groups whose plan row opens the swipe review by default. */
        val SWIPE_GROUPS = setOf(
            CleanupGroup.LARGE_VIDEO,
            CleanupGroup.OLD_VIDEO,
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
    }
}
