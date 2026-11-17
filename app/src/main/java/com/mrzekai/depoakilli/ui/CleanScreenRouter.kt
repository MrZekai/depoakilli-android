package com.mrzekai.depoakilli.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mrzekai.depoakilli.ads.AdPolicy
import com.mrzekai.depoakilli.ads.ListNativeAdCard
import com.mrzekai.depoakilli.model.CleanCategory
import com.mrzekai.depoakilli.model.ScanFocus
import com.mrzekai.depoakilli.ui.tools.PremiumCleanerToolScreen

@Composable
internal fun CleanScreen(
    state: CleanerUiState,
    onRequestAllFilesAccess: () -> Unit,
    onScan: () -> Unit,
    onToggleItem: (String) -> Unit,
    onToggleCategory: (CleanCategory) -> Unit,
    onSetItemsSelected: (ids: Set<String>, selected: Boolean, bulk: Boolean) -> Unit,
    onOpenCategoryReview: (CleanCategory, Set<String>?) -> Unit,
    onOpenStorageReview: (com.mrzekai.depoakilli.model.StorageFileType, Boolean) -> Unit,
    onRequestUsageAccess: () -> Unit,
    onRefreshInstalledApps: () -> Unit,
    onUninstallApp: (String) -> Unit,
    onToggleStorageReviewItem: (String) -> Unit,
    onToggleAllStorageReviewItems: () -> Unit,
    onClean: (Set<String>?) -> Unit,
    onCleanStorage: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleGroup: (com.mrzekai.depoakilli.model.CleanupGroup) -> Unit = {},
    onClearAppCaches: () -> Unit = {},
    viewModel: CleanerViewModel? = null,
    canRequestAds: Boolean = false,
    onDeepCheck: () -> Unit = {},
    onOpenFolder: (com.mrzekai.depoakilli.model.FolderSize) -> Unit = {},
    onOpenAppInfo: (String) -> Unit = {},
    onSwipeConfirmed: () -> Unit = {},
    onCloseSwipe: () -> Unit = {},
    autoOpenSuggestion: Boolean = false,
    onSuggestionOpened: () -> Unit = {},
) {
    if (
        state.scanFocus == ScanFocus.SMART ||
        (state.scanFocus == ScanFocus.ANALYZE && state.storageReview.type != null)
    ) {
        SmartCleanResultsScreen(
            state = state,
            onRequestAllFilesAccess = onRequestAllFilesAccess,
            onScan = onScan,
            onToggleItem = onToggleItem,
            onToggleCategory = onToggleCategory,
            onSetItemsSelected = onSetItemsSelected,
            onOpenCategoryReview = onOpenCategoryReview,
            onOpenStorageReview = onOpenStorageReview,
            onRequestUsageAccess = onRequestUsageAccess,
            onRefreshInstalledApps = onRefreshInstalledApps,
            onUninstallApp = onUninstallApp,
            onToggleStorageReviewItem = onToggleStorageReviewItem,
            onToggleAllStorageReviewItems = onToggleAllStorageReviewItems,
            onClean = onClean,
            onCleanStorage = onCleanStorage,
            modifier = modifier,
            onToggleGroup = onToggleGroup,
            onClearAppCaches = onClearAppCaches,
            onOpenSwipeReview = { group -> viewModel?.openSwipeReview(group) },
            onOpenPlanGrid = { group, items -> viewModel?.openPlanGroupGrid(group, items) },
            onSwipeMark = { delete -> viewModel?.swipeMark(delete) },
            onSwipeUndo = { viewModel?.swipeUndo() },
            onSwipeContinue = { viewModel?.swipeContinue() },
            onSwipeGrid = { viewModel?.switchSwipeToGrid() },
            onSwipeFinished = {
                viewModel?.closeSwipeReview(clearMarks = true)
                onSwipeConfirmed()
            },
            listAd = { ListNativeAdCard(canRequestAds = canRequestAds) },
            onCancelScan = { viewModel?.cancelScan() },
            onDeepCheck = onDeepCheck,
            onOpenFolder = { folder ->
                viewModel?.openFolderReview(folder)
                onOpenFolder(folder)
            },
            onOpenAppInfo = onOpenAppInfo,
            onCloseSwipe = onCloseSwipe,
            autoOpenSuggestion = autoOpenSuggestion,
            onSuggestionOpened = onSuggestionOpened,
            onExcludeFolder = { root, path -> viewModel?.excludeFolder(root, path) },
            reviewAd = { index ->
                // C5: the small list native follows the 3rd findings row when there are at least 4 rows.
                val rows = state.summary.plan.askRows.size
                if (index == AdPolicy.NATIVE_FINDINGS_AFTER_ROW - 1 && rows >= AdPolicy.NATIVE_FINDINGS_MIN_ROWS) {
                    item(key = "plan-list-native") { ListNativeAdCard(canRequestAds = canRequestAds) }
                }
            },
        )
        return
    }

    if (state.scanFocus == ScanFocus.ANALYZE) {
        StorageAnalyzerScreen(
            state = state,
            onRequestAllFilesAccess = onRequestAllFilesAccess,
            onScan = onScan,
            onReviewType = { type -> onOpenStorageReview(type, false) },
            modifier = modifier,
            onRefresh = { viewModel?.refreshToolScan() },
            listAd = { ListNativeAdCard(canRequestAds = canRequestAds) },
        )
        return
    }

    // Every remaining focus is a tool scan (the old generic results list is removed, T3.1).
    PremiumCleanerToolScreen(
        state = state,
        onRequestAllFilesAccess = onRequestAllFilesAccess,
        onScan = onScan,
        onToggleItem = onToggleItem,
        onSetItemsSelected = onSetItemsSelected,
        onClean = onClean,
        modifier = modifier,
        listAd = { ListNativeAdCard(canRequestAds = canRequestAds) },
        onCancelScan = { viewModel?.cancelScan() },
        onRefresh = { viewModel?.refreshToolScan() },
    )
}
