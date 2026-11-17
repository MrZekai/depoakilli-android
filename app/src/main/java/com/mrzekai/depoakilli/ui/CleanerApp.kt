package com.mrzekai.depoakilli.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwipeLeft
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.ads.AdPolicy
import com.mrzekai.depoakilli.ads.InterstitialTrigger
import com.mrzekai.depoakilli.data.CleaningThresholds
import com.mrzekai.depoakilli.model.ScanFocus
import com.mrzekai.depoakilli.ui.common.SystemBarIcons
import com.mrzekai.depoakilli.ui.theme.Ink
import com.mrzekai.depoakilli.ui.theme.Lime
import com.mrzekai.depoakilli.ui.whatsapp.WhatsAppCleanerDetailScreen

/** Three tabs (T1.10): Clean (the plan), Tools and Trash. Settings live behind the gear icon. */
internal enum class AppTab(@StringRes val titleRes: Int, val icon: ImageVector) {
    HOME(R.string.tab_clean, Icons.Outlined.CleaningServices),
    TOOLS(R.string.tab_tools, Icons.Outlined.Handyman),
    TRASH(R.string.tab_trash, Icons.Outlined.Delete),
}

internal enum class DetailScreen(@StringRes val titleRes: Int) {
    CLEAN_RESULTS(R.string.clean_results_title),
    WHATSAPP(R.string.whatsapp_cleaner_title),
    APP_CACHE(R.string.cache_manager_title),
    APP_MANAGER(R.string.app_manager_title),
    STORAGE_CHANGE(R.string.storage_change_title),
    SETTINGS(R.string.settings_title),
    ACCESS(R.string.security_center_title),
    PRIVACY(R.string.privacy_policy),
    TERMS(R.string.terms_of_service),
    ABOUT(R.string.about_app),
}

/** Screens whose use counts for the return-from-tool interstitial (T1B.3 b). */
internal val TOOL_SCREENS = setOf(
    DetailScreen.CLEAN_RESULTS,
    DetailScreen.WHATSAPP,
    DetailScreen.APP_CACHE,
    DetailScreen.APP_MANAGER,
    DetailScreen.STORAGE_CHANGE,
)

