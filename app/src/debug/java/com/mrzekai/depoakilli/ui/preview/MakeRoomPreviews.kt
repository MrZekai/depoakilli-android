package com.mrzekai.depoakilli.ui.preview

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.mrzekai.depoakilli.data.TrashEntry
import com.mrzekai.depoakilli.model.AppPlan
import com.mrzekai.depoakilli.model.CleanCategory
import com.mrzekai.depoakilli.model.InstalledAppEntry
import com.mrzekai.depoakilli.model.PlanFigures
import com.mrzekai.depoakilli.model.StorageSnapshot
import com.mrzekai.depoakilli.model.buildPlan
import com.mrzekai.depoakilli.ui.CleanTab
import com.mrzekai.depoakilli.ui.CleanerUiState
import com.mrzekai.depoakilli.ui.CleanupResult
import com.mrzekai.depoakilli.ui.CleanupResultDialog
import com.mrzekai.depoakilli.ui.DeviceCenterScreen
import com.mrzekai.depoakilli.ui.HomeHardActions
import com.mrzekai.depoakilli.ui.SettingsDetailScreen
import com.mrzekai.depoakilli.ui.TrashScreen
import com.mrzekai.depoakilli.ui.smart.cleanupPlanItems
import com.mrzekai.depoakilli.ui.theme.DepoAkilliTheme

/**
 * Faz 5 checks for every main screen: the small phone (360×640 dp), a tall
 * phone (411×891 dp), large text (font scale 1.3) and Arabic (right to left).
 * Debug-only: the preview tooling is a debug dependency.
 */
@Preview(name = "360x640", widthDp = 360, heightDp = 640, showBackground = true)
@Preview(name = "411x891", widthDp = 411, heightDp = 891, showBackground = true)
@Preview(name = "font 1.3", widthDp = 360, heightDp = 640, fontScale = 1.3f, showBackground = true)
@Preview(name = "Arabic RTL", widthDp = 411, heightDp = 891, locale = "ar", showBackground = true)
annotation class MakeRoomPreviews

private const val GB = 1_000_000_000L

private val fullPhone = StorageSnapshot(totalBytes = 128 * GB, availableBytes = 14 * GB)

private val noHardActions = HomeHardActions(
    onOpenMedia = {},
    onOpenApps = {},
    onOpenFolder = {},
    onOpenDuplicates = {},
)

@Composable
private fun HomePreview(state: CleanerUiState) {
    DepoAkilliTheme {
        CleanTab(
            state = state,
            onAllowAccess = {},
            onCancelScan = {},
            onStartScan = {},
            onClean = {},
            onToggleItem = {},
            onOpenFindings = {},
            onOpenTrash = {},
            hardActions = noHardActions,
        )
    }
}

@MakeRoomPreviews
@Composable
private fun HomeFirstRunPreview() = HomePreview(CleanerUiState(storage = fullPhone))

@MakeRoomPreviews
@Composable
private fun HomeReadyPreview() = HomePreview(CleanerUiState(storage = fullPhone, hasAllFilesAccess = true))

@MakeRoomPreviews
@Composable
private fun HomeScanningPreview() = HomePreview(
    CleanerUiState(
        storage = fullPhone,
        hasAllFilesAccess = true,
        scanning = true,
        scanProgressFiles = 18_420,
        scanFoundByCategory = mapOf(
            CleanCategory.LARGE_FILE to 3 * GB,
            CleanCategory.OLD_DOWNLOAD to 840_000_000L,
        ),
    ),
)

/** D7: plan Home with the Trash line, on every preview size and text scale. */
@MakeRoomPreviews
@Composable
private fun HomePlanWithTrashPreview() = HomePreview(
    CleanerUiState(
        storage = fullPhone,
        hasAllFilesAccess = true,
        dashboardSnapshotAtMillis = 1L,
        dashboardCleanableBytes = 264_000_000L,
        dashboardReviewBytes = 18 * GB,
        trashEntries = listOf(trashEntry("t", "old-installer.apk", 185_000_000L, 1)),
    ),
)

@MakeRoomPreviews
@Composable
private fun ToolsPreview() {
    DepoAkilliTheme {
        DeviceCenterScreen(
            state = CleanerUiState(storage = fullPhone, hasAllFilesAccess = true),
            onScan = {},
            onRequestAllFilesAccess = {},
            onOpenWhatsApp = {},
            onOpenAppManager = {},
        )
    }
}

private fun trashEntry(id: String, name: String, size: Long, daysAgo: Long) = TrashEntry(
    id = id,
    originalPath = "/storage/emulated/0/Download/$name",
    trashPath = "/trash/$id",
    sizeBytes = size,
    modifiedAtMillis = 0L,
    deletedAtMillis = System.currentTimeMillis() - daysAgo * 86_400_000L,
    group = "OLD_DOWNLOADS",
)

