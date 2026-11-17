package com.mrzekai.depoakilli.ui

import com.mrzekai.depoakilli.data.ExcludedFolder
import com.mrzekai.depoakilli.data.TrashEntry
import com.mrzekai.depoakilli.model.AppCacheSnapshot
import com.mrzekai.depoakilli.model.CleanCategory
import com.mrzekai.depoakilli.model.CleanupGroup
import com.mrzekai.depoakilli.model.DeviceInfoSnapshot
import com.mrzekai.depoakilli.model.InstalledAppEntry
import com.mrzekai.depoakilli.model.MemorySnapshot
import com.mrzekai.depoakilli.model.ScanFocus
import com.mrzekai.depoakilli.model.ScanSummary
import com.mrzekai.depoakilli.model.StorageReviewSummary
import com.mrzekai.depoakilli.model.StorageSnapshot
import com.mrzekai.depoakilli.model.WhatsAppLibrarySummary
import com.mrzekai.depoakilli.ui.review.SwipeReviewState

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

internal data class DeviceRefreshSnapshot(
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
    /**
     * Measured bytes per category once the files are classified (Faz 5 Home
     * "Found so far"); null until then. Exact duplicates arrive with the result.
     */
    val scanFoundByCategory: Map<CleanCategory, Long>? = null,
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
    /** Items that could not go to Trash; the user may delete them permanently. */
    val trashFallback: TrashFallback? = null,
    /** Files kept in the swipe review (V10). */
    val keptItemCount: Int = 0,
    /** Folders the user does not want suggestions from (V11). */
    val excludedFolders: List<ExcludedFolder> = emptyList(),
    val message: String? = null,
) {
    val trashBytes: Long get() = trashEntries.sumOf(TrashEntry::sizeBytes)
}

/**
 * Files a cleanup could not move to Trash. [folderMissing] means the Trash
 * folder itself cannot be created on this device (not a one-off failure).
 */
data class TrashFallback(
    val source: Source,
    val ids: Set<String>,
    val folderMissing: Boolean,
) {
    enum class Source { PLAN, WHATSAPP, STORAGE_REVIEW }
}
