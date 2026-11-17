package com.mrzekai.depoakilli.ui

import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.model.MemorySnapshot
import com.mrzekai.depoakilli.model.StorageSnapshot

/**
 * Pure state transitions used by [CleanerViewModel] (T3.5). Kept free of
 * Android types so the counters and flags are covered by plain unit tests.
 */
internal object CleanerStateReducers {
    /** Clears every running-scan flag; the cancelled job reports nothing. */
    fun scanCancelled(state: CleanerUiState): CleanerUiState = state.copy(
        scanning = false,
        dashboardRefreshing = false,
        scanHashing = false,
        message = null,
    )

    /** A failed review scan must not leave the spinner running. */
    fun storageReviewFailed(state: CleanerUiState, message: String): CleanerUiState = state.copy(
        storageReview = state.storageReview.copy(loading = false),
        message = message,
    )

    /** Removes deleted items and lowers the dashboard totals by what was freed. */
    fun afterCleanup(
        state: CleanerUiState,
        items: List<CleanableItem>,
        deletedIds: Set<String>,
        storageAfter: StorageSnapshot,
        memoryAfter: MemorySnapshot,
        ownCacheAfter: Long,
        result: CleanupResult,
    ): CleanerUiState {
        val deletedItems = items.filter { it.id in deletedIds }
        val deletedByCategory = deletedItems
            .groupBy { it.assessment.category }
            .mapValues { (_, categoryItems) -> categoryItems.sumOf(CleanableItem::sizeBytes) }
        val deletedAutoSelectedBytes = deletedItems
            .asSequence()
            .filter { it.assessment.recommended }
            .sumOf(CleanableItem::sizeBytes)
        val deletedReviewBytes = deletedItems
            .asSequence()
            .filterNot { it.assessment.recommended }
            .sumOf(CleanableItem::sizeBytes)
        val removedUris = deletedItems.mapTo(hashSetOf(), CleanableItem::uri)
        return state.copy(
            summary = state.summary.copy(items = state.summary.items.filterNot { it.id in deletedIds }),
            planSummary = state.planSummary?.let { plan ->
                plan.copy(items = plan.items.filterNot { it.uri in removedUris })
            },
            dashboardCleanableBytes = (state.dashboardCleanableBytes - deletedAutoSelectedBytes).coerceAtLeast(0L),
            dashboardReviewBytes = (state.dashboardReviewBytes - deletedReviewBytes).coerceAtLeast(0L),
            dashboardCategoryBytes = state.dashboardCategoryBytes.mapValues { (category, bytes) ->
                (bytes - (deletedByCategory[category] ?: 0L)).coerceAtLeast(0L)
            },
            storage = storageAfter,
            memory = memoryAfter,
            ownCacheBytes = ownCacheAfter,
            cleanupInProgress = false,
            cleanupResult = result,
            message = null,
        )
    }
}
