package com.mrzekai.depoakilli.ui

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.AppPlan
import com.mrzekai.depoakilli.model.CleanCategory
import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.model.CleanupGroup
import com.mrzekai.depoakilli.model.FolderSize
import com.mrzekai.depoakilli.model.IndexedFile
import com.mrzekai.depoakilli.model.InstalledAppEntry
import com.mrzekai.depoakilli.model.PlanFigures
import com.mrzekai.depoakilli.model.ScanSummary
import com.mrzekai.depoakilli.model.StorageFileType
import com.mrzekai.depoakilli.model.StorageReviewSummary
import com.mrzekai.depoakilli.model.swipeItemsFor
import com.mrzekai.depoakilli.ui.common.DeleteConfirmDialog
import com.mrzekai.depoakilli.ui.common.PageFrame
import com.mrzekai.depoakilli.ui.media.MediaPreviewFile
import com.mrzekai.depoakilli.ui.review.SwipeReviewScreen
import com.mrzekai.depoakilli.ui.review.SwipeReviewState
import com.mrzekai.depoakilli.ui.smart.*
import com.mrzekai.depoakilli.ui.smart.PLAN_APPS_KEY
import com.mrzekai.depoakilli.ui.smart.cleanupPlanItems

/** Faz 5 palette for the smart screens: Lime page, black cards, Lime accent. */
internal val SmartBackground = com.mrzekai.depoakilli.ui.theme.Lime
internal val SmartCard = com.mrzekai.depoakilli.ui.theme.Black
internal val SmartCardAlt = com.mrzekai.depoakilli.ui.theme.BlackChip
internal val SmartTextSecondary = com.mrzekai.depoakilli.ui.theme.OnBlackSoft
internal val SmartCyan = com.mrzekai.depoakilli.ui.theme.Lime
internal val SmartGreen = com.mrzekai.depoakilli.ui.theme.Lime

internal data class SmartPreviewFile(
    val uri: String,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val modifiedAtMillis: Long,
    val relativePath: String,
)

internal fun CleanableItem.toSmartPreview() = SmartPreviewFile(
    uri = uri,
    name = name,
    sizeBytes = sizeBytes,
    mimeType = mimeType,
    modifiedAtMillis = modifiedAtMillis,
    relativePath = relativePath,
)

internal fun SmartPreviewFile.toMediaPreview() = MediaPreviewFile(
    uri = uri,
    name = name,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
)

internal fun IndexedFile.toSmartPreview() = SmartPreviewFile(
    uri = uri,
    name = name,
    sizeBytes = sizeBytes,
    mimeType = mimeType,
    modifiedAtMillis = modifiedAtMillis,
    relativePath = relativePath,
)

