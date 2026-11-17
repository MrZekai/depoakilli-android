package com.mrzekai.depoakilli.ui

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.data.DeviceRepository
import com.mrzekai.depoakilli.data.ExcludedFolder
import com.mrzekai.depoakilli.diagnostics.AppDiagnostics
import com.mrzekai.depoakilli.model.CleanCategory
import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.model.CleanupGroup
import com.mrzekai.depoakilli.model.isBulkSelectable
import com.mrzekai.depoakilli.model.swipeItemsFor
import com.mrzekai.depoakilli.ui.review.SwipeReviewState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CleanerViewModel internal constructor(
    application: Application,
    repository: DeviceRepository,
) : CleanerStorageReviewLayer(application, repository) {
    /** Used by the default ViewModel factory. Tests pass a fake repository. */
    constructor(application: Application) : this(application, DeviceRepository(application))

    init {
        restoreSavedState()
        refreshDeviceState()
        refreshAppCaches()
        purgeExpiredTrash()
        refreshUserChoices()
    }

    /** Loads the counts behind V10/V11 for Settings and the plan note. */
    fun refreshUserChoices() {
        viewModelScope.launch {
            val (kept, folders) = withContext(Dispatchers.IO) {
                repository.keptItemCount() to repository.excludedFolderList()
            }
            _state.update { it.copy(keptItemCount = kept, excludedFolders = folders) }
        }
    }

    /** "Suggest them again": forget every keep decision; the next scan shows them again. */
    fun clearKeptItems() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.clearKeptItems() }
            refreshUserChoices()
        }
    }

    /**
     * "Don't suggest files from this folder" (V11): stored, and the folder's
     * review items leave the current plan right away.
     */
    fun excludeFolder(volumeRoot: String, relativePath: String) {
        val folder = ExcludedFolder(volumeRoot, ExcludedFolder.normalize(relativePath))
        // A file at the volume root has no folder to exclude; the button is hidden there too.
        if (!folder.isValid) return
        viewModelScope.launch {
            val added = withContext(Dispatchers.IO) { repository.excludeFolder(folder) }
            if (!added) {
                _state.update { it.copy(message = getApplication<Application>().getString(R.string.message_excluded_folders_full)) }
                return@launch
            }
            _state.update { state ->
                val keep: (CleanableItem) -> Boolean = { item ->
                    item.assessment.group.auto || !folder.contains(item.volumeRoot, item.relativePath)
                }
                state.copy(
                    summary = state.summary.copy(items = state.summary.items.filter(keep)),
                    planSummary = state.planSummary?.let { plan -> plan.copy(items = plan.items.filter(keep)) },
                    message = getApplication<Application>().getString(R.string.message_folder_excluded, folder.name),
                )
            }
            refreshUserChoices()
        }
    }

    fun includeFolder(folder: ExcludedFolder) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.includeFolder(folder) }
            refreshUserChoices()
        }
    }

    fun toggleItem(id: String) {
        _state.update { state ->
            state.copy(
                summary = state.summary.copy(
                    items = state.summary.items.map { item ->
                        if (item.id == id) item.copy(selected = !item.selected) else item
                    },
                ),
            )
        }
    }

    fun toggleCategory(category: CleanCategory) {
        _state.update { state ->
            val categoryItems = state.summary.items.filter { it.assessment.category == category }
            val select = categoryItems.any { it.isBulkSelectable() && !it.selected }
            state.copy(
                summary = state.summary.copy(
                    items = state.summary.items.map { item ->
                        val excluded = select && !item.isBulkSelectable()
                        if (item.assessment.category == category && !excluded) item.copy(selected = select) else item
                    },
                ),
            )
        }
    }

    /** Selects or clears a whole plan group. Groups that are not group-selectable are ignored. */
    fun setGroupSelected(group: CleanupGroup, selected: Boolean) {
        _state.update { state ->
            state.copy(summary = state.summary.copy(items = PlanSelection.setGroupSelected(state.summary.items, group, selected)))
        }
    }

    fun toggleGroup(group: CleanupGroup) {
        _state.update { state ->
            state.copy(summary = state.summary.copy(items = PlanSelection.toggleGroup(state.summary.items, group)))
        }
    }

    /**
     * Second, explicit confirmation path: files that could not be moved to
     * Trash are deleted permanently only after the user confirmed that.
     */
    fun deleteTrashFallbackPermanently() {
        val fallback = _state.value.trashFallback ?: return
        _state.update { it.copy(trashFallback = null) }
        if (_state.value.cleanupInProgress) return
        val ids = fallback.ids
        when (fallback.source) {
            TrashFallback.Source.WHATSAPP -> {
                val items = _state.value.whatsAppSummary.items.filter { it.id in ids }
                if (items.isNotEmpty()) runWhatsAppDeletion(items, forcePermanent = true, onCompleted = {})
                return
            }
            TrashFallback.Source.STORAGE_REVIEW -> {
                val items = _state.value.storageReview.items.filter { it.id in ids }
                if (items.isNotEmpty()) runStorageReviewDeletion(items, forcePermanent = true, onCleanupCompleted = {})
                return
            }
            TrashFallback.Source.PLAN -> Unit
        }
        val items = _state.value.summary.items.filter { it.id in ids }
        if (items.isEmpty() || _state.value.cleanupInProgress) return
        _state.update { it.copy(cleanupInProgress = true, cleanupResult = null) }
        viewModelScope.launch {
            val before = ioAvailableBytes()
            runSuspendCatching { repository.deleteDirectItems(items, forcePermanent = true) }
                .onSuccess { direct ->
                    finishCleanup(
                        items = items,
                        deletedIds = direct.deletedIds,
                        deletedBytes = direct.deletedBytes,
                        failedCount = direct.failedCount,
                        storageBeforeBytes = before,
                        changedCount = direct.changedCount,
                    )
                }
                .onFailure {
                    _state.update { state ->
                        state.copy(
                            cleanupInProgress = false,
                            message = getApplication<Application>().getString(R.string.message_cleanup_failed),
                        )
                    }
                }
        }
    }

    /** Opens the swipe review for a plan group, keeping earlier marks for the same files. */
    fun openSwipeReview(group: CleanupGroup) {
        _state.update { state ->
            val items = swipeItemsFor(group, state.summary.items)
            val fresh = SwipeReviewState.start(items)
            val previous = state.swipeStates[group]
            val review = if (previous != null && previous.itemIds == fresh.itemIds) previous else fresh
            state.copy(
                swipeReviewGroup = group,
                swipeStates = state.swipeStates + (group to review),
                smartCategoryReview = null,
                smartCategoryReviewIds = null,
                smartCategoryReviewGroup = null,
            )
        }
    }

    /** "Keep" is also remembered across scans (V10); "Delete" only marks. */
    fun swipeMark(delete: Boolean) {
        val state = _state.value
        val currentId = state.swipeReviewGroup?.let(state.swipeStates::get)?.currentId
        updateSwipe { it.mark(delete) }
        if (!delete && currentId != null) {
            val item = state.summary.items.firstOrNull { it.id == currentId } ?: return
            viewModelScope.launch {
                withContext(Dispatchers.IO) { repository.keepItem(item) }
                refreshUserChoices()
            }
        }
    }

    /** Undoing a "Keep" also forgets it (V10). */
    fun swipeUndo() {
        val state = _state.value
        val last = state.swipeReviewGroup?.let(state.swipeStates::get)?.decisions?.lastOrNull()
        updateSwipe { it.undo() }
        if (last != null && !last.delete) {
            val item = state.summary.items.firstOrNull { it.id == last.id } ?: return
            viewModelScope.launch {
                withContext(Dispatchers.IO) { repository.unkeepItem(item) }
                refreshUserChoices()
            }
        }
    }

    fun swipeContinue() = updateSwipe { it.continueAfterBreak() }

    /** Leaves the swipe review; marks stay unless [clearMarks] is set (after cleaning). */
    fun closeSwipeReview(clearMarks: Boolean = false) {
        _state.update { state ->
            val group = state.swipeReviewGroup
            state.copy(
                swipeReviewGroup = null,
                swipeStates = if (clearMarks && group != null) state.swipeStates - group else state.swipeStates,
            )
        }
    }

    /** Switches from the swipe review to the grid of the same group. */
    fun switchSwipeToGrid() {
        val state = _state.value
        val group = state.swipeReviewGroup ?: return
        val items = state.summary.items.filter { it.assessment.group == group }
        closeSwipeReview()
        if (items.isEmpty()) return
        openSmartCategoryReview(items.first().assessment.category, items.mapTo(linkedSetOf(), CleanableItem::id))
        _state.update { it.copy(smartCategoryReviewGroup = group) }
    }

    /** Grid review opened from a plan row; remembers the group for the swipe toggle. */
    fun openPlanGroupGrid(group: CleanupGroup?, items: List<CleanableItem>) {
        if (items.isEmpty()) return
        openSmartCategoryReview(items.first().assessment.category, items.mapTo(linkedSetOf(), CleanableItem::id))
        _state.update { it.copy(smartCategoryReviewGroup = group) }
    }

    private fun updateSwipe(change: (SwipeReviewState) -> SwipeReviewState) {
        _state.update { state ->
            val group = state.swipeReviewGroup ?: return@update state
            val current = state.swipeStates[group] ?: return@update state
            state.copy(swipeStates = state.swipeStates + (group to change(current)))
        }
    }

    /**
     * [bulk] is true for "select all" style actions; those never select items
     * from groups that are not group-selectable (personal documents, look-alike
     * copies). Explicit choices (swipe confirmation) pass false (T2.4).
     */
    fun setItemsSelected(ids: Set<String>, selected: Boolean, bulk: Boolean) {
        if (ids.isEmpty()) return
        _state.update { state ->
            state.copy(summary = state.summary.copy(items = PlanSelection.setItemsSelected(state.summary.items, ids, selected, bulk)))
        }
    }

    fun prepareCleanup(
        itemIds: Set<String>? = null,
        onCleanupCompleted: (Boolean) -> Unit,
    ) {
        if (_state.value.cleanupInProgress) return
        val selected = if (itemIds == null) {
            _state.value.summary.selectedItems
        } else {
            _state.value.summary.items.filter { it.id in itemIds && it.selected }
        }
        if (selected.isEmpty()) {
            _state.update { it.copy(message = getApplication<Application>().getString(R.string.message_select_item)) }
            return
        }
        AppDiagnostics.breadcrumb(
            "cleanup_confirmed",
            mapOf(
                "items" to selected.size,
                "bytes" to selected.sumOf(CleanableItem::sizeBytes),
            ),
        )
        _state.update {
            it.copy(
                cleanupInProgress = true,
                cleanupResult = null,
                message = null,
            )
        }
        viewModelScope.launch {
            val storageBefore = ioAvailableBytes()
            runSuspendCatching {
                // Every item is a shared-storage file path; the old MediaStore
                // consent request was never reached and is removed (T3.1).
                val direct = repository.deleteDirectItems(selected)
                finishCleanup(
                    items = selected,
                    deletedIds = direct.deletedIds,
                    deletedBytes = direct.deletedBytes,
                    failedCount = direct.failedCount,
                    storageBeforeBytes = storageBefore,
                    trashedBytes = direct.trashedBytes,
                    trashEntryIds = direct.trashEntryIds,
                    changedCount = direct.changedCount,
                )
                refreshTrash()
                offerTrashFallback(TrashFallback.Source.PLAN, direct.trashUnavailableIds, direct.trashFolderMissing)
                onCleanupCompleted(direct.deletedIds.isNotEmpty())
            }.onFailure {
                _state.update { state ->
                    state.copy(
                        cleanupInProgress = false,
                        message = getApplication<Application>().getString(R.string.message_cleanup_failed),
                    )
                }
            }
        }
    }

    private suspend fun finishCleanup(
        items: List<CleanableItem>,
        deletedIds: Set<String>,
        deletedBytes: Long,
        failedCount: Int,
        storageBeforeBytes: Long,
        trashedBytes: Long = 0L,
        trashEntryIds: List<String> = emptyList(),
        changedCount: Int = 0,
    ) {
        val storageAfter = ioStorageSnapshot()
        val memoryAfter = ioMemorySnapshot()
        val ownCacheAfter = ioOwnCacheBytes()
        val measuredResult = CleanupResult(
            deletedBytes = deletedBytes,
            deletedCount = deletedIds.size,
            failedCount = failedCount,
            beforeAvailableBytes = storageBeforeBytes,
            afterAvailableBytes = storageAfter.availableBytes,
            trashedBytes = trashedBytes,
            trashEntryIds = trashEntryIds,
            changedCount = changedCount,
        )
        _state.update { state ->
            CleanerStateReducers.afterCleanup(
                state = state,
                items = items,
                deletedIds = deletedIds,
                storageAfter = storageAfter,
                memoryAfter = memoryAfter,
                ownCacheAfter = ownCacheAfter,
                result = measuredResult,
            )
        }
        persistDashboardSnapshot()
        recordCleanupHistory(measuredResult)
    }

    fun dismissCleanupResult() {
        _state.update { it.copy(cleanupResult = null) }
    }

}

