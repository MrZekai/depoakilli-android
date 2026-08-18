package com.mrzekai.depoakilli.ui

import android.app.Application
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.data.DeviceRepository
import com.mrzekai.depoakilli.model.AppCacheSnapshot
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.model.CleanCategory
import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.model.DeviceInfoSnapshot
import com.mrzekai.depoakilli.model.InstalledAppEntry
import com.mrzekai.depoakilli.model.MemorySnapshot
import com.mrzekai.depoakilli.model.ScanFocus
import com.mrzekai.depoakilli.model.ScanSummary
import com.mrzekai.depoakilli.model.StorageFileType
import com.mrzekai.depoakilli.model.StorageReviewItem
import com.mrzekai.depoakilli.model.StorageReviewSummary
import com.mrzekai.depoakilli.model.StorageSnapshot
import com.mrzekai.depoakilli.model.WhatsAppLibrarySummary
import com.mrzekai.depoakilli.model.WhatsAppMediaCategory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MemoryOptimizationResult(
    val beforeAvailableBytes: Long,
    val afterAvailableBytes: Long,
    val beforeAppUsedBytes: Long,
    val afterAppUsedBytes: Long,
) {
    val appMemoryReleasedBytes: Long
        get() = (beforeAppUsedBytes - afterAppUsedBytes).coerceAtLeast(0L)

    val availableRamGainBytes: Long
        get() = (afterAvailableBytes - beforeAvailableBytes).coerceAtLeast(0L)
}

data class CleanerUiState(
    val storage: StorageSnapshot = StorageSnapshot(),
    val memory: MemorySnapshot = MemorySnapshot(),
    val ownCacheBytes: Long = 0L,
    val appCache: AppCacheSnapshot = AppCacheSnapshot(),
    val installedApps: List<InstalledAppEntry> = emptyList(),
    val summary: ScanSummary = ScanSummary(),
    val dashboardCleanableBytes: Long = 0L,
    val dashboardReviewBytes: Long = 0L,
    val dashboardCategoryBytes: Map<CleanCategory, Long> = emptyMap(),
    val smartCategoryReview: CleanCategory? = null,
    val storageReview: StorageReviewSummary = StorageReviewSummary(),
    val storageReviewProgressFiles: Int = 0,
    val storageReviewProgressDirectories: Int = 0,
    val scanning: Boolean = false,
    val scanProgressFiles: Int = 0,
    val scanProgressDirectories: Int = 0,
    val scanningAppCaches: Boolean = false,
    val loadingApps: Boolean = false,
    val optimizingMemory: Boolean = false,
    val memoryOptimizationResult: MemoryOptimizationResult? = null,
    val cleanupInProgress: Boolean = false,
    val lastScanCompleted: Boolean = false,
    val scanFocus: ScanFocus = ScanFocus.SMART,
    val pendingScanFocus: ScanFocus? = null,
    val hasAllFilesAccess: Boolean = false,
    val hasUsageAccess: Boolean = false,
    val hasWhatsAppAccess: Boolean = false,
    val whatsAppSummary: WhatsAppLibrarySummary = WhatsAppLibrarySummary(),
    val whatsAppScanning: Boolean = false,
    val whatsAppScanProgress: Int = 0,
    val whatsAppLastScanCompleted: Boolean = false,
    val deviceInfo: DeviceInfoSnapshot = DeviceInfoSnapshot(),
    val message: String? = null,
)

class CleanerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DeviceRepository(application)
    private val _state = MutableStateFlow(CleanerUiState())
    private var pendingConsentItems: List<CleanableItem> = emptyList()
    private var appCacheRefreshJob: Job? = null
    private var lastAppCacheRefreshAt = 0L

    val state: StateFlow<CleanerUiState> = _state.asStateFlow()

    init {
        refreshDeviceState()
        refreshAppCaches()
        refreshInstalledApps()
    }

    fun refreshDeviceState() {
        _state.update {
            it.copy(
                storage = repository.storageSnapshot(),
                memory = repository.memorySnapshot(),
                ownCacheBytes = repository.ownCacheSize(),
                hasAllFilesAccess = repository.hasAllFilesAccess(),
                hasUsageAccess = repository.hasUsageAccess(),
                hasWhatsAppAccess = repository.hasWhatsAppAccess(),
                deviceInfo = repository.deviceInfoSnapshot(),
            )
        }
    }

    fun refreshAppCaches(force: Boolean = false) {
        if (appCacheRefreshJob?.isActive == true || _state.value.scanning) return
        val now = SystemClock.elapsedRealtime()
        if (!force && lastAppCacheRefreshAt != 0L && now - lastAppCacheRefreshAt < APP_CACHE_REFRESH_INTERVAL_MILLIS) {
            return
        }
        _state.update { it.copy(scanningAppCaches = true) }
        appCacheRefreshJob = viewModelScope.launch {
            runCatching { repository.appCacheSnapshot() }
                .onSuccess { snapshot ->
                    lastAppCacheRefreshAt = SystemClock.elapsedRealtime()
                    _state.update {
                        it.copy(
                            appCache = snapshot,
                            scanningAppCaches = false,
                            hasUsageAccess = repository.hasUsageAccess(),
                        )
                    }
                }
                .onFailure {
                    _state.update { it.copy(scanningAppCaches = false) }
                }
        }
    }

    fun refreshInstalledApps() {
        if (_state.value.loadingApps) return
        _state.update { it.copy(loadingApps = true) }
        viewModelScope.launch {
            runCatching { repository.installedAppsSnapshot() }
                .onSuccess { apps ->
                    _state.update {
                        it.copy(
                            installedApps = apps,
                            loadingApps = false,
                            hasUsageAccess = repository.hasUsageAccess(),
                        )
                    }
                }
                .onFailure {
                    _state.update { it.copy(loadingApps = false) }
                }
        }
    }

    fun queueScanAfterPermission(focus: ScanFocus) {
        _state.update { it.copy(scanFocus = focus, pendingScanFocus = focus) }
    }

    fun resumePendingScanAfterPermission() {
        val focus = _state.value.pendingScanFocus ?: return
        if (!repository.hasAllFilesAccess()) return
        _state.update { it.copy(pendingScanFocus = null) }
        scan(focus)
    }

    fun scan(focus: ScanFocus = ScanFocus.SMART) {
        if (_state.value.scanning) return
        refreshDeviceState()
        if (!repository.hasAllFilesAccess()) {
            _state.update {
                it.copy(
                    scanFocus = focus,
                    message = getApplication<Application>().getString(R.string.message_all_files_required),
                )
            }
            return
        }
        appCacheRefreshJob?.cancel()
        _state.update {
            it.copy(
                scanning = true,
                scanProgressFiles = 0,
                scanProgressDirectories = 0,
                scanFocus = focus,
                pendingScanFocus = null,
                lastScanCompleted = false,
                message = null,
            )
        }
        viewModelScope.launch {
            runCatching {
                repository.scan(focus) { directories, files ->
                    _state.update { current ->
                        current.copy(
                            scanProgressDirectories = directories,
                            scanProgressFiles = files,
                        )
                    }
                }
            }.onSuccess { summary ->
                _state.update {
                    val comprehensive = focus == ScanFocus.SMART || focus == ScanFocus.DEEP
                    it.copy(
                        summary = summary,
                        dashboardCleanableBytes = if (comprehensive) {
                            summary.safeSuggestedBytes
                        } else {
                            it.dashboardCleanableBytes
                        },
                        dashboardReviewBytes = if (comprehensive) {
                            summary.reviewBytes
                        } else {
                            it.dashboardReviewBytes
                        },
                        dashboardCategoryBytes = if (comprehensive) {
                            summary.byCategory.mapValues { (_, items) -> items.sumOf(CleanableItem::sizeBytes) }
                        } else {
                            it.dashboardCategoryBytes
                        },
                        scanning = false,
                        lastScanCompleted = true,
                        storage = repository.storageSnapshot(),
                        memory = repository.memorySnapshot(),
                        hasAllFilesAccess = repository.hasAllFilesAccess(),
                        hasWhatsAppAccess = repository.hasWhatsAppAccess(),
                    )
                }
                if (focus != ScanFocus.SMART) {
                    refreshAppCaches()
                }
            }.onFailure {
                _state.update { current ->
                    current.copy(
                        scanning = false,
                        message = getApplication<Application>().getString(R.string.message_scan_failed),
                    )
                }
            }
        }
    }

    fun scanWhatsAppLibrary() {
        if (_state.value.whatsAppScanning) return
        refreshDeviceState()
        if (!repository.hasAllFilesAccess()) {
            _state.update {
                it.copy(message = getApplication<Application>().getString(R.string.message_all_files_required))
            }
            return
        }
        if (!repository.hasWhatsAppAccess()) {
            _state.update {
                it.copy(message = getApplication<Application>().getString(R.string.message_whatsapp_not_found))
            }
            return
        }
        _state.update {
            it.copy(
                hasWhatsAppAccess = true,
                whatsAppScanning = true,
                whatsAppScanProgress = 0,
                whatsAppLastScanCompleted = false,
                message = null,
            )
        }
        viewModelScope.launch {
            runCatching {
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
                _state.update {
                    it.copy(
                        whatsAppScanning = false,
                        whatsAppLastScanCompleted = true,
                        storage = repository.storageSnapshot(),
                    )
                }
            }.onFailure {
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
        _state.update { it.copy(cleanupInProgress = true) }
        viewModelScope.launch {
            runCatching { repository.deleteWhatsAppItems(selected) }
                .onSuccess { result ->
                    _state.update { state ->
                        state.copy(
                            whatsAppSummary = state.whatsAppSummary.copy(
                                items = state.whatsAppSummary.items.filterNot { it.id in result.deletedIds },
                            ),
                            storage = repository.storageSnapshot(),
                            cleanupInProgress = false,
                            message = getApplication<Application>().getString(
                                if (result.failedCount == 0) R.string.message_whatsapp_cleanup_complete else R.string.message_whatsapp_cleanup_partial,
                                ByteFormatter.format(result.deletedBytes),
                                result.failedCount,
                            ),
                        )
                    }
                    onCompleted(result.deletedIds.isNotEmpty())
                }
                .onFailure {
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

    fun openSmartCategoryReview(category: CleanCategory) {
        _state.update { it.copy(smartCategoryReview = category) }
    }

    fun closeSmartCategoryReview() {
        _state.update { it.copy(smartCategoryReview = null) }
    }

    fun openStorageReview(type: StorageFileType) {
        if (_state.value.storageReview.loading && _state.value.storageReview.type == type) return
        if (!repository.hasAllFilesAccess()) {
            _state.update {
                it.copy(message = getApplication<Application>().getString(R.string.message_all_files_required))
            }
            return
        }
        _state.update {
            it.copy(
                storageReview = StorageReviewSummary(type = type, loading = true),
                storageReviewProgressFiles = 0,
                storageReviewProgressDirectories = 0,
                message = null,
            )
        }
        viewModelScope.launch {
            runCatching {
                repository.scanStorageReview(type) { directories, files ->
                    _state.update { state ->
                        if (state.storageReview.type == type) {
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
                    if (state.storageReview.type == type) {
                        state.copy(
                            storageReview = review,
                            storageReviewProgressFiles = review.scannedFileCount,
                        )
                    } else {
                        state
                    }
                }
            }.onFailure {
                _state.update { state ->
                    state.copy(
                        storageReview = StorageReviewSummary(type = type, loading = false),
                        message = getApplication<Application>().getString(R.string.message_scan_failed),
                    )
                }
            }
        }
    }

    fun closeStorageReview() {
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
            val select = categoryItems.any { !it.selected }
            state.copy(
                summary = state.summary.copy(
                    items = state.summary.items.map { item ->
                        if (item.assessment.category == category) item.copy(selected = select) else item
                    },
                ),
            )
        }
    }

    fun setItemsSelected(ids: Set<String>, selected: Boolean) {
        if (ids.isEmpty()) return
        _state.update { state ->
            state.copy(
                summary = state.summary.copy(
                    items = state.summary.items.map { item ->
                        if (item.id in ids && item.selected != selected) item.copy(selected = selected) else item
                    },
                ),
            )
        }
    }

    fun prepareCleanup(
        itemIds: Set<String>? = null,
        onPlanReady: (DeviceRepository.DeletePlan) -> Unit,
        onCleanupCompleted: () -> Unit,
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
        _state.update { it.copy(cleanupInProgress = true, message = null) }
        viewModelScope.launch {
            runCatching {
                val direct = repository.deleteDirectItems(selected)
                val remaining = selected.filterNot { it.id in direct.attemptedIds }
                val plan = repository.createDeleteRequest(remaining)
                if (plan is DeviceRepository.DeletePlan.NoMediaFiles) {
                    finishCleanup(selected, direct.deletedIds, direct.deletedBytes, direct.failedCount)
                    onCleanupCompleted()
                } else {
                    pendingConsentItems = remaining
                    finishCleanup(
                        items = selected.filter { it.id in direct.attemptedIds },
                        deletedIds = direct.deletedIds,
                        deletedBytes = direct.deletedBytes,
                        failedCount = direct.failedCount,
                        clearPending = false,
                    )
                    onPlanReady(plan)
                }
            }.onFailure {
                pendingConsentItems = emptyList()
                _state.update { state ->
                    state.copy(
                        cleanupInProgress = false,
                        message = getApplication<Application>().getString(R.string.message_cleanup_failed),
                    )
                }
            }
        }
    }

    fun deleteSelectedStorageReview(onCleanupCompleted: () -> Unit) {
        if (_state.value.cleanupInProgress) return
        val selected = _state.value.storageReview.selectedItems
        if (selected.isEmpty()) {
            _state.update { it.copy(message = getApplication<Application>().getString(R.string.message_select_item)) }
            return
        }
        _state.update { it.copy(cleanupInProgress = true, message = null) }
        viewModelScope.launch {
            runCatching { repository.deleteStorageReviewItems(selected) }
                .onSuccess { result ->
                    finishStorageReviewCleanup(
                        attempted = selected,
                        deletedIds = result.deletedIds,
                        deletedBytes = result.deletedBytes,
                        failedCount = result.failedCount,
                    )
                    onCleanupCompleted()
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

    fun completeCleanup(approved: Boolean) {
        val pending = pendingConsentItems
        pendingConsentItems = emptyList()
        if (!approved) {
            _state.update {
                it.copy(
                    cleanupInProgress = false,
                    message = getApplication<Application>().getString(R.string.message_cleanup_cancelled),
                )
            }
            return
        }
        val removedIds = pending.mapTo(hashSetOf(), CleanableItem::id)
        val removedBytes = pending.sumOf(CleanableItem::sizeBytes)
        finishCleanup(pending, removedIds, removedBytes, 0)
    }

    fun refreshAfterCleanup() {
        refreshDeviceState()
        val focus = _state.value.scanFocus
        if (repository.hasAllFilesAccess() && _state.value.lastScanCompleted) {
            scan(focus)
        }
    }

    private fun finishCleanup(
        items: List<CleanableItem>,
        deletedIds: Set<String>,
        deletedBytes: Long,
        failedCount: Int,
        clearPending: Boolean = true,
    ) {
        _state.update { state ->
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
            state.copy(
                summary = state.summary.copy(items = state.summary.items.filterNot { it.id in deletedIds }),
                dashboardCleanableBytes = (state.dashboardCleanableBytes - deletedAutoSelectedBytes).coerceAtLeast(0L),
                dashboardReviewBytes = (state.dashboardReviewBytes - deletedReviewBytes).coerceAtLeast(0L),
                dashboardCategoryBytes = state.dashboardCategoryBytes.mapValues { (category, bytes) ->
                    (bytes - (deletedByCategory[category] ?: 0L)).coerceAtLeast(0L)
                },
                storage = repository.storageSnapshot(),
                memory = repository.memorySnapshot(),
                ownCacheBytes = repository.ownCacheSize(),
                cleanupInProgress = if (clearPending) false else state.cleanupInProgress,
                message = getApplication<Application>().getString(
                    if (failedCount == 0) R.string.message_cleanup_complete_detail else R.string.message_cleanup_partial_detail,
                    ByteFormatter.format(deletedBytes),
                    failedCount,
                ),
            )
        }
        if (clearPending) pendingConsentItems = emptyList()
    }

    private fun finishStorageReviewCleanup(
        attempted: List<StorageReviewItem>,
        deletedIds: Set<String>,
        deletedBytes: Long,
        failedCount: Int,
    ) {
        _state.update { state ->
            val deletedFiles = attempted.filter { it.id in deletedIds }.map(StorageReviewItem::file)
            val deletedUris = deletedFiles.mapTo(hashSetOf()) { it.uri }
            val remainingSmartItems = state.summary.items.filterNot { it.uri in deletedUris }
            val reviewType = state.storageReview.type
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

            state.copy(
                summary = state.summary.copy(
                    items = remainingSmartItems,
                    storageTypes = updatedStorageTypes,
                    storagePreviews = state.summary.storagePreviews.mapValues { (_, files) ->
                        files.filterNot { it.uri in deletedUris }
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
                storage = repository.storageSnapshot(),
                memory = repository.memorySnapshot(),
                cleanupInProgress = false,
                message = getApplication<Application>().getString(
                    if (failedCount == 0) R.string.message_cleanup_complete_detail else R.string.message_cleanup_partial_detail,
                    ByteFormatter.format(deletedBytes),
                    failedCount,
                ),
            )
        }
    }

    fun onDeepCacheCleanupResult(approved: Boolean) {
        refreshDeviceState()
        refreshAppCaches(force = true)
        _state.update {
            it.copy(
                message = getApplication<Application>().getString(
                    if (approved) R.string.message_deep_cache_complete else R.string.message_cache_cleanup_cancelled,
                ),
            )
        }
    }

    fun optimizeMemory(releaseHeavyResources: () -> Unit) {
        if (_state.value.scanning || _state.value.optimizingMemory) return
        val before = repository.memorySnapshot()
        _state.update {
            it.copy(
                optimizingMemory = true,
                memoryOptimizationResult = null,
                message = null,
            )
        }

        // Release only Smart Cleaner's temporary/heavy in-memory resources.
        // Scan selections and user-reviewed file state remain intact.
        runCatching(releaseHeavyResources)
        runCatching { Runtime.getRuntime().gc() }

        viewModelScope.launch {
            delay(MEMORY_MEASUREMENT_DELAY_MILLIS)
            val after = repository.memorySnapshot()
            val result = MemoryOptimizationResult(
                beforeAvailableBytes = before.availableBytes,
                afterAvailableBytes = after.availableBytes,
                beforeAppUsedBytes = before.appUsedBytes,
                afterAppUsedBytes = after.appUsedBytes,
            )
            _state.update {
                it.copy(
                    memory = after,
                    optimizingMemory = false,
                    memoryOptimizationResult = result,
                    message = null,
                )
            }
        }
    }

    fun dismissMemoryOptimizationResult() {
        _state.update { it.copy(memoryOptimizationResult = null) }
    }

    fun clearOwnAppCache() {
        viewModelScope.launch {
            val clearedBytes = repository.clearOwnCache().coerceAtLeast(0L)
            _state.update {
                it.copy(
                    ownCacheBytes = repository.ownCacheSize(),
                    storage = repository.storageSnapshot(),
                    message = getApplication<Application>().getString(
                        if (clearedBytes > 0L) R.string.message_own_cache_cleared else R.string.message_cache_already_empty,
                        ByteFormatter.format(clearedBytes),
                    ),
                )
            }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    fun showMessage(@StringRes messageRes: Int) {
        _state.update { it.copy(message = getApplication<Application>().getString(messageRes)) }
    }

    private companion object {
        const val MEMORY_MEASUREMENT_DELAY_MILLIS = 700L
        const val WHATSAPP_COMPLETION_DELAY_MILLIS = 250L
        const val APP_CACHE_REFRESH_INTERVAL_MILLIS = 60L * 1000L
    }
}
