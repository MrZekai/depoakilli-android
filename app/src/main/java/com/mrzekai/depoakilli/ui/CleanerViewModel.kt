package com.mrzekai.depoakilli.ui

import android.app.Application
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.data.DeviceRepository
import com.mrzekai.depoakilli.data.RestoreResult
import com.mrzekai.depoakilli.data.StoragePathRules
import com.mrzekai.depoakilli.data.TrashEntry
import com.mrzekai.depoakilli.diagnostics.AppDiagnostics
import com.mrzekai.depoakilli.model.AppCacheSnapshot
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.model.CleanCategory
import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.model.CleanupGroup
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
import com.mrzekai.depoakilli.ui.review.SwipeReviewState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CleanupResultKind {
    FILES,
    SYSTEM_CACHE,
    APP_CACHE,
}

data class CleanupResult(
    val deletedBytes: Long,
    val deletedCount: Int,
    val failedCount: Int,
    val cancelledCount: Int = 0,
    val beforeAvailableBytes: Long = 0L,
    val afterAvailableBytes: Long = 0L,
    val kind: CleanupResultKind = CleanupResultKind.FILES,
    val operationSucceeded: Boolean = true,
    val subjectLabel: String? = null,
    /** Bytes moved to the in-app trash; not included in [deletedBytes] (not freed yet). */
    val trashedBytes: Long = 0L,
    /** Trash entries created by this cleanup, used by Undo. */
    val trashEntryIds: List<String> = emptyList(),
    /** Items skipped because they changed or disappeared after the scan. */
    val changedCount: Int = 0,
    /** Items put back with Undo. */
    val restoredCount: Int = 0,
)

private data class DeviceRefreshSnapshot(
    val storage: StorageSnapshot,
    val memory: MemorySnapshot,
    val ownCacheBytes: Long,
    val hasAllFilesAccess: Boolean,
    val hasUsageAccess: Boolean,
    val hasWhatsAppAccess: Boolean,
    val deviceInfo: DeviceInfoSnapshot,
)

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
    val dashboardScannedFileCount: Int = 0,
    val dashboardScannedBytes: Long = 0L,
    val dashboardSnapshotAtMillis: Long = 0L,
    val dashboardRefreshing: Boolean = false,
    val smartCategoryReview: CleanCategory? = null,
    val smartCategoryReviewIds: Set<String>? = null,
    val storageReview: StorageReviewSummary = StorageReviewSummary(),
    val storageReviewProgressFiles: Int = 0,
    val storageReviewProgressDirectories: Int = 0,
    val scanning: Boolean = false,
    val scanProgressFiles: Int = 0,
    val scanProgressDirectories: Int = 0,
    val scanningAppCaches: Boolean = false,
    val loadingApps: Boolean = false,
    val cleanupInProgress: Boolean = false,
    val cleanupResult: CleanupResult? = null,
    val cleanupHistory: CleanupHistorySnapshot = CleanupHistorySnapshot(),
    val storageChange: StorageChangeReport = StorageChangeReport(),
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
    val trashEntries: List<TrashEntry> = emptyList(),
    /** Full-content duplicate check in progress during a scan. */
    val scanHashing: Boolean = false,
    val scanHashDoneBytes: Long = 0L,
    val scanHashTotalBytes: Long = 0L,
    /** The Smart plan, kept while a tool scan temporarily owns [summary]. */
    val planSummary: ScanSummary? = null,
    /** When the current Smart plan finished scanning (elapsed realtime), 0 when none. */
    val planScannedAtElapsed: Long = 0L,
    /** Group shown in the swipe review, null when closed. */
    val swipeReviewGroup: CleanupGroup? = null,
    /** Swipe marks per group; kept when the review is left and reopened. */
    val swipeStates: Map<CleanupGroup, SwipeReviewState> = emptyMap(),
    /** Plan group behind the open grid review, used for the grid/swipe toggle. */
    val smartCategoryReviewGroup: CleanupGroup? = null,
    /** "Deep duplicate check" (full hash of large look-alikes) in progress. */
    val deepCheckRunning: Boolean = false,
    /** Items that could not go to Trash (e.g. storage full); the user may delete them permanently. */
    val trashFallbackIds: Set<String> = emptySet(),
    val message: String? = null,
) {
    val trashBytes: Long get() = trashEntries.sumOf(TrashEntry::sizeBytes)
}

class CleanerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DeviceRepository(application)
    private val dashboardSnapshotStore = DashboardSnapshotStore(application)
    private val cleanupHistoryStore = CleanupHistoryStore(application)
    private val storageChangeStore = StorageChangeStore(application)
    private val _state = MutableStateFlow(CleanerUiState())
    private var pendingConsentItems: List<CleanableItem> = emptyList()
    private var pendingCleanupDeletedBytes: Long = 0L
    private var pendingCleanupDeletedCount: Int = 0
    private var pendingCleanupFailedCount: Int = 0
    private var pendingCleanupStorageBeforeBytes: Long = 0L
    private var pendingDeepCacheBeforeBytes: Long = 0L
    private var pendingDeepCacheStorageBeforeBytes: Long = 0L
    private var pendingIndividualCachePackage: String? = null
    private var pendingIndividualCacheLabel: String? = null
    private var pendingIndividualCacheBeforeBytes: Long = 0L
    private var pendingIndividualCacheStorageBeforeBytes: Long = 0L
    private var deviceRefreshJob: Job? = null
    private var appCacheRefreshJob: Job? = null
    private var installedAppsRefreshJob: Job? = null
    private var storageReviewJob: Job? = null
    private var storageReviewGeneration = 0L
    private var lastDeviceRefreshAt = 0L
    private var lastAppCacheRefreshAt = 0L
    private var captureAppCacheForStorageChange = false

    val state: StateFlow<CleanerUiState> = _state.asStateFlow()

    // Storage and cache probing is disk I/O: StatFs on every mounted volume and
    // a recursive walk of the app cache directories. viewModelScope dispatches
    // on Dispatchers.Main.immediate, so these helpers keep the cleanup and scan
    // paths off the UI thread instead of risking StrictMode violations and ANRs
    // on slow or heavily populated storage.
    private suspend fun ioStorageSnapshot(): StorageSnapshot =
        withContext(Dispatchers.IO) { repository.storageSnapshot() }

    private suspend fun ioAvailableBytes(): Long = ioStorageSnapshot().availableBytes

    private suspend fun ioOwnCacheBytes(): Long =
        withContext(Dispatchers.IO) { repository.ownCacheSize() }

    private suspend fun ioMemorySnapshot(): MemorySnapshot =
        withContext(Dispatchers.Default) { repository.memorySnapshot() }

    init {
        restoreDashboardSnapshot()
        restoreCleanupHistory()
        restoreStorageChange()
        refreshDeviceState()
        refreshAppCaches()
        purgeExpiredTrash()
    }

    /** Deletes trash entries older than the retention period, then refreshes the trash list. */
    fun purgeExpiredTrash() {
        viewModelScope.launch {
            runCatching { repository.purgeExpiredTrash() }
            refreshTrash()
        }
    }

    fun refreshTrash() {
        viewModelScope.launch {
            val entries = runCatching { withContext(Dispatchers.IO) { repository.trashEntries() } }
                .getOrDefault(emptyList())
            _state.update { it.copy(trashEntries = entries) }
        }
    }

    /** Puts the files of the shown cleanup back where they were. */
    fun undoCleanup() {
        val result = _state.value.cleanupResult ?: return
        if (result.trashEntryIds.isEmpty()) return
        restoreTrash(result.trashEntryIds) { restored ->
            _state.update { state ->
                val current = state.cleanupResult ?: return@update state
                val remaining = current.trashEntryIds.filterNot { it in restored.restoredIds }
                state.copy(
                    cleanupResult = current.copy(
                        trashEntryIds = remaining,
                        trashedBytes = (current.trashedBytes - restored.restoredBytes).coerceAtLeast(0L),
                        restoredCount = current.restoredCount + restored.restoredIds.size,
                    ),
                )
            }
        }
    }

    fun restoreTrash(ids: Collection<String>, onDone: (RestoreResult) -> Unit = {}) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val result = runCatching { repository.restoreFromTrash(ids) }.getOrNull() ?: return@launch
            val entries = withContext(Dispatchers.IO) { repository.trashEntries() }
            _state.update { it.copy(trashEntries = entries) }
            onDone(result)
        }
    }

    /** Permanently deletes trash entries (all when [ids] is null) and reports the measured result. */
    fun emptyTrash(ids: Collection<String>? = null, onDone: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val before = ioAvailableBytes()
            val freed = runCatching { repository.emptyTrash(ids) }.getOrDefault(0L)
            val entries = withContext(Dispatchers.IO) { repository.trashEntries() }
            val storageAfter = ioStorageSnapshot()
            _state.update { state ->
                val current = state.cleanupResult
                state.copy(
                    trashEntries = entries,
                    storage = storageAfter,
                    cleanupResult = current?.let { result ->
                        val gone = result.trashEntryIds.filterNot { id -> entries.any { it.id == id } }
                        if (gone.isEmpty()) {
                            result
                        } else {
                            result.copy(
                                trashEntryIds = result.trashEntryIds - gone.toSet(),
                                trashedBytes = (result.trashedBytes - freed).coerceAtLeast(0L),
                                deletedBytes = result.deletedBytes + freed,
                                afterAvailableBytes = storageAfter.availableBytes,
                                beforeAvailableBytes = if (result.beforeAvailableBytes > 0L) result.beforeAvailableBytes else before,
                            )
                        }
                    },
                )
            }
            onDone(freed)
        }
    }

    private fun restoreDashboardSnapshot() {
        val snapshot = dashboardSnapshotStore.load() ?: return
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

    private fun persistDashboardSnapshot() {
        val state = _state.value
        if (state.dashboardSnapshotAtMillis <= 0L) return
        dashboardSnapshotStore.save(
            DashboardSnapshot(
                cleanableBytes = state.dashboardCleanableBytes,
                reviewBytes = state.dashboardReviewBytes,
                categoryBytes = state.dashboardCategoryBytes,
                scannedFileCount = state.dashboardScannedFileCount,
                scannedBytes = state.dashboardScannedBytes,
                analyzedAtMillis = state.dashboardSnapshotAtMillis,
            ),
        )
    }

    private fun restoreCleanupHistory() {
        _state.update {
            it.copy(cleanupHistory = cleanupHistoryStore.load())
        }
    }

    private fun restoreStorageChange() {
        _state.update {
            it.copy(storageChange = storageChangeStore.load())
        }
    }

    private fun recordCleanupHistory(result: CleanupResult) {
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
        val updated = cleanupHistoryStore.record(
            deletedBytes = result.deletedBytes,
            deletedCount = result.deletedCount,
        )
        _state.update {
            it.copy(cleanupHistory = updated)
        }
    }

    fun refreshDeviceState(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && deviceRefreshJob?.isActive == true) return
        if (
            !force &&
            lastDeviceRefreshAt != 0L &&
            now - lastDeviceRefreshAt < DEVICE_REFRESH_INTERVAL_MILLIS
        ) {
            return
        }

        if (force) deviceRefreshJob?.cancel()

        deviceRefreshJob = viewModelScope.launch {
            runCatching {
                coroutineScope {
                    val storage = async(Dispatchers.Default) { repository.storageSnapshot() }
                    val memory = async(Dispatchers.Default) { repository.memorySnapshot() }
                    val ownCache = async(Dispatchers.IO) { repository.ownCacheSize() }
                    val allFiles = async(Dispatchers.Default) { repository.hasAllFilesAccess() }
                    val usage = async(Dispatchers.Default) { repository.hasUsageAccess() }
                    val whatsApp = async(Dispatchers.Default) { repository.hasWhatsAppAccess() }
                    val deviceInfo = async(Dispatchers.Default) { repository.deviceInfoSnapshot() }

                    DeviceRefreshSnapshot(
                        storage = storage.await(),
                        memory = memory.await(),
                        ownCacheBytes = ownCache.await(),
                        hasAllFilesAccess = allFiles.await(),
                        hasUsageAccess = usage.await(),
                        hasWhatsAppAccess = whatsApp.await(),
                        deviceInfo = deviceInfo.await(),
                    )
                }
            }.onSuccess { snapshot ->
                lastDeviceRefreshAt = SystemClock.elapsedRealtime()
                _state.update {
                    it.copy(
                        storage = snapshot.storage,
                        memory = snapshot.memory,
                        ownCacheBytes = snapshot.ownCacheBytes,
                        hasAllFilesAccess = snapshot.hasAllFilesAccess,
                        hasUsageAccess = snapshot.hasUsageAccess,
                        hasWhatsAppAccess = snapshot.hasWhatsAppAccess,
                        deviceInfo = snapshot.deviceInfo,
                    )
                }
            }
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

                    val storageChangeReport =
                        if (captureAppCacheForStorageChange) {
                            captureAppCacheForStorageChange = false
                            storageChangeStore.updateCurrentAppCaches(snapshot)
                        } else {
                            null
                        }

                    _state.update { current ->
                        current.copy(
                            appCache = snapshot,
                            scanningAppCaches = false,
                            hasUsageAccess = repository.hasUsageAccess(),
                            storageChange = storageChangeReport ?: current.storageChange,
                        )
                    }
                }
                .onFailure {
                    captureAppCacheForStorageChange = false
                    _state.update { it.copy(scanningAppCaches = false) }
                }
        }
    }

    fun refreshInstalledApps() {
        if (_state.value.loadingApps) return
        _state.update { it.copy(loadingApps = true) }
        installedAppsRefreshJob = viewModelScope.launch {
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

    private var scanJob: Job? = null

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

    fun scanWhatsAppLibrary() {
        if (_state.value.whatsAppScanning) return
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
        _state.update {
            it.copy(
                cleanupInProgress = true,
                cleanupResult = null,
                message = null,
            )
        }
        viewModelScope.launch {
            val storageBefore = ioAvailableBytes()
            runCatching { repository.deleteWhatsAppItems(selected) }
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

    fun openSmartCategoryReview(category: CleanCategory, itemIds: Set<String>? = null) {
        _state.update {
            it.copy(
                smartCategoryReview = category,
                smartCategoryReviewIds = itemIds,
            )
        }
    }

    fun closeSmartCategoryReview() {
        _state.update {
            it.copy(
                smartCategoryReview = null,
                smartCategoryReviewIds = null,
                smartCategoryReviewGroup = null,
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
            runCatching {
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
                        state.copy(
                            message = getApplication<Application>().getString(R.string.message_scan_failed),
                        )
                    } else {
                        state
                    }
                }
            }
        }
    }

    private fun isWhatsAppIndexedPath(relativePath: String): Boolean =
        StoragePathRules.isWhatsAppPath(relativePath)

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

    fun dismissTrashFallback() {
        _state.update { it.copy(trashFallbackIds = emptySet()) }
    }

    /**
     * Second, explicit confirmation path: files that could not be moved to
     * Trash are deleted permanently only after the user confirmed that.
     */
    fun deleteTrashFallbackPermanently() {
        val ids = _state.value.trashFallbackIds
        _state.update { it.copy(trashFallbackIds = emptySet()) }
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
            val items = state.summary.items.filter { it.assessment.group == group }
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

    fun swipeMark(delete: Boolean) = updateSwipe { it.mark(delete) }

    fun swipeUndo() = updateSwipe { it.undo() }

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
        resetPendingCleanupResult()
        _state.update {
            it.copy(
                cleanupInProgress = true,
                cleanupResult = null,
                message = null,
            )
        }
        viewModelScope.launch {
            pendingCleanupStorageBeforeBytes = ioAvailableBytes()
            runCatching {
                val direct = repository.deleteDirectItems(selected)
                val remaining = selected.filterNot { it.id in direct.attemptedIds }
                val plan = repository.createDeleteRequest(remaining)
                if (plan is DeviceRepository.DeletePlan.NoMediaFiles) {
                    finishCleanup(
                        items = selected,
                        deletedIds = direct.deletedIds,
                        deletedBytes = direct.deletedBytes,
                        failedCount = direct.failedCount,
                        trashedBytes = direct.trashedBytes,
                        trashEntryIds = direct.trashEntryIds,
                        changedCount = direct.changedCount,
                    )
                    refreshTrash()
                    if (direct.trashUnavailableIds.isNotEmpty()) {
                        _state.update { it.copy(trashFallbackIds = direct.trashUnavailableIds) }
                    }
                    onCleanupCompleted(direct.deletedIds.isNotEmpty())
                } else {
                    pendingConsentItems = remaining
                    pendingCleanupDeletedBytes = direct.deletedBytes
                    pendingCleanupDeletedCount = direct.deletedIds.size
                    pendingCleanupFailedCount = direct.failedCount
                    finishCleanup(
                        items = selected.filter { it.id in direct.attemptedIds },
                        deletedIds = direct.deletedIds,
                        deletedBytes = direct.deletedBytes,
                        failedCount = direct.failedCount,
                        clearPending = false,
                    )
                    onPlanReady(plan)
                }
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                pendingConsentItems = emptyList()
                resetPendingCleanupResult()
                _state.update { state ->
                    state.copy(
                        cleanupInProgress = false,
                        message = getApplication<Application>().getString(R.string.message_cleanup_failed),
                    )
                }
            }
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

        _state.update {
            it.copy(
                cleanupInProgress = true,
                cleanupResult = null,
                message = null,
            )
        }
        viewModelScope.launch {
            val storageBefore = ioAvailableBytes()
            runCatching { repository.deleteStorageReviewItems(selected) }
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

    fun completeCleanup(approved: Boolean): Boolean {
        val pending = pendingConsentItems
        pendingConsentItems = emptyList()
        val directDeletedCount = pendingCleanupDeletedCount
        val directDeletedBytes = pendingCleanupDeletedBytes
        val directFailedCount = pendingCleanupFailedCount
        val storageBefore = pendingCleanupStorageBeforeBytes

        if (!approved) {
            resetPendingCleanupResult()
            viewModelScope.launch {
                val partialResult = if (directDeletedCount > 0 || directFailedCount > 0) {
                    CleanupResult(
                        deletedBytes = directDeletedBytes,
                        deletedCount = directDeletedCount,
                        failedCount = directFailedCount,
                        cancelledCount = pending.size,
                        beforeAvailableBytes = storageBefore,
                        afterAvailableBytes = ioAvailableBytes(),
                    )
                } else {
                    null
                }
                _state.update {
                    it.copy(
                        cleanupInProgress = false,
                        cleanupResult = partialResult,
                        message = if (partialResult == null) {
                            getApplication<Application>().getString(R.string.message_cleanup_cancelled)
                        } else {
                            null
                        },
                    )
                }
                partialResult?.let(::recordCleanupHistory)
            }
            // A cancelled Android consent flow never triggers a monetization break.
            return false
        }

        repository.invalidateStorageIndex()
        val removedIds = pending.mapTo(hashSetOf(), CleanableItem::id)
        val removedBytes = pending.sumOf(CleanableItem::sizeBytes)
        resetPendingCleanupResult()
        viewModelScope.launch {
            val combinedResult = CleanupResult(
                deletedBytes = directDeletedBytes + removedBytes,
                deletedCount = directDeletedCount + removedIds.size,
                failedCount = directFailedCount,
                beforeAvailableBytes = storageBefore,
                afterAvailableBytes = ioAvailableBytes(),
            )
            finishCleanup(
                items = pending,
                deletedIds = removedIds,
                deletedBytes = removedBytes,
                failedCount = 0,
                storageBeforeBytes = storageBefore,
                result = combinedResult,
            )
        }
        return directDeletedCount + removedIds.size > 0
    }

    fun refreshAfterCleanup() {
        refreshDeviceState()
        val focus = _state.value.scanFocus
        if (repository.hasAllFilesAccess() && _state.value.lastScanCompleted) {
            scan(focus)
        }
    }

    private suspend fun finishCleanup(
        items: List<CleanableItem>,
        deletedIds: Set<String>,
        deletedBytes: Long,
        failedCount: Int,
        storageBeforeBytes: Long = pendingCleanupStorageBeforeBytes,
        clearPending: Boolean = true,
        result: CleanupResult? = null,
        trashedBytes: Long = 0L,
        trashEntryIds: List<String> = emptyList(),
        changedCount: Int = 0,
    ) {
        val storageAfter = ioStorageSnapshot()
        val memoryAfter = ioMemorySnapshot()
        val ownCacheAfter = ioOwnCacheBytes()
        val measuredResult = if (clearPending) {
            result ?: CleanupResult(
                deletedBytes = deletedBytes,
                deletedCount = deletedIds.size,
                failedCount = failedCount,
                beforeAvailableBytes = storageBeforeBytes,
                afterAvailableBytes = storageAfter.availableBytes,
                trashedBytes = trashedBytes,
                trashEntryIds = trashEntryIds,
                changedCount = changedCount,
            )
        } else {
            null
        }
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
            val removedUris = deletedItems.mapTo(hashSetOf(), CleanableItem::uri)
            state.copy(
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
                cleanupInProgress = if (clearPending) false else state.cleanupInProgress,
                cleanupResult = measuredResult ?: state.cleanupResult,
                message = if (measuredResult == null) state.message else null,
            )
        }
        persistDashboardSnapshot()
        measuredResult?.let(::recordCleanupHistory)
        if (clearPending) {
            pendingConsentItems = emptyList()
            resetPendingCleanupResult()
        }
    }

    fun dismissCleanupResult() {
        _state.update { it.copy(cleanupResult = null) }
    }

    private fun resetPendingCleanupResult() {
        pendingCleanupDeletedBytes = 0L
        pendingCleanupDeletedCount = 0
        pendingCleanupFailedCount = 0
        pendingCleanupStorageBeforeBytes = 0L
    }

    private suspend fun finishStorageReviewCleanup(
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

    fun beginDeepCacheCleanupMeasurement() {
        val current = _state.value
        pendingDeepCacheBeforeBytes = current.appCache.totalCacheBytes.coerceAtLeast(0L)
        pendingDeepCacheStorageBeforeBytes = current.storage.availableBytes.coerceAtLeast(0L)
    }

    fun onDeepCacheCleanupResult(approved: Boolean) {
        val beforeCacheBytes = pendingDeepCacheBeforeBytes
        val beforeAvailableBytes = pendingDeepCacheStorageBeforeBytes
        pendingDeepCacheBeforeBytes = 0L
        pendingDeepCacheStorageBeforeBytes = 0L

        if (!approved) {
            showMessage(R.string.message_cache_system_unavailable)
            refreshDeviceState(force = true)
            refreshAppCaches(force = true)
            return
        }

        appCacheRefreshJob?.cancel()
        _state.update { it.copy(scanningAppCaches = true, message = null) }

        viewModelScope.launch {
            val afterCache = runCatching { repository.appCacheSnapshot() }.getOrNull()
            val storageAfter = runCatching { ioStorageSnapshot() }
                .getOrElse { _state.value.storage }

            val measuredCacheReduction =
                if (beforeCacheBytes > 0L && afterCache != null) {
                    (beforeCacheBytes - afterCache.totalCacheBytes).coerceAtLeast(0L)
                } else {
                    0L
                }

            if (afterCache != null) {
                lastAppCacheRefreshAt = SystemClock.elapsedRealtime()
            }

            _state.update { current ->
                current.copy(
                    appCache = afterCache ?: current.appCache,
                    storage = storageAfter,
                    scanningAppCaches = false,
                    cleanupResult = if (measuredCacheReduction > 0L) CleanupResult(
                        deletedBytes = measuredCacheReduction,
                        deletedCount = 0,
                        failedCount = 0,
                        beforeAvailableBytes = beforeAvailableBytes,
                        afterAvailableBytes = storageAfter.availableBytes,
                        kind = CleanupResultKind.SYSTEM_CACHE,
                        operationSucceeded = true,
                    ) else null,
                    message = if (measuredCacheReduction == 0L) {
                        getApplication<Application>().getString(R.string.message_cache_system_unavailable)
                    } else null,
                )
            }
        }
    }

    fun beginIndividualAppCacheMeasurement(packageName: String) {
        val current = _state.value
        val app = current.appCache.entries.firstOrNull { it.packageName == packageName }

        pendingIndividualCachePackage = packageName
        pendingIndividualCacheLabel = app?.label ?: packageName
        pendingIndividualCacheBeforeBytes = app?.cacheBytes?.coerceAtLeast(0L) ?: 0L
        pendingIndividualCacheStorageBeforeBytes = current.storage.availableBytes.coerceAtLeast(0L)

        _state.update { it.copy(cleanupResult = null, message = null) }
    }

    fun cancelIndividualAppCacheMeasurement() {
        pendingIndividualCachePackage = null
        pendingIndividualCacheLabel = null
        pendingIndividualCacheBeforeBytes = 0L
        pendingIndividualCacheStorageBeforeBytes = 0L
    }

    fun onIndividualAppCacheSettingsReturned() {
        val packageName = pendingIndividualCachePackage
        val appLabel = pendingIndividualCacheLabel
        val beforeCacheBytes = pendingIndividualCacheBeforeBytes
        val beforeAvailableBytes = pendingIndividualCacheStorageBeforeBytes

        cancelIndividualAppCacheMeasurement()

        if (packageName.isNullOrBlank()) {
            refreshAppCaches(force = true)
            return
        }

        appCacheRefreshJob?.cancel()
        _state.update { it.copy(scanningAppCaches = true) }

        viewModelScope.launch {
            val afterCache = runCatching {
                repository.appCacheSnapshot()
            }.getOrNull()

            val storageAfter = runCatching {
                ioStorageSnapshot()
            }.getOrElse {
                _state.value.storage
            }

            val afterCacheBytes = afterCache
                ?.entries
                ?.firstOrNull { it.packageName == packageName }
                ?.cacheBytes
                ?.coerceAtLeast(0L)
                ?: 0L

            val measuredReduction =
                if (beforeCacheBytes > 0L && afterCache != null) {
                    (beforeCacheBytes - afterCacheBytes).coerceAtLeast(0L)
                } else {
                    0L
                }

            if (afterCache != null) {
                lastAppCacheRefreshAt = SystemClock.elapsedRealtime()
            }

            _state.update { current ->
                current.copy(
                    appCache = afterCache ?: current.appCache,
                    storage = storageAfter,
                    scanningAppCaches = false,
                    cleanupResult = if (measuredReduction > 0L) {
                        CleanupResult(
                            deletedBytes = measuredReduction,
                            deletedCount = 0,
                            failedCount = 0,
                            beforeAvailableBytes = beforeAvailableBytes,
                            afterAvailableBytes = storageAfter.availableBytes,
                            kind = CleanupResultKind.APP_CACHE,
                            subjectLabel = appLabel,
                        )
                    } else {
                        null
                    },
                    message = if (measuredReduction > 0L) {
                        null
                    } else {
                        getApplication<Application>().getString(
                            R.string.message_individual_cache_no_change,
                        )
                    },
                )
            }
        }
    }

    fun clearOwnAppCache() {
        viewModelScope.launch {
            val beforeAvailableBytes = ioAvailableBytes()
            val clearedBytes = repository.clearOwnCache().coerceAtLeast(0L)
            val storageAfter = ioStorageSnapshot()
            val ownCacheAfter = ioOwnCacheBytes()

            _state.update { current ->
                if (clearedBytes > 0L) {
                    current.copy(
                        ownCacheBytes = ownCacheAfter,
                        storage = storageAfter,
                        cleanupResult = CleanupResult(
                            deletedBytes = clearedBytes,
                            deletedCount = 0,
                            failedCount = 0,
                            beforeAvailableBytes = beforeAvailableBytes,
                            afterAvailableBytes = storageAfter.availableBytes,
                            kind = CleanupResultKind.APP_CACHE,
                            subjectLabel = getApplication<Application>().getString(R.string.app_name),
                        ),
                        message = null,
                    )
                } else {
                    current.copy(
                        ownCacheBytes = ownCacheAfter,
                        storage = storageAfter,
                        cleanupResult = null,
                        message = getApplication<Application>().getString(
                            R.string.message_cache_already_empty,
                        ),
                    )
                }
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
        const val WHATSAPP_COMPLETION_DELAY_MILLIS = 80L
        const val DEVICE_REFRESH_INTERVAL_MILLIS = 1_500L
        const val APP_CACHE_REFRESH_INTERVAL_MILLIS = 60L * 1000L
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
    ): Boolean {
        if (!hasAccess || scanning) return false
        if (!hasPlan || planScannedAtElapsed <= 0L) return true
        return nowElapsed - planScannedAtElapsed > java.util.concurrent.TimeUnit.HOURS.toMillis(QUICK_SCAN_MAX_AGE_HOURS)
    }
}
