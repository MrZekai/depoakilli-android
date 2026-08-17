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

data class CleanerUiState(
    val storage: StorageSnapshot = StorageSnapshot(),
    val memory: MemorySnapshot = MemorySnapshot(),
    val ownCacheBytes: Long = 0L,
    val appCache: AppCacheSnapshot = AppCacheSnapshot(),
    val installedApps: List<InstalledAppEntry> = emptyList(),
    val summary: ScanSummary = ScanSummary(),
    val dashboardCleanableBytes: Long = 0L,
    val dashboardCategoryBytes: Map<CleanCategory, Long> = emptyMap(),
    val scanning: Boolean = false,
    val scanProgressFiles: Int = 0,
    val scanProgressDirectories: Int = 0,
    val scanningAppCaches: Boolean = false,
    val loadingApps: Boolean = false,
    val optimizingMemory: Boolean = false,
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
                            summary.totalSuggestedBytes
                        } else {
                            it.dashboardCleanableBytes
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
                refreshAppCaches(force = true)
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

    fun deleteSelectedWhatsApp() {
        val selected = _state.value.whatsAppSummary.selectedItems
        if (selected.isEmpty()) {
            _state.update { it.copy(message = getApplication<Application>().getString(R.string.message_select_item)) }
            return
        }
        viewModelScope.launch {
            val result = repository.deleteWhatsAppItems(selected)
            _state.update { state ->
                state.copy(
                    whatsAppSummary = state.whatsAppSummary.copy(
                        items = state.whatsAppSummary.items.filterNot { it.id in result.deletedIds },
                    ),
                    storage = repository.storageSnapshot(),
                    message = getApplication<Application>().getString(
                        if (result.failedCount == 0) R.string.message_whatsapp_cleanup_complete else R.string.message_whatsapp_cleanup_partial,
                        ByteFormatter.format(result.deletedBytes),
                        result.failedCount,
                    ),
                )
            }
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

    fun prepareCleanup(
        onPlanReady: (DeviceRepository.DeletePlan) -> Unit,
        onCleanupCompleted: () -> Unit,
    ) {
        val selected = _state.value.summary.selectedItems
        if (selected.isEmpty()) {
            _state.update { it.copy(message = getApplication<Application>().getString(R.string.message_select_item)) }
            return
        }
        viewModelScope.launch {
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
        }
    }

    fun completeCleanup(approved: Boolean) {
        val pending = pendingConsentItems
        pendingConsentItems = emptyList()
        if (!approved) {
            _state.update {
                it.copy(message = getApplication<Application>().getString(R.string.message_cleanup_cancelled))
            }
            return
        }
        val removedIds = pending.mapTo(hashSetOf(), CleanableItem::id)
        val removedBytes = pending.sumOf(CleanableItem::sizeBytes)
        finishCleanup(pending, removedIds, removedBytes, 0)
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
            state.copy(
                summary = state.summary.copy(items = state.summary.items.filterNot { it.id in deletedIds }),
                dashboardCleanableBytes = (state.dashboardCleanableBytes - deletedBytes).coerceAtLeast(0L),
                dashboardCategoryBytes = state.dashboardCategoryBytes.mapValues { (category, bytes) ->
                    (bytes - (deletedByCategory[category] ?: 0L)).coerceAtLeast(0L)
                },
                storage = repository.storageSnapshot(),
                memory = repository.memorySnapshot(),
                ownCacheBytes = repository.ownCacheSize(),
                message = getApplication<Application>().getString(
                    if (failedCount == 0) R.string.message_cleanup_complete_detail else R.string.message_cleanup_partial_detail,
                    ByteFormatter.format(deletedBytes),
                    failedCount,
                ),
            )
        }
        if (clearPending) pendingConsentItems = emptyList()
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
                message = null,
                summary = it.summary.copy(items = emptyList()),
                whatsAppSummary = it.whatsAppSummary.copy(items = emptyList()),
            )
        }
        runCatching(releaseHeavyResources)
        runCatching { Runtime.getRuntime().gc() }
        viewModelScope.launch {
            delay(MEMORY_MEASUREMENT_DELAY_MILLIS)
            val after = repository.memorySnapshot()
            val releasedAppMemory = (before.appUsedBytes - after.appUsedBytes).coerceAtLeast(0L)
            val message = if (releasedAppMemory > 0L) {
                getApplication<Application>().getString(
                    R.string.message_memory_optimized,
                    ByteFormatter.format(releasedAppMemory),
                    ByteFormatter.format(after.availableBytes),
                )
            } else {
                getApplication<Application>().getString(
                    R.string.message_memory_optimized_stable,
                    ByteFormatter.format(after.availableBytes),
                )
            }
            _state.update { it.copy(memory = after, optimizingMemory = false, message = message) }
        }
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
