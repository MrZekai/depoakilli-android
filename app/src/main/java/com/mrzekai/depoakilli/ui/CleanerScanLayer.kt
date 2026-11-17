package com.mrzekai.depoakilli.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.viewModelScope
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.data.DeviceRepository
import com.mrzekai.depoakilli.diagnostics.AppDiagnostics
import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.model.ScanFocus
import com.mrzekai.depoakilli.model.StorageReviewSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Layer of [CleanerViewModel] (T3.5): Scan state and actions.
 * The layers form one inheritance chain so the public API stays on
 * CleanerViewModel while each concern lives in its own file.
 */
abstract class CleanerScanLayer(
    application: Application,
    repository: DeviceRepository,
) : CleanerAppCacheLayer(application, repository) {
    fun queueScanAfterPermission(focus: ScanFocus) {
        _state.update {
            it.copy(
                scanFocus = focus,
                pendingScanFocus = focus,
                smartCategoryReview = null,
                smartCategoryReviewIds = null,
            )
        }
    }

    fun refreshDashboard() {
        val current = _state.value
        if (
            current.dashboardRefreshing ||
            current.scanning ||
            current.whatsAppScanning ||
            current.cleanupInProgress
        ) return

        _state.update { it.copy(dashboardRefreshing = true, message = null) }
        refreshDeviceState()

        if (repository.hasAllFilesAccess()) {
            scan(ScanFocus.SMART)
        } else {
            _state.update { it.copy(dashboardRefreshing = false) }
        }
    }

    fun resumePendingScanAfterPermission() {
        val focus = _state.value.pendingScanFocus ?: return
        if (!repository.hasAllFilesAccess()) return
        _state.update { it.copy(pendingScanFocus = null) }
        scan(focus)
    }

    internal var scanJob: Job? = null

    /** Cancels a running scan; the screen closes immediately and a new scan can start. */
    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _state.update {
            it.copy(
                scanning = false,
                dashboardRefreshing = false,
                scanHashing = false,
                message = null,
            )
        }
    }

    /**
     * Quick plan scan on app open (T1.10): only with all-files access, never
     * while another scan runs, and only when there is no plan or it is older
     * than [QUICK_SCAN_MAX_AGE_HOURS]. Nothing is ever deleted automatically.
     */
    fun onAppOpened() {
        val current = _state.value
        val hasPlan = current.planSummary != null ||
            (current.scanFocus == ScanFocus.SMART && current.lastScanCompleted)
        if (
            AutoScanPolicy.shouldScan(
                hasAccess = repository.hasAllFilesAccess(),
                scanning = current.scanning,
                hasPlan = hasPlan,
                planScannedAtElapsed = current.planScannedAtElapsed,
                nowElapsed = SystemClock.elapsedRealtime(),
                savedSummaryAtMillis = current.dashboardSnapshotAtMillis,
                nowMillis = System.currentTimeMillis(),
            )
        ) {
            scan(ScanFocus.SMART)
        }
    }

    /** Brings the Smart plan back after a tool scan replaced the shown results. */
    fun showPlan() {
        _state.update { state ->
            val stashed = state.planSummary
            if (state.scanning || state.scanFocus == ScanFocus.SMART || stashed == null) {
                state
            } else {
                state.copy(
                    summary = stashed,
                    planSummary = null,
                    scanFocus = ScanFocus.SMART,
                    lastScanCompleted = true,
                    smartCategoryReview = null,
                    smartCategoryReviewIds = null,
                )
            }
        }
    }

    fun scan(focus: ScanFocus = ScanFocus.SMART) {
        if (_state.value.scanning) return
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
        // A new scan replaces the result set, so any sub-list opened on the
        // previous results is closed. A stale review left behind here kept its
        // title ("Old downloads") on the next tool's screen (QA v43 #06).
        storageReviewGeneration++
        storageReviewJob?.cancel()
        storageReviewJob = null
        val scanStartedAtElapsed = SystemClock.elapsedRealtime()
        AppDiagnostics.breadcrumb("scan_started", mapOf("focus" to focus.name))
        _state.update {
            // Keep the Smart plan while a tool scan borrows the results area.
            val stash = when {
                focus == ScanFocus.SMART -> null
                it.scanFocus == ScanFocus.SMART && it.lastScanCompleted -> it.summary
                else -> it.planSummary
            }
            it.copy(
                planSummary = stash,
                scanHashing = false,
                scanHashDoneBytes = 0L,
                scanHashTotalBytes = 0L,
                smartCategoryReview = null,
                smartCategoryReviewIds = null,
                storageReview = StorageReviewSummary(),
                storageReviewProgressFiles = 0,
                storageReviewProgressDirectories = 0,
                scanning = true,
                scanProgressFiles = 0,
                scanProgressDirectories = 0,
                scanFocus = focus,
                pendingScanFocus = null,
                lastScanCompleted = false,
                message = null,
            )
        }
        scanJob = viewModelScope.launch {
            runCatching {
                repository.scan(
                    focus = focus,
                    onProgress = { directories, files ->
                        _state.update { current ->
                            current.copy(
                                scanProgressDirectories = directories,
                                scanProgressFiles = files,
                            )
                        }
                    },
                    onHashProgress = { done, total ->
                        _state.update { current ->
                            current.copy(scanHashing = true, scanHashDoneBytes = done, scanHashTotalBytes = total)
                        }
                    },
                )
            }.onSuccess { summary ->
                AppDiagnostics.breadcrumb(
                    "scan_completed",
                    mapOf(
                        "focus" to focus.name,
                        "files" to summary.scannedFileCount,
                        "bytes" to summary.scannedBytes,
                        "ms" to (SystemClock.elapsedRealtime() - scanStartedAtElapsed),
                        "limit_reached" to summary.scanLimitReached,
                    ),
                )
                val comprehensive = focus == ScanFocus.SMART || focus == ScanFocus.DEEP
                val recordsStorageChange = comprehensive || focus == ScanFocus.ANALYZE
                val analyzedAtMillis = if (recordsStorageChange) System.currentTimeMillis() else 0L
                val storageSnapshot = ioStorageSnapshot()
                val memorySnapshot = ioMemorySnapshot()
                val hasAllFilesAccess = repository.hasAllFilesAccess()
                // hasWhatsAppAccess() stats the WhatsApp media roots on disk.
                val hasWhatsAppAccess = withContext(Dispatchers.IO) { repository.hasWhatsAppAccess() }
                val storageChangeReport = if (recordsStorageChange) {
                    storageChangeStore.recordFileSnapshot(
                        storage = storageSnapshot,
                        storageTypes = summary.storageTypes,
                        analyzedAtMillis = analyzedAtMillis,
                    )
                } else {
                    _state.value.storageChange
                }

                _state.update {
                    it.copy(
                        summary = if (focus == ScanFocus.ANALYZE) {
                            it.summary.copy(
                                scannedFileCount = summary.scannedFileCount,
                                scannedBytes = summary.scannedBytes,
                                limitedAccess = summary.limitedAccess,
                                scanLimitReached = summary.scanLimitReached,
                                storageTypes = summary.storageTypes,
                                storagePreviews = summary.storagePreviews,
                                smartMediaTypes = summary.smartMediaTypes,
                            )
                        } else {
                            summary
                        },
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
                        dashboardScannedFileCount = if (comprehensive) {
                            summary.scannedFileCount
                        } else {
                            it.dashboardScannedFileCount
                        },
                        dashboardScannedBytes = if (comprehensive) {
                            summary.scannedBytes
                        } else {
                            it.dashboardScannedBytes
                        },
                        dashboardSnapshotAtMillis = if (comprehensive) {
                            analyzedAtMillis
                        } else {
                            it.dashboardSnapshotAtMillis
                        },
                        storageChange = if (recordsStorageChange) {
                            storageChangeReport
                        } else {
                            it.storageChange
                        },
                        scanning = false,
                        dashboardRefreshing = false,
                        scanHashing = false,
                        lastScanCompleted = true,
                        planScannedAtElapsed = if (focus == ScanFocus.SMART) {
                            SystemClock.elapsedRealtime()
                        } else {
                            it.planScannedAtElapsed
                        },
                        storage = storageSnapshot,
                        memory = memorySnapshot,
                        hasAllFilesAccess = hasAllFilesAccess,
                        hasWhatsAppAccess = hasWhatsAppAccess,
                    )
                }
                if (comprehensive) {
                    persistDashboardSnapshot()
                }

                if (recordsStorageChange && repository.hasUsageAccess()) {
                    // Enrich the real storage snapshot asynchronously with
                    // Android-reported per-app cache values.
                    captureAppCacheForStorageChange = true
                    refreshAppCaches(force = true)
                }
            }.onFailure { error ->
                // A cancelled scan (ViewModel cleared, or a newer scan started)
                // is not a crash: reporting it would flood diagnostic logs and show a
                // false "scan failed" message.
                if (error is CancellationException) return@onFailure
                AppDiagnostics.captureException(
                    error,
                    "scan_failed",
                    mapOf("focus" to focus.name),
                )
                _state.update { current ->
                    current.copy(
                        scanning = false,
                        dashboardRefreshing = false,
                        scanHashing = false,
                        message = getApplication<Application>().getString(R.string.message_scan_failed),
                    )
                }
            }
        }
    }

    /** Full content check of one look-alike copy; a mismatch removes it from the list. */
    fun verifyDuplicate(id: String) {
        val item = _state.value.summary.items.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            val verified = runSuspendCatching { repository.verifyDuplicate(item) }.getOrNull()
            _state.update { state ->
                state.copy(summary = state.summary.copy(items = PlanSelection.applyVerification(state.summary.items, id, verified)))
            }
        }
    }

    /**
     * Deep duplicate check (T1B.6): full verification of the largest
     * look-alike groups. Started by the user only, never during a scan.
     */
    fun runDeepDuplicateCheck() {
        val current = _state.value
        if (current.deepCheckRunning || current.scanning || current.cleanupInProgress) return
        _state.update { it.copy(deepCheckRunning = true) }
        viewModelScope.launch {
            val results = runSuspendCatching { repository.verifyLargeDuplicateGroups(current.summary.items) }
                .getOrDefault(emptyMap())
            _state.update { state ->
                state.copy(
                    deepCheckRunning = false,
                    summary = state.summary.copy(items = PlanSelection.applyVerifications(state.summary.items, results)),
                )
            }
        }
    }

}