@MakeRoomPreviews
@Composable
private fun TrashPreview() {
    DepoAkilliTheme {
        TrashScreen(
            entries = listOf(
                trashEntry("1", "holiday-video.mp4", 2 * GB, 1),
                trashEntry("2", "installer.apk", 120_000_000L, 4),
                trashEntry("3", "scan-2024.pdf", 8_000_000L, 6),
            ),
            onRefresh = {},
            onRestore = {},
            onDeleteForever = {},
        )
    }
}

@MakeRoomPreviews
@Composable
private fun TrashEmptyPreview() {
    DepoAkilliTheme {
        TrashScreen(entries = emptyList(), onRefresh = {}, onRestore = {}, onDeleteForever = {})
    }
}

@MakeRoomPreviews
@Composable
private fun SettingsPreview() {
    DepoAkilliTheme {
        SettingsDetailScreen(
            onOpenLanguageSettings = {},
            onRateApp = {},
            onSendFeedback = {},
            onShareApp = {},
            onOpenPrivacyAccess = {},
            onOpenLegalPage = {},
            hasAllFilesAccess = true,
        )
    }
}

@MakeRoomPreviews
@Composable
private fun ResultPreview() {
    DepoAkilliTheme {
        CleanupResultDialog(
            result = CleanupResult(
                deletedBytes = 0L,
                deletedCount = 42,
                failedCount = 0,
                trashedBytes = 3 * GB,
                trashEntryIds = listOf("1"),
            ),
            onSystemDismiss = {},
            onDone = {},
        )
    }
}

/** Faz 6 (E2): after "Free now", the deleted bytes, "freed" and the measured free-space pill. */
@MakeRoomPreviews
@Composable
private fun ResultFreedPreview() {
    DepoAkilliTheme {
        CleanupResultDialog(
            result = CleanupResult(
                deletedBytes = 3 * GB,
                deletedCount = 42,
                failedCount = 0,
                beforeAvailableBytes = 4 * GB,
                afterAvailableBytes = 7 * GB,
            ),
            onSystemDismiss = {},
            onDone = {},
        )
    }
}

private val previewApps = listOf(
    InstalledAppEntry("com.example.game", "Big Game", appBytes = 3 * GB, lastUsedMillis = 1L),
    InstalledAppEntry("com.example.chat", "Chat", appBytes = GB / 4, dataBytes = 2 * GB, lastUsedMillis = System.currentTimeMillis()),
    InstalledAppEntry("com.example.maps", "Maps", appBytes = GB, lastUsedMillis = System.currentTimeMillis()),
)

/** Faz 6 (E1): the plan's apps section, with and without Usage access. */
@MakeRoomPreviews
@Composable
private fun PlanAppsPreview() {
    DepoAkilliTheme {
        LazyColumn {
            cleanupPlanItems(
                plan = buildPlan(emptyList()),
                figures = PlanFigures(0L, 0L, 0L, 0L),
                onConfirmSuggested = {},
                onSeeSuggested = {},
                appCacheBytes = null,
                unusedApps = emptyList(),
                onOpenRow = {},
                onClearAppCaches = {},
                onOpenUnusedApps = {},
                onScanAgain = {},
                appRows = AppPlan.rows(previewApps, hasUsageAccess = true, ownPackage = "own"),
                showAppsNote = true,
            )
        }
    }
}

@MakeRoomPreviews
@Composable
private fun PlanAppsNoAccessPreview() {
    DepoAkilliTheme {
        LazyColumn {
            cleanupPlanItems(
                plan = buildPlan(emptyList()),
                figures = PlanFigures(0L, 0L, 0L, 0L),
                onConfirmSuggested = {},
                onSeeSuggested = {},
                appCacheBytes = null,
                unusedApps = null,
                onOpenRow = {},
                onClearAppCaches = {},
                onOpenUnusedApps = {},
                onScanAgain = {},
                appRows = null,
            )
        }
    }
}

/** Faz 6: plan Home with the apps line and the black Trash chip. */
@MakeRoomPreviews
@Composable
private fun HomePlanAppsPreview() = HomePreview(
    CleanerUiState(
        storage = fullPhone,
        hasAllFilesAccess = true,
        hasUsageAccess = true,
        installedApps = previewApps,
        dashboardSnapshotAtMillis = 1L,
        dashboardCleanableBytes = 264_000_000L,
        dashboardReviewBytes = 18 * GB,
        trashEntries = listOf(trashEntry("t", "old-installer.apk", 185_000_000L, 1)),
    ),
)
