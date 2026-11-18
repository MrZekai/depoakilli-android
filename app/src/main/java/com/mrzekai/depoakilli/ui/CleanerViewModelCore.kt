package com.mrzekai.depoakilli.ui

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrzekai.depoakilli.data.DeviceRepository
import com.mrzekai.depoakilli.diagnostics.AppDiagnostics
import com.mrzekai.depoakilli.model.MemorySnapshot
import com.mrzekai.depoakilli.model.StorageSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


internal const val WHATSAPP_COMPLETION_DELAY_MILLIS = 80L
internal const val DEVICE_REFRESH_INTERVAL_MILLIS = 1_500L
internal const val APP_CACHE_REFRESH_INTERVAL_MILLIS = 60L * 1000L

/**
 * Layer of [CleanerViewModel] (T3.5): Base state and actions.
 * The layers form one inheritance chain so the public API stays on
 * CleanerViewModel while each concern lives in its own file.
 */
abstract class CleanerViewModelCore(
    application: Application,
    internal val repository: DeviceRepository,
) : AndroidViewModel(application) {
    internal val dashboardSnapshotStore = DashboardSnapshotStore(application)
    internal val cleanupHistoryStore = CleanupHistoryStore(application)
    internal val storageChangeStore = StorageChangeStore(application)
    internal val _state = MutableStateFlow(CleanerUiState())
    internal var pendingDeepCacheBeforeBytes: Long = 0L
    internal var pendingDeepCacheStorageBeforeBytes: Long = 0L
    internal var pendingIndividualCachePackage: String? = null
    internal var pendingIndividualCacheLabel: String? = null
    internal var pendingIndividualCacheBeforeBytes: Long = 0L
    internal var pendingIndividualCacheStorageBeforeBytes: Long = 0L
    internal var deviceRefreshJob: Job? = null
    internal var appCacheRefreshJob: Job? = null
    internal var installedAppsRefreshJob: Job? = null
    internal var storageReviewJob: Job? = null
    internal var storageReviewGeneration = 0L
    internal var lastDeviceRefreshAt = 0L
    internal var lastAppCacheRefreshAt = 0L
    internal var captureAppCacheForStorageChange = false

    val state: StateFlow<CleanerUiState> = _state.asStateFlow()

    // Storage and cache probing is disk I/O: StatFs on every mounted volume and
    // a recursive walk of the app cache directories. viewModelScope dispatches
    // on Dispatchers.Main.immediate, so these helpers keep the cleanup and scan
    // paths off the UI thread instead of risking StrictMode violations and ANRs
    // on slow or heavily populated storage.
    internal suspend fun ioStorageSnapshot(): StorageSnapshot =
        withContext(Dispatchers.IO) { repository.storageSnapshot() }

    internal suspend fun ioAvailableBytes(): Long = ioStorageSnapshot().availableBytes

    internal suspend fun ioOwnCacheBytes(): Long =
        withContext(Dispatchers.IO) { repository.ownCacheSize() }

    internal suspend fun ioMemorySnapshot(): MemorySnapshot =
        withContext(Dispatchers.Default) { repository.memorySnapshot() }

    /** Saved-state load, kept off the main thread (T3.5). onAppOpened waits for it. */
    internal var restoreJob: Job? = null

    internal fun restoreSavedState() {
        restoreJob = viewModelScope.launch {
            restoreDashboardSnapshot()
            restoreCleanupHistory()
            restoreStorageChange()
        }
    }

    internal suspend fun restoreDashboardSnapshot() {
        val snapshot = withContext(Dispatchers.IO) { dashboardSnapshotStore.load() } ?: return
        _state.update {
            it.copy(
                dashboardCleanableBytes = snapshot.cleanableBytes,
                dashboardReviewBytes = snapshot.reviewBytes,
                dashboardCategoryBytes = snapshot.categoryBytes,
                dashboardScannedFileCount = snapshot.scannedFileCount,
                dashboardScannedBytes = snapshot.scannedBytes,
                dashboardSnapshotAtMillis = snapshot.analyzedAtMillis,
            )
        }
    }

    internal fun persistDashboardSnapshot() {
        val state = _state.value
        if (state.dashboardSnapshotAtMillis <= 0L) return
        val snapshot =
            DashboardSnapshot(
                cleanableBytes = state.dashboardCleanableBytes,
                reviewBytes = state.dashboardReviewBytes,
                categoryBytes = state.dashboardCategoryBytes,
                scannedFileCount = state.dashboardScannedFileCount,
                scannedBytes = state.dashboardScannedBytes,
                analyzedAtMillis = state.dashboardSnapshotAtMillis,
            )
        viewModelScope.launch(Dispatchers.IO) { dashboardSnapshotStore.save(snapshot) }
    }

    internal suspend fun restoreCleanupHistory() {
        val history = withContext(Dispatchers.IO) { cleanupHistoryStore.load() }
        _state.update { it.copy(cleanupHistory = history) }
    }

    internal suspend fun restoreStorageChange() {
        val report = withContext(Dispatchers.IO) { storageChangeStore.load() }
        _state.update { it.copy(storageChange = report) }
    }

    internal fun recordCleanupHistory(result: CleanupResult) {
        AppDiagnostics.breadcrumb(
            "cleanup_result",
            mapOf(
                "ok" to result.operationSucceeded,
                "deleted" to result.deletedCount,
                "failed" to result.failedCount,
                "bytes" to result.deletedBytes,
                "kind" to result.kind.name,
            ),
        )
        if (result.deletedCount <= 0) return
        viewModelScope.launch {
            val updated = withContext(Dispatchers.IO) {
                cleanupHistoryStore.record(
                    deletedBytes = result.deletedBytes,
                    deletedCount = result.deletedCount,
                )
            }
            _state.update { it.copy(cleanupHistory = updated) }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    fun showMessage(@StringRes messageRes: Int) {
        _state.update { it.copy(message = getApplication<Application>().getString(messageRes)) }
    }

}
