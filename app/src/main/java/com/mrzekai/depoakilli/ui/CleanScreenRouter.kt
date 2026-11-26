package com.mrzekai.depoakilli.ui

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    onDeepCheck: () -> Unit = {},
    onOpenFolder: (com.mrzekai.depoakilli.model.FolderSize) -> Unit = {},
    onOpenAppInfo: (String) -> Unit = {},
    onCloseSwipe: () -> Unit = {},
    autoOpenSuggestion: Boolean = false,
    onSuggestionOpened: () -> Unit = {},
    scrollToApps: Boolean = false,
    onScrolledToApps: () -> Unit = {},
    onOpenPaywall: () -> Unit = {},
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
            onSwipeGrid = { viewModel?.switchSwipeToGrid() },
            onSwipeFinished = { viewModel?.closeSwipeReview(clearMarks = true) },
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
            onVerifyAndSelect = { ids -> viewModel?.verifyAndSelectCopies(ids) },
            scrollToApps = scrollToApps,
            onScrolledToApps = onScrolledToApps,
            onExcludeFolder = { root, path -> viewModel?.excludeFolder(root, path) },
            onOpenPaywall = onOpenPaywall,
            onCleanupOrigin = { origin -> viewModel?.setNextCleanupOrigin(origin) },
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
        onCancelScan = { viewModel?.cancelScan() },
        onRefresh = { viewModel?.refreshToolScan() },
        onVerifyAndSelect = { ids -> viewModel?.verifyAndSelectCopies(ids) },
    )
}
