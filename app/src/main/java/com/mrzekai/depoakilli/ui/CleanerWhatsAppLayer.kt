package com.mrzekai.depoakilli.ui

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.data.DeviceRepository
import com.mrzekai.depoakilli.model.CleanCategory
import com.mrzekai.depoakilli.model.WhatsAppMediaCategory
import com.mrzekai.depoakilli.model.WhatsAppMediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Layer of [CleanerViewModel] (T3.5): WhatsApp state and actions.
 * The layers form one inheritance chain so the public API stays on
 * CleanerViewModel while each concern lives in its own file.
 */
abstract class CleanerWhatsAppLayer(
    application: Application,
    repository: DeviceRepository,
) : CleanerScanLayer(application, repository) {
    fun scanWhatsAppLibrary() {
        if (_state.value.whatsAppScanning) return
        if (!repository.hasAllFilesAccess()) {
            _state.update {
                it.copy(message = getApplication<Application>().getString(R.string.message_all_files_required))
            }
            return
        }
        _state.update {
            it.copy(
                whatsAppScanning = true,
                whatsAppScanProgress = 0,
                whatsAppLastScanCompleted = false,
                message = null,
            )
        }
        viewModelScope.launch {
            // hasWhatsAppAccess() stats the WhatsApp media roots on disk (T3.5).
            val whatsAppFound = runSuspendCatching {
                withContext(Dispatchers.IO) { repository.hasWhatsAppAccess() }
            }.getOrDefault(false)
            if (!whatsAppFound) {
                _state.update {
                    it.copy(
                        whatsAppScanning = false,
                        message = getApplication<Application>().getString(R.string.message_whatsapp_not_found),
                    )
                }
                return@launch
            }
            _state.update { it.copy(hasWhatsAppAccess = true) }
            runSuspendCatching {
                repository.scanWhatsAppLibrary { progress ->
                    _state.update { current ->
                        current.copy(whatsAppScanProgress = progress.coerceIn(0, 100))
                    }
                }
            }.onSuccess { summary ->
                _state.update {
                    it.copy(
                        whatsAppSummary = summary,
                        whatsAppScanProgress = 100,
                    )
                }
                delay(WHATSAPP_COMPLETION_DELAY_MILLIS)
                val storageAfter = ioStorageSnapshot()
                _state.update {
                    it.copy(
                        whatsAppScanning = false,
                        whatsAppLastScanCompleted = true,
                        storage = storageAfter,
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                _state.update {
                    it.copy(
                        whatsAppScanning = false,
                        whatsAppScanProgress = 0,
                        message = getApplication<Application>().getString(R.string.message_whatsapp_scan_failed),
                    )
                }
            }
        }
    }

    fun toggleWhatsAppItem(id: String) {
        _state.update { state ->
            state.copy(
                whatsAppSummary = state.whatsAppSummary.copy(
                    items = state.whatsAppSummary.items.map { item ->
                        if (item.id == id) item.copy(selected = !item.selected) else item
                    },
                ),
            )
        }
    }

    fun toggleWhatsAppCategory(category: WhatsAppMediaCategory) {
        _state.update { state ->
            val categoryItems = state.whatsAppSummary.items.filter { it.category == category }
            val select = categoryItems.any { !it.selected }
            state.copy(
                whatsAppSummary = state.whatsAppSummary.copy(
                    items = state.whatsAppSummary.items.map { item ->
                        if (item.category == category) item.copy(selected = select) else item
                    },
                ),
            )
        }
    }

    fun deleteSelectedWhatsApp(onCompleted: (Boolean) -> Unit = {}) {
        if (_state.value.cleanupInProgress) return
        val selected = _state.value.whatsAppSummary.selectedItems
        if (selected.isEmpty()) {
            _state.update { it.copy(message = getApplication<Application>().getString(R.string.message_select_item)) }
            onCompleted(false)
            return
        }
        runWhatsAppDeletion(selected, forcePermanent = false, onCompleted = onCompleted)
    }

    internal fun runWhatsAppDeletion(
        selected: List<WhatsAppMediaItem>,
        forcePermanent: Boolean,
        onCompleted: (Boolean) -> Unit,
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
            runSuspendCatching { repository.deleteWhatsAppItems(selected, forcePermanent) }
                .onSuccess { result ->
                    val storageAfter = ioStorageSnapshot()
                    val measuredResult = CleanupResult(
                        deletedBytes = result.deletedBytes,
                        deletedCount = result.deletedIds.size,
                        failedCount = result.failedCount,
                        beforeAvailableBytes = storageBefore,
                        afterAvailableBytes = storageAfter.availableBytes,
                        trashedBytes = result.trashedBytes,
                        trashEntryIds = result.trashEntryIds,
                        changedCount = result.changedCount,
                    )
                    refreshTrash()
                    _state.update { state ->
                        val trackedWhatsAppBytes = state.dashboardCategoryBytes[CleanCategory.WHATSAPP_MEDIA] ?: 0L
                        val trackedDeletedBytes = minOf(trackedWhatsAppBytes, result.deletedBytes + result.trashedBytes)
                        state.copy(
                            whatsAppSummary = state.whatsAppSummary.copy(
                                items = state.whatsAppSummary.items.filterNot { it.id in result.deletedIds },
                            ),
                            dashboardReviewBytes = (state.dashboardReviewBytes - trackedDeletedBytes).coerceAtLeast(0L),
                            dashboardCategoryBytes = state.dashboardCategoryBytes.toMutableMap().apply {
                                if (containsKey(CleanCategory.WHATSAPP_MEDIA)) {
                                    this[CleanCategory.WHATSAPP_MEDIA] =
                                        (trackedWhatsAppBytes - trackedDeletedBytes).coerceAtLeast(0L)
                                }
                            },
                            storage = storageAfter,
                            cleanupInProgress = false,
                            cleanupResult = measuredResult,
                            message = null,
                        )
                    }
                    persistDashboardSnapshot()
                    recordCleanupHistory(measuredResult)
                    offerTrashFallback(TrashFallback.Source.WHATSAPP, result.trashUnavailableIds, result.trashFolderMissing)
                    onCompleted(result.deletedIds.isNotEmpty())
                }
                .onFailure { error ->
                    if (error is CancellationException) return@onFailure
                    _state.update { state ->
                        state.copy(
                            cleanupInProgress = false,
                            message = getApplication<Application>().getString(R.string.message_cleanup_failed),
                        )
                    }
                    onCompleted(false)
                }
        }
    }

}