@Composable
internal fun SmartCleanResultsScreen(
    state: CleanerUiState,
    onRequestAllFilesAccess: () -> Unit,
    onScan: () -> Unit,
    onToggleItem: (String) -> Unit,
    onToggleCategory: (CleanCategory) -> Unit,
    onSetItemsSelected: (ids: Set<String>, selected: Boolean, bulk: Boolean) -> Unit,
    onOpenCategoryReview: (CleanCategory, Set<String>?) -> Unit,
    onOpenStorageReview: (StorageFileType, Boolean) -> Unit,
    onRequestUsageAccess: () -> Unit,
    onRefreshInstalledApps: () -> Unit,
    onUninstallApp: (String) -> Unit,
    onToggleStorageReviewItem: (String) -> Unit,
    onToggleAllStorageReviewItems: () -> Unit,
    onClean: (Set<String>?) -> Unit,
    onCleanStorage: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleGroup: (CleanupGroup) -> Unit = {},
    onClearAppCaches: () -> Unit = {},
    planHeader: LazyListScope.() -> Unit = {},
    reviewAd: LazyListScope.(Int) -> Unit = {},
    onCancelScan: () -> Unit = {},
    onOpenSwipeReview: (CleanupGroup) -> Unit = {},
    onOpenPlanGrid: (CleanupGroup?, List<CleanableItem>) -> Unit = { _, items ->
        if (items.isNotEmpty()) {
            onOpenCategoryReview(items.first().assessment.category, items.mapTo(linkedSetOf(), CleanableItem::id))
        }
    },
    onSwipeMark: (Boolean) -> Unit = {},
    onSwipeUndo: () -> Unit = {},
    onSwipeContinue: () -> Unit = {},
    onSwipeGrid: () -> Unit = {},
    onSwipeFinished: () -> Unit = {},
    listAd: @Composable () -> Unit = {},
    onDeepCheck: () -> Unit = {},
    onOpenFolder: (FolderSize) -> Unit = {},
    onOpenAppInfo: (String) -> Unit = {},
    onExcludeFolder: (volumeRoot: String, relativePath: String) -> Unit = { _, _ -> },
    onCloseSwipe: () -> Unit = {},
    autoOpenSuggestion: Boolean = false,
    onSuggestionOpened: () -> Unit = {},
    onVerifyAndSelect: (Set<String>) -> Unit = {},
    scrollToApps: Boolean = false,
    onScrolledToApps: () -> Unit = {},
) {
    val swipeGroup = state.swipeReviewGroup
    val swipe = swipeGroup?.let(state.swipeStates::get)
    if (swipe != null && state.hasAllFilesAccess && !state.scanning) {
        SwipeReviewScreen(
            review = swipe,
            items = swipeItemsFor(swipeGroup, state.summary.items),
            onMark = onSwipeMark,
            onUndo = onSwipeUndo,
            onContinue = onSwipeContinue,
            onShowGrid = onSwipeGrid,
            onConfirmClean = { ids ->
                onSetItemsSelected(ids, true, false)
                onClean(ids)
                onSwipeFinished()
            },
            modifier = modifier,
            groupTitle = stringResource(swipeGroup.titleRes),
            onClose = onCloseSwipe,
            breakCardContent = listAd,
        )
        return
    }
    when {
        !state.hasAllFilesAccess -> SmartAccessRequired(
            onRequest = onRequestAllFilesAccess,
            modifier = modifier,
        )

        state.scanning -> SmartScanningState(state = state, onCancel = onCancelScan, modifier = modifier)
        !state.lastScanCompleted -> SmartEmptyState(onScan = onScan, modifier = modifier)
        else -> SmartResultsContent(
            summary = state.summary,
            activeCategoryReview = state.smartCategoryReview,
            activeCategoryReviewIds = state.smartCategoryReviewIds,
            installedApps = state.installedApps,
            loadingApps = state.loadingApps,
            hasUsageAccess = state.hasUsageAccess,
            storageReview = state.storageReview,
            storageReviewProgressFiles = state.storageReviewProgressFiles,
            storageReviewProgressDirectories = state.storageReviewProgressDirectories,
            cleanupInProgress = state.cleanupInProgress,
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
            appCacheBytes = state.appCache.totalCacheBytes.takeIf { state.hasUsageAccess },
            onToggleGroup = onToggleGroup,
            onClearAppCaches = onClearAppCaches,
            planHeader = planHeader,
            reviewAd = reviewAd,
            onOpenSwipeReview = onOpenSwipeReview,
            onOpenPlanGrid = onOpenPlanGrid,
            deepCheckRunning = state.deepCheckRunning,
            onDeepCheck = onDeepCheck,
            listAd = listAd,
            onOpenFolder = onOpenFolder,
            onOpenAppInfo = onOpenAppInfo,
            onExcludeFolder = onExcludeFolder,
            sharedFigures = state.planFigures(),
            autoOpenSuggestion = autoOpenSuggestion,
            onSuggestionOpened = onSuggestionOpened,
            onVerifyAndSelect = onVerifyAndSelect,
            verifySelectPercent = state.verifySelectPercent,
            verifyFeedback = state.verifyFeedback,
            scrollToApps = scrollToApps,
            onScrolledToApps = onScrolledToApps,
        )
    }
}