/** Like runCatching, but never swallows coroutine cancellation. */
internal suspend inline fun <T> runSuspendCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (failure: Exception) {
    Result.failure(failure)
}

/** Pure rule for the automatic quick scan on app open (T1.10). */
internal object AutoScanPolicy {
    const val QUICK_SCAN_MAX_AGE_HOURS = 6L

    fun shouldScan(
        hasAccess: Boolean,
        scanning: Boolean,
        hasPlan: Boolean,
        planScannedAtElapsed: Long,
        nowElapsed: Long,
        savedSummaryAtMillis: Long = 0L,
        nowMillis: Long = 0L,
    ): Boolean {
        if (!hasAccess || scanning) return false
        val maxAge = java.util.concurrent.TimeUnit.HOURS.toMillis(QUICK_SCAN_MAX_AGE_HOURS)
        if (hasPlan && planScannedAtElapsed > 0L) return nowElapsed - planScannedAtElapsed > maxAge
        // After the app was closed the plan is gone from memory, but the saved
        // card totals (finding 8) are enough for 6 hours: no scan on every launch.
        return !savedSummaryIsFresh(savedSummaryAtMillis, nowMillis)
    }

    fun savedSummaryIsFresh(savedSummaryAtMillis: Long, nowMillis: Long): Boolean =
        savedSummaryAtMillis > 0L &&
            nowMillis >= savedSummaryAtMillis &&
            nowMillis - savedSummaryAtMillis <= java.util.concurrent.TimeUnit.HOURS.toMillis(QUICK_SCAN_MAX_AGE_HOURS)
}