internal enum class AccessDisclosure {
    ALL_FILES,
    USAGE_ACCESS,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanerApp(
    viewModel: CleanerViewModel,
    canRequestAds: Boolean,
    fullScreenAdActive: Boolean,
    appOpenCoverVisible: Boolean = false,
    privacyOptionsRequired: Boolean,
    adFreeRemainingMillis: Long,
    rewardedAdReady: Boolean,
    onRequestAllFilesAccess: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onClearAllAppCaches: () -> Unit,
    onOpenAppDetails: (String) -> Unit,
    onPrepareCleanup: (Set<String>?) -> Unit,
    onPrepareStorageCleanup: () -> Unit,
    onPrepareWhatsAppCleanup: ((Boolean) -> Unit) -> Unit,
    onCleanupResultDismissed: () -> Unit,
    onCleanupSucceeded: () -> Unit,
    onReturnedToClean: (dwellMillis: Long) -> Unit = {},
    onNaturalBreak: (InterstitialTrigger) -> Unit = {},
    onDeepDuplicateCheck: () -> Unit = {},
    onUninstallApp: (String) -> Unit,
    onOpenAppInfo: (String) -> Unit,
    onOpenLanguageSettings: () -> Unit,
    onShowPrivacyOptions: () -> Unit,
    onShowRewardedAd: () -> Unit,
    onRateApp: () -> Unit,
    onSendFeedback: () -> Unit,
    onShareApp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var detailScreen by rememberSaveable { mutableStateOf<DetailScreen?>(null) }
    var legalReturnScreen by rememberSaveable { mutableStateOf<DetailScreen?>(null) }
    var accessDisclosure by rememberSaveable { mutableStateOf<AccessDisclosure?>(null) }
    // After the user returned from the system screen without granting access,
    // the Clean tab shows one sentence and "Open settings" (T2.1).
    var allFilesAskedOnce by rememberSaveable { mutableStateOf(false) }
    // Scan data lives in the ViewModel, not saved navigation state. After a
    // process restart the saved results route must not reopen without its data.
    LaunchedEffect(Unit) {
        if (detailScreen == DetailScreen.CLEAN_RESULTS && !state.lastScanCompleted && !state.scanning) {
            detailScreen = null
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    // Trigger (b): time spent on a tool screen before returning to the Clean or
    // Tools tab. Settings, privacy, terms and about pages never count.
    var detailEnteredAt by remember { mutableStateOf(0L) }
    LaunchedEffect(detailScreen) {
        val toolScreen = detailScreen in TOOL_SCREENS
        when {
            toolScreen && detailEnteredAt == 0L ->
                detailEnteredAt = android.os.SystemClock.elapsedRealtime()
            detailScreen == null && detailEnteredAt > 0L -> {
                val dwell = android.os.SystemClock.elapsedRealtime() - detailEnteredAt
                detailEnteredAt = 0L
                if (state.cleanupResult == null && !state.cleanupInProgress && !state.scanning) onReturnedToClean(dwell)
            }
            !toolScreen -> detailEnteredAt = 0L
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    fun requestAllFilesWithDisclosure() {
        accessDisclosure = AccessDisclosure.ALL_FILES
    }

    fun requestUsageWithDisclosure() {
        accessDisclosure = AccessDisclosure.USAGE_ACCESS
    }

    fun launchScan(focus: ScanFocus) {
        legalReturnScreen = null
        detailScreen = DetailScreen.CLEAN_RESULTS
        if (!state.hasAllFilesAccess) {
            viewModel.queueScanAfterPermission(focus)
            requestAllFilesWithDisclosure()
        } else {
            viewModel.scan(focus)
        }
    }

    /** Findings list (T5.4); the interstitial follows a finished scan (AdPolicy limits apply). */
    fun openFindings() {
        legalReturnScreen = null
        viewModel.showPlan()
        detailScreen = DetailScreen.CLEAN_RESULTS
        onNaturalBreak(InterstitialTrigger.OPEN_FINDINGS)
    }

    fun openLegalPage(page: LegalPage) {
        legalReturnScreen = detailScreen
        detailScreen = when (page) {
            LegalPage.PRIVACY -> DetailScreen.PRIVACY
            LegalPage.TERMS -> DetailScreen.TERMS
            LegalPage.ABOUT -> DetailScreen.ABOUT
        }
    }

    fun navigateBackInApp() {
        when {
            detailScreen == DetailScreen.CLEAN_RESULTS && state.storageReview.type != null -> {
                // Folders and groups open from the findings list (T5.4); going
                // back returns there.
                viewModel.closeStorageReview()
            }
            detailScreen == DetailScreen.CLEAN_RESULTS && state.swipeReviewGroup != null -> {
                viewModel.closeSwipeReview()
            }
            detailScreen == DetailScreen.CLEAN_RESULTS && state.smartCategoryReview != null -> {
                viewModel.closeSmartCategoryReview()
            }
            detailScreen in setOf(DetailScreen.PRIVACY, DetailScreen.TERMS, DetailScreen.ABOUT) &&
                legalReturnScreen != null -> {
                detailScreen = legalReturnScreen
                legalReturnScreen = null
            }
            detailScreen != null -> {
                detailScreen = null
                legalReturnScreen = null
            }
            selectedTabIndex != AppTab.HOME.ordinal ->
                selectedTabIndex = AppTab.HOME.ordinal
        }
    }

    val hasInAppBackTarget =
        detailScreen != null ||
            state.storageReview.type != null ||
            state.smartCategoryReview != null ||
            selectedTabIndex != AppTab.HOME.ordinal

    BackHandler(enabled = hasInAppBackTarget && !fullScreenAdActive) {
        navigateBackInApp()
    }

    // Lime screens: dark status icons. The black bottom menu and the black
    // swipe review: light icons (Faz 5).
    val swipeVisible = detailScreen == DetailScreen.CLEAN_RESULTS &&
        state.swipeReviewGroup?.let(state.swipeStates::get) != null
    SystemBarIcons(
        darkStatusIcons = !swipeVisible,
        darkNavigationIcons = detailScreen != null && !swipeVisible,
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            val swipeOpen = detailScreen == DetailScreen.CLEAN_RESULTS && state.swipeReviewGroup != null &&
                state.swipeReviewGroup?.let(state.swipeStates::get) != null
            if (detailScreen != DetailScreen.WHATSAPP && !swipeOpen) {
                TopAppBar(
                    title = {
                        val storageReviewTitle = state.storageReview.type?.titleRes
                        val categoryReviewTitle = state.smartCategoryReview?.titleRes
                        val titleRes = when {
                            detailScreen == DetailScreen.CLEAN_RESULTS && storageReviewTitle != null ->
                                storageReviewTitle
                            detailScreen == DetailScreen.CLEAN_RESULTS && categoryReviewTitle != null ->
                                categoryReviewTitle
                            detailScreen == DetailScreen.CLEAN_RESULTS && state.scanFocus == ScanFocus.SMART ->
                                R.string.smart_clean_results_title
                            detailScreen == DetailScreen.CLEAN_RESULTS ->
                                scanFocusTitleRes(state.scanFocus)
                            else -> detailScreen?.titleRes ?: AppTab.entries[selectedTabIndex].titleRes
                        }
                        val folderTitle = state.storageReview.folder?.name
                            ?.takeIf { detailScreen == DetailScreen.CLEAN_RESULTS }
                        // Tabs carry their own large title or giant figure (Faz 5).
                        if (detailScreen != null) {
                            Text(
                                folderTitle ?: stringResource(titleRes),
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    navigationIcon = {
                        if (detailScreen != null) {
                            IconButton(onClick = ::navigateBackInApp) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = stringResource(R.string.back),
                                )
                            }
                        }
                    },
                    actions = {
                        val gridGroup = state.smartCategoryReviewGroup
                        if (
                            detailScreen == DetailScreen.CLEAN_RESULTS &&
                            state.smartCategoryReview != null &&
                            gridGroup != null &&
                            gridGroup in com.mrzekai.depoakilli.ui.review.SwipeReviewState.SWIPE_GROUPS
                        ) {
                            IconButton(onClick = { viewModel.openSwipeReview(gridGroup) }) {
                                Icon(
                                    Icons.Outlined.SwipeLeft,
                                    contentDescription = stringResource(R.string.swipe_review_view),
                                )
                            }
                        }
                        if (detailScreen == null) {
                            IconButton(onClick = { detailScreen = DetailScreen.SETTINGS }) {
                                Icon(
                                    Icons.Outlined.Settings,
                                    contentDescription = stringResource(R.string.settings_title),
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Lime,
                        scrolledContainerColor = Lime,
                        titleContentColor = Ink,
                        navigationIconContentColor = Ink,
                        actionIconContentColor = Ink,
                    ),
                )
            }
        },
        bottomBar = {
            // Faz 5 ad table: banner on Home, Tools and Trash (above the menu)
            // and on the findings list; tool lists carry an in-list native
            // instead, and the swipe review has its own break card.
            val findingsListOpen = state.scanFocus == ScanFocus.SMART &&
                state.smartCategoryReview == null &&
                state.storageReview.type == null &&
                state.swipeReviewGroup == null
            val screenAllowsBanner = when (detailScreen) {
                null -> when (AppTab.entries[selectedTabIndex]) {
                    AppTab.HOME,
                    AppTab.TOOLS,
                    AppTab.TRASH -> true
                }
                DetailScreen.CLEAN_RESULTS -> state.hasAllFilesAccess &&
                    (findingsListOpen || state.scanFocus == ScanFocus.ANALYZE)
                DetailScreen.WHATSAPP,
                DetailScreen.APP_CACHE,
                DetailScreen.APP_MANAGER -> false
                DetailScreen.STORAGE_CHANGE -> true
                DetailScreen.SETTINGS,
                DetailScreen.ACCESS,
                DetailScreen.PRIVACY,
                DetailScreen.TERMS,
                DetailScreen.ABOUT -> false
            }

            val bannerAllowed =
                canRequestAds &&
                    screenAllowsBanner &&
                    accessDisclosure == null &&
                    !fullScreenAdActive &&
                    !appOpenCoverVisible &&
                    // No banner next to the swipe-review break card (it has its own native ad).
                    state.swipeReviewGroup?.let { state.swipeStates[it]?.breakPending } != true &&
                    // No ad while a scan runs (Faz 5 ad table: "Taranıyor: yok").
                    !state.scanning &&
                    !state.dashboardRefreshing &&
                    !state.whatsAppScanning &&
                    !state.cleanupInProgress &&
                    state.cleanupResult == null &&
                    adFreeRemainingMillis <= 0L

            AppBottomBar(
                detailScreenOpen = detailScreen != null,
                bannerAllowed = bannerAllowed,
                selectedTabIndex = selectedTabIndex,
                onSelectTab = { selectedTabIndex = it },
            )
        },
    ) { padding ->
        when (detailScreen) {
            DetailScreen.CLEAN_RESULTS -> CleanScreen(
                state = state,
                onRequestAllFilesAccess = ::requestAllFilesWithDisclosure,
                onScan = { viewModel.scan(state.scanFocus) },
                onToggleItem = viewModel::toggleItem,
                onToggleCategory = viewModel::toggleCategory,
                onSetItemsSelected = viewModel::setItemsSelected,
                onOpenCategoryReview = viewModel::openSmartCategoryReview,
                onOpenStorageReview = viewModel::openStorageReview,
                onRequestUsageAccess = ::requestUsageWithDisclosure,
                onRefreshInstalledApps = viewModel::refreshInstalledApps,
                onUninstallApp = onUninstallApp,
                onToggleStorageReviewItem = viewModel::toggleStorageReviewItem,
                onToggleAllStorageReviewItems = viewModel::toggleAllStorageReviewItems,
                onClean = onPrepareCleanup,
                onCleanStorage = onPrepareStorageCleanup,
                modifier = Modifier.padding(padding),
                onToggleGroup = viewModel::toggleGroup,
                onClearAppCaches = onClearAllAppCaches,
                viewModel = viewModel,
                canRequestAds = canRequestAds,
                onDeepCheck = onDeepDuplicateCheck,
                onOpenAppInfo = onOpenAppInfo,
                onSwipeConfirmed = { onNaturalBreak(InterstitialTrigger.SWIPE_FINISHED) },
                onCloseSwipe = ::navigateBackInApp,
            )

            DetailScreen.WHATSAPP -> WhatsAppCleanerDetailScreen(
                state = state,
                onBack = ::navigateBackInApp,
                onRequestAccess = ::requestAllFilesWithDisclosure,
                onScan = viewModel::scanWhatsAppLibrary,
                onToggleItem = viewModel::toggleWhatsAppItem,
                onToggleCategory = viewModel::toggleWhatsAppCategory,
                onDeleteSelected = {
                    onPrepareWhatsAppCleanup { _ ->
                        // Keep the WhatsApp tool as the return surface.
                        // The measured result dialog owns the navigation break.
                    }
                },
                modifier = Modifier.padding(padding),
            )

            DetailScreen.APP_CACHE -> AppCacheManagerScreen(
                state = state,
                onRequestUsageAccess = ::requestUsageWithDisclosure,
                onRefresh = { viewModel.refreshAppCaches(force = true) },
                onClearAllAppCaches = onClearAllAppCaches,
                onClearOwnCache = viewModel::clearOwnAppCache,
                onOpenAppDetails = onOpenAppDetails,
                modifier = Modifier.padding(padding),
            )

            DetailScreen.APP_MANAGER -> AppManagerScreen(
                state = state,
                onRequestUsageAccess = ::requestUsageWithDisclosure,
                onRefresh = viewModel::refreshInstalledApps,
                onUninstallApp = onUninstallApp,
                onOpenAppInfo = onOpenAppInfo,
                modifier = Modifier.padding(padding),
            )

            DetailScreen.STORAGE_CHANGE -> StorageChangeScreen(
                report = state.storageChange,
                refreshing = state.scanning && state.scanFocus == ScanFocus.ANALYZE,
                progressFiles = state.scanProgressFiles,
                progressDirectories = state.scanProgressDirectories,
                onAnalyze = {
                    if (!state.hasAllFilesAccess) {
                        viewModel.queueScanAfterPermission(ScanFocus.ANALYZE)
                        requestAllFilesWithDisclosure()
                    } else {
                        viewModel.scan(ScanFocus.ANALYZE)
                    }
                },
                modifier = Modifier.padding(padding),
            )

            DetailScreen.SETTINGS -> SettingsDetailScreen(
                privacyOptionsRequired = privacyOptionsRequired,
                canRequestAds = canRequestAds,
                adFreeRemainingMillis = adFreeRemainingMillis,
                rewardedAdReady = rewardedAdReady,
                onOpenLanguageSettings = onOpenLanguageSettings,
                onRateApp = onRateApp,
                onSendFeedback = onSendFeedback,
                onShareApp = onShareApp,
                onShowPrivacyOptions = onShowPrivacyOptions,
                onShowRewardedAd = onShowRewardedAd,
                onOpenPrivacyAccess = { detailScreen = DetailScreen.ACCESS },
                onOpenLegalPage = ::openLegalPage,
                modifier = Modifier.padding(padding),
                keptItemCount = state.keptItemCount,
                onClearKeptItems = viewModel::clearKeptItems,
                excludedFolders = state.excludedFolders,
                onIncludeFolder = viewModel::includeFolder,
                hasAllFilesAccess = state.hasAllFilesAccess,
                hasUsageAccess = state.hasUsageAccess,
            )

            DetailScreen.ACCESS -> SecurityCenterScreen(
                state = state,
                onManageAllFilesAccess = {
                    if (state.hasAllFilesAccess) {
                        onRequestAllFilesAccess()
                    } else {
                        requestAllFilesWithDisclosure()
                    }
                },
                onManageUsageAccess = {
                    if (state.hasUsageAccess) {
                        onRequestUsageAccess()
                    } else {
                        requestUsageWithDisclosure()
                    }
                },
                onOpenPrivacy = {
                    legalReturnScreen = DetailScreen.ACCESS
                    detailScreen = DetailScreen.PRIVACY
                },
                modifier = Modifier.padding(padding),
            )

            DetailScreen.PRIVACY,
            DetailScreen.TERMS,
            DetailScreen.ABOUT -> LegalDetailScreen(
                page = when (detailScreen) {
                    DetailScreen.PRIVACY -> LegalPage.PRIVACY
                    DetailScreen.TERMS -> LegalPage.TERMS
                    else -> LegalPage.ABOUT
                },
                info = state.deviceInfo,
                modifier = Modifier.padding(padding),
            )

            null -> when (AppTab.entries[selectedTabIndex]) {
                AppTab.HOME -> {
                    LaunchedEffect(Unit) {
                        viewModel.showPlan()
                        viewModel.onAppOpened()
                    }
                    CleanTab(
                        state = state,
                        onAllowAccess = ::requestAllFilesWithDisclosure,
                        onCancelScan = viewModel::cancelScan,
                        onStartScan = {
                            if (!state.hasAllFilesAccess) {
                                requestAllFilesWithDisclosure()
                            } else {
                                viewModel.scan(ScanFocus.SMART)
                            }
                        },
                        onClean = { ids -> onPrepareCleanup(ids) },
                        onToggleItem = viewModel::toggleItem,
                        onOpenFindings = ::openFindings,
                        onOpenTrash = { selectedTabIndex = AppTab.TRASH.ordinal },
                        hardActions = HomeHardActions(
                            onOpenMedia = { launchScan(ScanFocus.MEDIA) },
                            onOpenApps = {
                                detailScreen = DetailScreen.APP_MANAGER
                                viewModel.refreshInstalledApps()
                            },
                            onOpenFolder = { folder ->
                                if (folder != null) {
                                    viewModel.openFolderReview(folder)
                                    detailScreen = DetailScreen.CLEAN_RESULTS
                                } else {
                                    openFindings()
                                }
                            },
                            onOpenDuplicates = { launchScan(ScanFocus.DUPLICATES) },
                        ),
                        accessDeniedOnce = allFilesAskedOnce,
                        onOpenSettings = onRequestAllFilesAccess,
                        modifier = Modifier.padding(padding),
                    )
                }

                AppTab.TOOLS -> DeviceCenterScreen(
                    state = state,
                    onScan = ::launchScan,
                    onRequestAllFilesAccess = ::requestAllFilesWithDisclosure,
                    onOpenWhatsApp = {
                        detailScreen = DetailScreen.WHATSAPP
                        if (state.hasWhatsAppAccess) viewModel.scanWhatsAppLibrary()
                    },
                    onOpenCache = {
                        detailScreen = DetailScreen.APP_CACHE
                        viewModel.refreshAppCaches()
                    },
                    onOpenAppManager = {
                        detailScreen = DetailScreen.APP_MANAGER
                        viewModel.refreshInstalledApps()
                    },
                    onOpenStorageChange = {
                        detailScreen = DetailScreen.STORAGE_CHANGE
                    },
                    modifier = Modifier.padding(padding),
                )

                AppTab.TRASH -> TrashScreen(
                    entries = state.trashEntries,
                    onRefresh = viewModel::refreshTrash,
                    onRestore = { ids -> viewModel.restoreTrash(ids) },
                    onDeleteForever = { ids -> viewModel.emptyTrash(ids) },
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }

    val trashFallback = state.trashFallback
    if (trashFallback != null && state.cleanupResult == null && !state.cleanupInProgress) {
        TrashFallbackDialog(
            fallback = trashFallback,
            bytes = trashFallbackBytes(state, trashFallback),
            onDeletePermanently = viewModel::deleteTrashFallbackPermanently,
            onDismiss = viewModel::dismissTrashFallback,
        )
    }

    accessDisclosure?.let { disclosure ->
        AccessDisclosureDialog(
            disclosure = disclosure,
            onContinue = {
                accessDisclosure = null
                if (disclosure == AccessDisclosure.ALL_FILES) {
                    allFilesAskedOnce = true
                    onRequestAllFilesAccess()
                } else {
                    onRequestUsageAccess()
                }
            },
            onDismiss = { accessDisclosure = null },
        )
    }

    if (!fullScreenAdActive) {
        state.cleanupResult?.let { result ->
            val dismissResultAndReturn: () -> Unit = {
                val returnScreen = detailScreen
                val returnTabIndex = selectedTabIndex

                viewModel.dismissCleanupResult()
                // Done already gives the post-cleanup break; no second one on return.
                detailEnteredAt = 0L
                legalReturnScreen = null
                // Done returns to the tool's main screen with refreshed
                // totals, not to the sub-list the cleanup was started from,
                // and never leaves a stale sub-list behind (QA v43 #06).
                viewModel.closeSmartCategoryReview()
                viewModel.closeStorageReview()

                when {
                    result.kind != CleanupResultKind.FILES -> {
                        detailScreen = DetailScreen.APP_CACHE
                    }
                    returnScreen == DetailScreen.WHATSAPP -> {
                        detailScreen = DetailScreen.WHATSAPP
                    }
                    returnScreen == DetailScreen.CLEAN_RESULTS &&
                        returnTabIndex == AppTab.TOOLS.ordinal -> {
                        detailScreen = DetailScreen.CLEAN_RESULTS
                        selectedTabIndex = AppTab.TOOLS.ordinal
                    }
                    else -> {
                        detailScreen = null
                        selectedTabIndex = AppTab.HOME.ordinal
                    }
                }
            }

            CleanupResultDialog(
                result = result,
                canRequestAds = canRequestAds,
                showRewardedOffer = canRequestAds && adFreeRemainingMillis <= 0L && rewardedAdReady,
                onShowRewardedAd = onShowRewardedAd,
                onSystemDismiss = {
                    dismissResultAndReturn()
                },
                onUndo = viewModel::undoCleanup,
                onEmptyTrash = { viewModel.emptyTrash(result.trashEntryIds) },
                nextReview = if (result.kind == CleanupResultKind.FILES && state.scanFocus == ScanFocus.SMART) {
                    state.summary.plan.review.firstOrNull { it.bytes >= CleaningThresholds.GROUP_MIN_BYTES }
                } else {
                    null
                },
                onReviewNext = {
                    val next = state.summary.plan.review.firstOrNull()
                    dismissResultAndReturn()
                    val group = next?.group
                    if (next != null && group != null) {
                        if (group in com.mrzekai.depoakilli.ui.review.SwipeReviewState.SWIPE_GROUPS) {
                            viewModel.openSwipeReview(group)
                        } else {
                            viewModel.openPlanGroupGrid(group, next.items)
                        }
                        detailScreen = DetailScreen.CLEAN_RESULTS
                    }
                },
                onDone = { resultAdPresented ->
                    val eligibleForNaturalBreakAd =
                        result.operationSucceeded &&
                            (
                                result.deletedCount > 0 ||
                                    result.deletedBytes > 0L ||
                                    result.kind == CleanupResultKind.SYSTEM_CACHE
                                )

                    dismissResultAndReturn()

                    if (eligibleForNaturalBreakAd && result.failedCount == 0) onCleanupSucceeded()
                    // Trigger (a): Done closes the result, also when the result
                    // panel already showed an ad; AdPolicy limits frequency.
                    if (eligibleForNaturalBreakAd) {
                        onCleanupResultDismissed()
                    }
                },
            )
        }
    }

        if (fullScreenAdActive) FullScreenAdCover()
    }
}

/** Size of the files that could not go to Trash, for the permanent-delete title. */
private fun trashFallbackBytes(state: CleanerUiState, fallback: TrashFallback): Long {
    val ids = fallback.ids
    return when (fallback.source) {
        TrashFallback.Source.PLAN -> state.summary.items.filter { it.id in ids }.sumOf { it.sizeBytes }
        TrashFallback.Source.WHATSAPP -> state.whatsAppSummary.items.filter { it.id in ids }.sumOf { it.sizeBytes }
        TrashFallback.Source.STORAGE_REVIEW -> state.storageReview.items.filter { it.id in ids }.sumOf { it.file.sizeBytes }
    }
}