@Composable
internal fun SmartResultsContent(
    summary: ScanSummary,
    activeCategoryReview: CleanCategory?,
    activeCategoryReviewIds: Set<String>?,
    installedApps: List<InstalledAppEntry>,
    loadingApps: Boolean,
    hasUsageAccess: Boolean,
    storageReview: StorageReviewSummary,
    storageReviewProgressFiles: Int,
    storageReviewProgressDirectories: Int,
    cleanupInProgress: Boolean,
    onScan: () -> Unit,
    onToggleItem: (String) -> Unit,
    onToggleCategory: (CleanCategory) -> Unit,
    onSetItemsSelected: (ids: Set<String>, selected: Boolean, bulk: Boolean) -> Unit,
    onOpenCategoryReview: (CleanCategory, Set<String>?) -> Unit,
    onOpenStorageReview: (StorageFileType, Boolean) -> Unit,
    onRequestUsageAccess: () -> Unit,
    onRefreshInstalledApps: () -> Unit,
    onUninstallApp: (String) -> Unit,
    onToggleStorageReviewItem: (String) -> Unit,
    onToggleAllStorageReviewItems: () -> Unit,
    onClean: (Set<String>?) -> Unit,
    onCleanStorage: () -> Unit,
    modifier: Modifier,
    appCacheBytes: Long?,
    onToggleGroup: (CleanupGroup) -> Unit,
    onClearAppCaches: () -> Unit,
    planHeader: LazyListScope.() -> Unit,
    reviewAd: LazyListScope.(Int) -> Unit,
    onOpenSwipeReview: (CleanupGroup) -> Unit,
    onOpenPlanGrid: (CleanupGroup?, List<CleanableItem>) -> Unit,
    deepCheckRunning: Boolean,
    onDeepCheck: () -> Unit,
    listAd: @Composable () -> Unit,
    onOpenFolder: (FolderSize) -> Unit,
    onOpenAppInfo: (String) -> Unit,
    onExcludeFolder: (volumeRoot: String, relativePath: String) -> Unit,
    sharedFigures: PlanFigures? = null,
    autoOpenSuggestion: Boolean = false,
    onSuggestionOpened: () -> Unit = {},
    onVerifyAndSelect: (Set<String>) -> Unit = {},
    verifySelectPercent: Int? = null,
    verifyFeedback: String? = null,
    scrollToApps: Boolean = false,
    onScrolledToApps: () -> Unit = {},
) {
    var previewItemId by remember { mutableStateOf<String?>(null) }
    var previewStorageItemId by remember { mutableStateOf<String?>(null) }
    var showUnusedAppsReview by remember { mutableStateOf(false) }

    // V8: the largest-apps rows need the app list once Usage Access is on.
    LaunchedEffect(hasUsageAccess) {
        if (hasUsageAccess && installedApps.isEmpty() && !loadingApps) onRefreshInstalledApps()
    }

    val unusedApps = remember(installedApps, hasUsageAccess) {
        unusedAppsForSmartReview(installedApps, hasUsageAccess)
    }

    val category = activeCategoryReview
    if (category != null) {
        val reviewItems = summary.byCategory[category].orEmpty().let { allItems ->
            activeCategoryReviewIds?.let { ids -> allItems.filter { it.id in ids } } ?: allItems
        }
        CategoryDetailPage(
            category = category,
            items = reviewItems,
            cleanupInProgress = cleanupInProgress,
            onToggleCategory = {
                // D1: the user's "Select all" works both ways; look-alikes are checked first.
                val candidates = PlanSelection.selectAllCandidates(reviewItems)
                val ids = candidates.mapTo(linkedSetOf(), CleanableItem::id)
                if (PlanSelection.needsVerification(reviewItems)) {
                    onVerifyAndSelect(ids)
                } else {
                    onSetItemsSelected(ids, PlanSelection.selectAllTarget(reviewItems), false)
                }
            },
            verifyingPercent = verifySelectPercent,
            verifyFeedback = verifyFeedback,
            onToggleItem = onToggleItem,
            onPreview = { previewItemId = it.id },
            onCleanSelected = { ids -> onClean(ids) },
            modifier = modifier,
            gridAd = listAd,
        )
        previewItemId?.let { itemId ->
            summary.items.firstOrNull { it.id == itemId }?.let { item ->
                FilePreviewDialog(
                    file = item.toSmartPreview(),
                    selected = item.selected,
                    reason = assessmentReason(item.assessment),
                    onToggleSelection = { onToggleItem(item.id) },
                    onDismiss = { previewItemId = null },
                    onExcludeFolder = if (item.assessment.group.auto || !canExcludeFolder(item.relativePath)) {
                        null
                    } else {
                        {
                            previewItemId = null
                            onExcludeFolder(item.volumeRoot, item.relativePath)
                        }
                    },
                )
            }
        }
        return
    }

    val storageType = storageReview.type
    if (storageType != null) {
        StorageDetailPage(
            type = storageType,
            review = storageReview,
            progressFiles = storageReviewProgressFiles,
            progressDirectories = storageReviewProgressDirectories,
            cleanupInProgress = cleanupInProgress,
            onToggleItem = onToggleStorageReviewItem,
            onToggleAll = onToggleAllStorageReviewItems,
            onPreview = { previewStorageItemId = it.id },
            onCleanSelected = onCleanStorage,
            modifier = modifier,
        )
        previewStorageItemId?.let { itemId ->
            storageReview.items.firstOrNull { it.id == itemId }?.let { item ->
                FilePreviewDialog(
                    file = item.file.toSmartPreview(),
                    selected = item.selected,
                    reason = stringResource(R.string.storage_review_manual_reason),
                    onToggleSelection = { onToggleStorageReviewItem(item.id) },
                    onDismiss = { previewStorageItemId = null },
                    onExcludeFolder = if (canExcludeFolder(item.file.relativePath)) {
                        {
                            previewStorageItemId = null
                            onExcludeFolder(item.file.volumeRoot, item.file.relativePath)
                        }
                    } else {
                        null
                    },
                )
            }
        }
        return
    }

    val plan = summary.plan
    val deepCheck = remember(summary.items) { PlanSelection.deepCheckCandidates(summary.items) }
    val findingsApps = if (hasUsageAccess && installedApps.isNotEmpty()) unusedApps else null
    // C1: the same figures Home reads (no second calculation here).
    val figures = sharedFigures ?: PlanFigures.of(plan, findingsApps)
    // C2: "My pick" asks once through the shared confirmation, list visible on request.
    var suggestConfirm by rememberSaveable { mutableStateOf(false) }
    var suggestShowList by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(autoOpenSuggestion) {
        if (autoOpenSuggestion) {
            if (figures.showsSuggestion) suggestConfirm = true
            onSuggestionOpened()
        }
    }
    if (suggestConfirm) {
        val suggested = plan.suggestedItems
        DeleteConfirmDialog(
            items = suggested,
            inProgress = cleanupInProgress,
            initiallyShowAll = suggestShowList,
            onDismiss = {
                suggestConfirm = false
                suggestShowList = false
            },
            onConfirm = { ids ->
                suggestConfirm = false
                suggestShowList = false
                // The app's own pick: bulk = true keeps camera files and look-alikes out (D1).
                onSetItemsSelected(ids, true, true)
                onClean(ids)
            },
        )
    }
    // E1: the apps section (real icons, Android's own uninstall or settings screens).
    val context = androidx.compose.ui.platform.LocalContext.current
    val appRows = remember(installedApps, hasUsageAccess) {
        AppPlan.rows(installedApps, hasUsageAccess, context.packageName)
    }
    val appsNotePrefs = remember { context.getSharedPreferences("plan_apps", android.content.Context.MODE_PRIVATE) }
    // The one-line note shows until the user has actually had it on screen once
    // (marked from the note item itself, not merely when rows exist).
    val showAppsNote = remember { !appsNotePrefs.getBoolean("note_seen", false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    // Home's "Apps hold X more" line lands on this section.
    LaunchedEffect(scrollToApps) {
        if (!scrollToApps) return@LaunchedEffect
        repeat(40) {
            val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == PLAN_APPS_KEY }
            if (target != null) {
                listState.animateScrollToItem(target.index)
                onScrolledToApps()
                return@LaunchedEffect
            }
            if (!listState.canScrollForward) {
                onScrolledToApps()
                return@LaunchedEffect
            }
            listState.scrollBy(500f)
        }
        onScrolledToApps()
    }
    PageFrame(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            planHeader(this)
            cleanupPlanItems(
                plan = plan,
                figures = figures,
                onConfirmSuggested = { suggestConfirm = true },
                onSeeSuggested = {
                    suggestShowList = true
                    suggestConfirm = true
                },
                appCacheBytes = appCacheBytes,
                unusedApps = findingsApps,
                onOpenRow = { row ->
                    val group = row.group
                    if (group != null && group in SwipeReviewState.SWIPE_GROUPS) {
                        onOpenSwipeReview(group)
                    } else {
                        onOpenPlanGrid(group, row.items)
                    }
                },
                onClearAppCaches = onClearAppCaches,
                onOpenUnusedApps = {
                    if (!hasUsageAccess) onRequestUsageAccess() else showUnusedAppsReview = true
                },
                onScanAgain = onScan,
                afterReviewRow = reviewAd,
                deepCheck = deepCheck,
                deepCheckRunning = deepCheckRunning,
                onDeepCheck = onDeepCheck,
                biggestFolders = summary.biggestFolders,
                hiddenKeptCount = summary.hiddenKeptCount,
                onOpenFolder = onOpenFolder,
                appRows = appRows,
                onOpenAppInfo = onOpenAppInfo,
                onUninstallApp = onUninstallApp,
                onRequestUsageAccess = onRequestUsageAccess,
                showAppsNote = showAppsNote,
                onAppsNoteShown = { appsNotePrefs.edit().putBoolean("note_seen", true).apply() },
            )
        }
    }

    previewItemId?.let { itemId ->
        summary.items.firstOrNull { it.id == itemId }?.let { item ->
            FilePreviewDialog(
                file = item.toSmartPreview(),
                selected = item.selected,
                reason = assessmentReason(item.assessment),
                onToggleSelection = { onToggleItem(item.id) },
                onDismiss = { previewItemId = null },
                onExcludeFolder = if (item.assessment.group.auto || !canExcludeFolder(item.relativePath)) {
                    null
                } else {
                    {
                        previewItemId = null
                        onExcludeFolder(item.volumeRoot, item.relativePath)
                    }
                },
            )
        }
    }

    if (showUnusedAppsReview) {
        UnusedAppsReviewDialog(
            apps = unusedApps,
            onUninstallApp = onUninstallApp,
            onDismiss = { showUnusedAppsReview = false },
        )
    }
}

internal const val LIST_AD_AFTER_ITEMS = 12

internal val PERSONAL_STORAGE_TYPES = setOf(StorageFileType.IMAGES, StorageFileType.VIDEOS, StorageFileType.DOCUMENTS)

/** V8: the five biggest apps the user installed (system apps left out). */
internal fun largestUserApps(apps: List<InstalledAppEntry>, limit: Int = 5): List<InstalledAppEntry> =
    apps.asSequence()
        .filterNot(InstalledAppEntry::systemApp)
        .filter { it.totalBytes > 0L }
        .sortedByDescending(InstalledAppEntry::totalBytes)
        .take(limit)
        .toList()
