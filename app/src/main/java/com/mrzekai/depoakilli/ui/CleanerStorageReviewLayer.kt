package com.mrzekai.depoakilli.ui

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.data.DeviceRepository
import com.mrzekai.depoakilli.data.StoragePathRules
import com.mrzekai.depoakilli.model.CleanCategory
import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.model.FolderSize
import com.mrzekai.depoakilli.model.StorageFileType
import com.mrzekai.depoakilli.model.StorageReviewItem
import com.mrzekai.depoakilli.model.StorageReviewSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Layer of [CleanerViewModel] (T3.5): StorageReview state and actions.
 * The layers form one inheritance chain so the public API stays on
 * CleanerViewModel while each concern lives in its own file.
 */
abstract class CleanerStorageReviewLayer(
    application: Application,
    repository: DeviceRepository,
) : CleanerWhatsAppLayer(application, repository) {
    fun openSmartCategoryReview(category: CleanCategory, itemIds: Set<String>? = null) {
        _state.update {
            it.copy(
                smartCategoryReview = category,
                smartCategoryReviewIds = itemIds,
                verifyFeedback = null,
            )
        }
    }

    fun closeSmartCategoryReview() {
        _state.update {
            it.copy(
                smartCategoryReview = null,
                smartCategoryReviewIds = null,
                smartCategoryReviewGroup = null,
                verifyFeedback = null,
            )
        }
    }

    fun openStorageReview(type: StorageFileType, excludeWhatsAppMedia: Boolean = false) {
        if (!repository.hasAllFilesAccess()) {
            _state.update {
                it.copy(message = getApplication<Application>().getString(R.string.message_all_files_required))
            }
            return
        }

        storageReviewJob?.cancel()
        val reviewGeneration = ++storageReviewGeneration

        val current = _state.value
        val immediateItems = current.summary.storagePreviews[type]
            .orEmpty()
            .asSequence()
            .filter { file ->
                !excludeWhatsAppMedia || !isWhatsAppIndexedPath(file.relativePath)
            }
            .map { file -> StorageReviewItem(file = file, selected = false) }
            .toList()

        // Open the actual selectable content immediately from Smart Scan previews.
        // The full category is refreshed silently in the background; the user never
        // waits on a second "preparing category" screen.
        _state.update {
            it.copy(
                storageReview = StorageReviewSummary(
                    type = type,
                    excludeWhatsAppMedia = excludeWhatsAppMedia,
                    items = immediateItems,
                    scannedFileCount = immediateItems.size,
                    scanLimitReached = current.summary.scanLimitReached,
                    loading = true,
                ),
                storageReviewProgressFiles = 0,
                storageReviewProgressDirectories = 0,
                message = null,
            )
        }

        storageReviewJob = viewModelScope.launch {
            runSuspendCatching {
                repository.scanStorageReview(type, excludeWhatsAppMedia) { directories, files ->
                    _state.update { state ->
                        if (
                            reviewGeneration == storageReviewGeneration &&
                            state.storageReview.type == type &&
                            state.storageReview.excludeWhatsAppMedia == excludeWhatsAppMedia
                        ) {
                            state.copy(
                                storageReviewProgressDirectories = directories,
                                storageReviewProgressFiles = files,
                            )
                        } else {
                            state
                        }
                    }
                }
            }.onSuccess { review ->
                _state.update { state ->
                    if (
                        reviewGeneration == storageReviewGeneration &&
                        state.storageReview.type == type &&
                        state.storageReview.excludeWhatsAppMedia == excludeWhatsAppMedia
                    ) {
                        val selectedIds = state.storageReview.items
                            .asSequence()
                            .filter(StorageReviewItem::selected)
                            .mapTo(hashSetOf(), StorageReviewItem::id)

                        state.copy(
                            storageReview = review.copy(
                                items = review.items.map { item ->
                                    if (item.id in selectedIds) item.copy(selected = true) else item
                                },
                                loading = false,
                            ),
                            storageReviewProgressFiles = review.scannedFileCount,
                        )
                    } else {
                        state
                    }
                }
            }.onFailure {
                _state.update { state ->
                    if (
                        reviewGeneration == storageReviewGeneration &&
                        state.storageReview.type == type &&
                        state.storageReview.excludeWhatsAppMedia == excludeWhatsAppMedia
                    ) {
                        // Keep the immediately visible Smart Scan items usable even if
                        // the silent background expansion fails.
                        CleanerStateReducers.storageReviewFailed(
                            state,
                            getApplication<Application>().getString(R.string.message_scan_failed),
                        )
                    } else {
                        state
                    }
                }
            }
        }
    }

    internal fun isWhatsAppIndexedPath(relativePath: String): Boolean =
        StoragePathRules.isWhatsAppPath(relativePath)

    /** Opens one of the biggest folders as a review list; nothing is selected (V7). */
    /** Faz 3C: files matching a ready-made filter (live count on the filter screen). */
    suspend fun filterFiles(filter: com.mrzekai.depoakilli.plan.FileFilter) = repository.filterFiles(filter)

    /** Faz 3C: shows the files of a ready-made filter in the usual review (selection is the user's). */
    fun openFilterReview(filter: com.mrzekai.depoakilli.plan.FileFilter, label: String) {
        storageReviewJob?.cancel()
        ++storageReviewGeneration
        _state.update {
            it.copy(
                storageReview = StorageReviewSummary(type = StorageFileType.OTHER, loading = true),
                storageReviewProgressFiles = 0,
                storageReviewProgressDirectories = 0,
                message = null,
            )
        }
        storageReviewJob = viewModelScope.launch {
            val files = runSuspendCatching { repository.filterFiles(filter) }.getOrDefault(emptyList())
            _state.update { it.copy(storageReview = repository.filterReview(files, label)) }
        }
    }

    fun openFolderReview(folder: FolderSize) {
        if (!repository.hasAllFilesAccess()) {
            _state.update {
                it.copy(message = getApplication<Application>().getString(R.string.message_all_files_required))
            }
            return
        }
        storageReviewJob?.cancel()
        val reviewGeneration = ++storageReviewGeneration
        _state.update {
            it.copy(
                storageReview = StorageReviewSummary(type = StorageFileType.OTHER, folder = folder, loading = true),
                storageReviewProgressFiles = 0,
                storageReviewProgressDirectories = 0,
                message = null,
            )
        }
        storageReviewJob = viewModelScope.launch {
            runSuspendCatching {
                repository.scanFolderReview(folder) { directories, files ->
                    _state.update { state ->
                        if (reviewGeneration == storageReviewGeneration && state.storageReview.folder == folder) {
                            state.copy(storageReviewProgressDirectories = directories, storageReviewProgressFiles = files)
                        } else {
                            state
                        }
                    }
                }
            }.onSuccess { review ->
                _state.update { state ->
                    if (reviewGeneration == storageReviewGeneration && state.storageReview.folder == folder) {
                        state.copy(storageReview = review)
                    } else {
                        state
                    }
                }
            }.onFailure {
                _state.update { state ->
                    if (reviewGeneration == storageReviewGeneration && state.storageReview.folder == folder) {
                        state.copy(
                            storageReview = state.storageReview.copy(loading = false),
                            message = getApplication<Application>().getString(R.string.message_scan_failed),
                        )
                    } else {
                        state
                    }
                }
            }
        }
    }

    fun closeStorageReview() {
        storageReviewGeneration++
        storageReviewJob?.cancel()
        storageReviewJob = null
        _state.update {
            it.copy(
                storageReview = StorageReviewSummary(),
                storageReviewProgressFiles = 0,
                storageReviewProgressDirectories = 0,
            )
        }
    }

    fun toggleStorageReviewItem(id: String) {
        _state.update { state ->
            state.copy(
                storageReview = state.storageReview.copy(
                    items = state.storageReview.items.map { item ->
                        if (item.id == id) item.copy(selected = !item.selected) else item
                    },
                ),
            )
        }
    }

    fun toggleAllStorageReviewItems() {
        // D1: "Select all" is the user's own action and works on every review
        // (personal media and folders too); the confirmation then shows the
        // groups and a camera warning, and everything goes to Trash.
        _state.update { state ->
            val items = state.storageReview.items
            val select = items.any { !it.selected }
            state.copy(
                storageReview = state.storageReview.copy(
                    items = items.map { it.copy(selected = select) },
                ),
            )
        }
    }

    fun deleteSelectedStorageReview(onCleanupCompleted: (Boolean) -> Unit) {
        if (_state.value.cleanupInProgress) return
        val selected = _state.value.storageReview.selectedItems
        if (selected.isEmpty()) {
            _state.update { it.copy(message = getApplication<Application>().getString(R.string.message_select_item)) }
            return
        }

        storageReviewGeneration++
        storageReviewJob?.cancel()
        storageReviewJob = null
        runStorageReviewDeletion(selected, forcePermanent = false, onCleanupCompleted = onCleanupCompleted)
    }

    internal fun runStorageReviewDeletion(
        selected: List<StorageReviewItem>,
        forcePermanent: Boolean,
        onCleanupCompleted: (Boolean) -> Unit,
    ) {
        _state.update {
            it.copy(
                cleanupInProgress = true,
                cleanupResult = null,
                message = null,
            )
        }
        viewModelScope.launch {
            val storageBefore = ioAvailableBytes()
            runSuspendCatching { repository.deleteStorageReviewItems(selected, forcePermanent) }
                .onSuccess { result ->
                    finishStorageReviewCleanup(
                        attempted = selected,
                        deletedIds = result.deletedIds,
                        deletedBytes = result.deletedBytes,
                        failedCount = result.failedCount,
                        beforeAvailableBytes = storageBefore,
                        trashedBytes = result.trashedBytes,
                        trashEntryIds = result.trashEntryIds,
                        changedCount = result.changedCount,
                    )
                    refreshTrash()
                    offerTrashFallback(TrashFallback.Source.STORAGE_REVIEW, result.trashUnavailableIds, result.trashFolderMissing)
                    onCleanupCompleted(result.deletedIds.isNotEmpty())
                }
                .onFailure { error ->
                    if (error is CancellationException) return@onFailure
                    _state.update { state ->
                        state.copy(
                            cleanupInProgress = false,
                            message = getApplication<Application>().getString(R.string.message_cleanup_failed),
                        )
                    }
                }
        }
    }

    internal suspend fun finishStorageReviewCleanup(
        attempted: List<StorageReviewItem>,
        deletedIds: Set<String>,
        deletedBytes: Long,
        failedCount: Int,
        beforeAvailableBytes: Long,
        trashedBytes: Long = 0L,
        trashEntryIds: List<String> = emptyList(),
        changedCount: Int = 0,
    ) {
        val storageAfter = ioStorageSnapshot()
        val memoryAfter = ioMemorySnapshot()
        val measuredResult = CleanupResult(
            deletedBytes = deletedBytes,
            deletedCount = deletedIds.size,
            failedCount = failedCount,
            beforeAvailableBytes = beforeAvailableBytes,
            afterAvailableBytes = storageAfter.availableBytes,
            trashedBytes = trashedBytes,
            trashEntryIds = trashEntryIds,
            changedCount = changedCount,
        )
        _state.update { state ->
            val deletedFiles = attempted.filter { it.id in deletedIds }.map(StorageReviewItem::file)
            val deletedUris = deletedFiles.mapTo(hashSetOf()) { it.uri }
            val remainingSmartItems = state.summary.items.filterNot { it.uri in deletedUris }
            // A folder review mixes file types: type statistics are left as they are.
            val reviewType = state.storageReview.type.takeIf { state.storageReview.folder == null }
            val reviewFolder = state.storageReview.folder
            val deletedStorageBytes = deletedFiles.sumOf { it.sizeBytes }
            val deletedStorageCount = deletedFiles.size
            val updatedStorageTypes = state.summary.storageTypes.map { stat ->
                if (reviewType != null && stat.type == reviewType) {
                    stat.copy(
                        fileCount = (stat.fileCount - deletedStorageCount).coerceAtLeast(0),
                        totalBytes = (stat.totalBytes - deletedStorageBytes).coerceAtLeast(0L),
                    )
                } else {
                    stat
                }
            }.filter { it.fileCount > 0 || it.totalBytes > 0L }

            val updatedSmartMediaTypes = state.summary.smartMediaTypes.map { stat ->
                if (
                    state.storageReview.excludeWhatsAppMedia &&
                    reviewType != null &&
                    stat.type == reviewType
                ) {
                    stat.copy(
                        fileCount = (stat.fileCount - deletedStorageCount).coerceAtLeast(0),
                        totalBytes = (stat.totalBytes - deletedStorageBytes).coerceAtLeast(0L),
                    )
                } else {
                    stat
                }
            }.filter { it.fileCount > 0 || it.totalBytes > 0L }

            state.copy(
                summary = state.summary.copy(
                    items = remainingSmartItems,
                    storageTypes = updatedStorageTypes,
                    smartMediaTypes = updatedSmartMediaTypes,
                    storagePreviews = state.summary.storagePreviews.mapValues { (_, files) ->
                        files.filterNot { it.uri in deletedUris }
                    },
                    biggestFolders = state.summary.biggestFolders.map { folder ->
                        if (folder == reviewFolder) {
                            folder.copy(
                                bytes = (folder.bytes - deletedStorageBytes).coerceAtLeast(0L),
                                fileCount = (folder.fileCount - deletedStorageCount).coerceAtLeast(0),
                            )
                        } else {
                            folder
                        }
                    },
                ),
                dashboardCleanableBytes = remainingSmartItems
                    .asSequence()
                    .filter { it.assessment.recommended }
                    .sumOf(CleanableItem::sizeBytes),
                dashboardReviewBytes = remainingSmartItems
                    .asSequence()
                    .filterNot { it.assessment.recommended }
                    .sumOf(CleanableItem::sizeBytes),
                dashboardCategoryBytes = remainingSmartItems
                    .groupBy { it.assessment.category }
                    .mapValues { (_, items) -> items.sumOf(CleanableItem::sizeBytes) },
                storageReview = state.storageReview.copy(
                    items = state.storageReview.items.filterNot { it.id in deletedIds },
                    loading = false,
                ),
                storage = storageAfter,
                memory = memoryAfter,
                cleanupInProgress = false,
                cleanupResult = measuredResult,
                message = null,
            )
        }
        persistDashboardSnapshot()
        recordCleanupHistory(measuredResult)
    }

}
