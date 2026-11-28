package com.mrzekai.depoakilli.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.billing.ProPlanOption
import com.mrzekai.depoakilli.billing.ProScreenState
import com.mrzekai.depoakilli.data.CleaningThresholds
import com.mrzekai.depoakilli.model.ScanFocus
import com.mrzekai.depoakilli.model.ageDays
import com.mrzekai.depoakilli.model.rowGroup
import com.mrzekai.depoakilli.model.ToolSuggestion
import com.mrzekai.depoakilli.model.SuggestionTool
import com.mrzekai.depoakilli.ui.common.HubTabs
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
    PRO(R.string.pro_title),
    WATCH(R.string.watch_screen_title),

    /** Faz 3A-2 Z5–Z8: shrinking big videos without deleting. */
    VIDEOS(R.string.video_title),
    SIMILAR(R.string.similar_title),
    PLAN(R.string.plan_title),
    FILTERS(R.string.filters_tools),
    LEARNED(R.string.learned_link),
}

internal enum class AccessDisclosure {
    ALL_FILES,
    USAGE_ACCESS,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanerApp(
    viewModel: CleanerViewModel,
    pro: ProScreenState,
    onRequestAllFilesAccess: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onClearAllAppCaches: () -> Unit,
    onOpenAppDetails: (String) -> Unit,
    onPrepareCleanup: (Set<String>?) -> Unit,
    onPrepareStorageCleanup: () -> Unit,
    onPrepareWhatsAppCleanup: ((Boolean) -> Unit) -> Unit,
    onCleanupResultDismissed: () -> Unit,
    onCleanupSucceeded: () -> Unit,
    onDeepDuplicateCheck: () -> Unit = {},
    onUninstallApp: (String) -> Unit,
    onOpenAppInfo: (String) -> Unit,
    onOpenLanguageSettings: () -> Unit,
    onOpenPro: () -> Unit = {},
    onBuyPlan: (ProPlanOption) -> Unit = {},
    onRestorePurchases: () -> Unit = {},
    onManageSubscription: () -> Unit = {},
    /** Faz 2.2b: average photo size for "about N photos" (null until measured or without photos). */
    averagePhotoBytes: Long? = null,
    onPaywallOpened: () -> Unit = {},
    onRateApp: () -> Unit,
    onSendFeedback: () -> Unit,
    onShareApp: () -> Unit,
    /** Faz 3A-1 Storage Watch. */
    watch: com.mrzekai.depoakilli.ui.watch.WatchHost = com.mrzekai.depoakilli.ui.watch.WatchHost(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var detailScreen by rememberSaveable { mutableStateOf<DetailScreen?>(null) }
    var legalReturnScreen by rememberSaveable { mutableStateOf<DetailScreen?>(null) }
    var accessDisclosure by rememberSaveable { mutableStateOf<AccessDisclosure?>(null) }
    // After the user returned from the system screen without granting access,
    // the Clean tab shows one sentence and "Open settings" (T2.1).
    var allFilesAskedOnce by rememberSaveable { mutableStateOf(false) }
    // C2: the result screen's "My pick" line opens the findings list with its confirmation.
    var openSuggestionRequested by rememberSaveable { mutableStateOf(false) }
    var scrollToAppsRequested by rememberSaveable { mutableStateOf(false) }
    // Faz 2 Y2: the paywall opens only when the user taps (Y1 card, Y4, Y7, Settings).
    var paywallOpen by rememberSaveable { mutableStateOf(false) }
    // Faz 3A-2: video candidates (headers only, cached) feed the paywall's measured estimate.
    val shrinkApp = androidx.compose.ui.platform.LocalContext.current.applicationContext as? com.mrzekai.depoakilli.DepoAkilliApplication
    val videoFinder = shrinkApp?.videoShrink?.state?.collectAsStateWithLifecycle()?.value
    val videoResult = (videoFinder as? com.mrzekai.depoakilli.video.VideoFinderState.Ready)?.result
    LaunchedEffect(state.summary, state.hasAllFilesAccess) {
        if (state.hasAllFilesAccess && com.mrzekai.depoakilli.pro.ProGainEstimator.VIDEO_COMPRESSION_AVAILABLE) shrinkApp?.videoShrink?.refresh()
    }
    // Faz 3B-1: similar photos, looked at only while the app is in front (no service).
    val similarState = shrinkApp?.similarPhotos?.state?.collectAsStateWithLifecycle()?.value
    val similarResult = (similarState as? com.mrzekai.depoakilli.photo.SimilarState.Ready)?.result
    LaunchedEffect(state.summary, state.hasAllFilesAccess) {
        if (state.hasAllFilesAccess && com.mrzekai.depoakilli.pro.ProGainEstimator.SIMILAR_PHOTOS_AVAILABLE &&
            shrinkApp?.similarPhotos?.state?.value is com.mrzekai.depoakilli.photo.SimilarState.Idle && state.summary.scannedFileCount > 0
        ) {
            shrinkApp.similarPhotos.start()
        }
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, shrinkApp) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> shrinkApp?.similarPhotos?.pause()
                androidx.lifecycle.Lifecycle.Event.ON_START -> shrinkApp?.similarPhotos?.resume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val proEstimate = remember(state.summary, state.freeTier.firstCleanupUsed, state.scanFocus, videoResult, similarResult) {
        state.proEstimate(videoResult, similarResult)
    }
    fun openPaywall() {
        onOpenPro()
        onPaywallOpened()
        paywallOpen = true
    }
    // Faz 3A-1: Z2 before the watch is on; the hibernation note once after it.
    var watchIntroOpen by rememberSaveable { mutableStateOf(false) }
    var hibernationSheet by rememberSaveable { mutableStateOf(false) }
    val watchContext = androidx.compose.ui.platform.LocalContext.current
    fun afterWatchOn() {
        if (watch.hibernationRestricted && !watch.snapshot.hibernationExplained) {
            watch.markHibernationExplained()
            hibernationSheet = true
        }
    }
    val notificationLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { _ -> afterWatchOn() }
    fun turnWatchOn() {
        watch.enable()
        watchIntroOpen = false
        detailScreen = DetailScreen.WATCH
        // Android 13+: asked once, only now; after a "no" it is never asked again.
        val needsAsk = android.os.Build.VERSION.SDK_INT >= 33 &&
            !watch.snapshot.notificationAsked &&
            !com.mrzekai.depoakilli.watch.WatchNotifier.canPost(watchContext)
        if (needsAsk) {
            watch.markNotificationAsked()
            notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            afterWatchOn()
        }
    }
    fun openWatch() {
        if (watch.snapshot.settings.enabled) detailScreen = DetailScreen.WATCH else watchIntroOpen = true
    }
    // A purchase that turned Pro on goes from the paywall to "Pro is on" (Y5).
    LaunchedEffect(pro.isPro) {
        if (pro.isPro && paywallOpen) {
            paywallOpen = false
            detailScreen = DetailScreen.PRO
        }
    }
    // Scan data lives in the ViewModel, not saved navigation state. After a
    // process restart the saved results route must not reopen without its data.
    LaunchedEffect(Unit) {
        if (detailScreen == DetailScreen.CLEAN_RESULTS && !state.lastScanCompleted && !state.scanning) {
            detailScreen = null
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
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

    /** Findings list (T5.4). */
    fun openFindings() {
        legalReturnScreen = null
        viewModel.showPlan()
        detailScreen = DetailScreen.CLEAN_RESULTS
    }

    /** C4: switches between the two tabs of the Apps or Storage tool. */
    fun openHubTab(hub: HubSpec, index: Int) {
        when (hub.kind) {
            HubKind.APPS -> if (index == 0) {
                detailScreen = DetailScreen.APP_MANAGER
                viewModel.refreshInstalledApps()
            } else {
                detailScreen = DetailScreen.APP_CACHE
                viewModel.refreshAppCaches()
            }
            HubKind.STORAGE -> if (index == 0) {
                if (state.scanFocus == ScanFocus.ANALYZE && (state.lastScanCompleted || state.scanning)) {
                    detailScreen = DetailScreen.CLEAN_RESULTS
                } else {
                    launchScan(ScanFocus.ANALYZE)
                }
            } else {
                detailScreen = DetailScreen.STORAGE_CHANGE
            }
        }
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

    // Faz 3A-1: a tapped notice opens its screen; anything else starts from there by the user.
    LaunchedEffect(watch.target) {
        when (watch.target) {
            com.mrzekai.depoakilli.watch.WatchNotifier.TARGET_WHATSAPP -> {
                selectedTabIndex = AppTab.HOME.ordinal
                detailScreen = DetailScreen.WHATSAPP
            }
            com.mrzekai.depoakilli.watch.WatchNotifier.TARGET_WATCH -> {
                selectedTabIndex = AppTab.HOME.ordinal
                detailScreen = DetailScreen.WATCH
            }
            // Faz 3A-2: the new-videos notice and the shrinking notification open the video screens.
            com.mrzekai.depoakilli.watch.WatchNotifier.TARGET_SIMILAR -> {
                if (com.mrzekai.depoakilli.pro.ProGainEstimator.SIMILAR_PHOTOS_AVAILABLE) detailScreen = DetailScreen.SIMILAR
            }
            com.mrzekai.depoakilli.watch.WatchNotifier.TARGET_VIDEOS,
            com.mrzekai.depoakilli.video.VideoCompressionService.TARGET_SHRINK -> {
                selectedTabIndex = AppTab.HOME.ordinal
                detailScreen = DetailScreen.VIDEOS
            }
            else -> return@LaunchedEffect
        }
        watch.consumeTarget()
    }
    val watchUi = com.mrzekai.depoakilli.ui.watch.WatchUi(
        snapshot = watch.snapshot,
        isPro = pro.isPro,
        notificationsOn = watch.notificationsOn,
        hibernationRestricted = watch.hibernationRestricted,
        nowMillis = System.currentTimeMillis(),
    )
    val watchActions = com.mrzekai.depoakilli.ui.watch.WatchActions(
        turnOn = { watchIntroOpen = true },
        turnOff = watch.disable,
        setThreshold = { on -> watch.updateSettings { it.copy(thresholdAlerts = on) } },
        setWeekly = { on -> watch.updateSettings { it.copy(weeklySummary = on) } },
        setWhatsApp = { on -> watch.updateSettings { it.copy(whatsAppAlerts = on) } },
        setNewVideos = { on -> watch.updateSettings { it.copy(newVideoAlerts = on) } },
        setNewSimilar = { on -> watch.updateSettings { it.copy(newSimilarAlerts = on) } },
        openPaywall = ::openPaywall,
        openNotificationSettings = { watch.openSystem(com.mrzekai.depoakilli.watch.WatchNotifier.notificationSettingsIntent(watchContext)) },
        openHibernationSettings = { watch.openSystem(com.mrzekai.depoakilli.watch.WatchHibernation.settingsIntent(watchContext)) },
        openAppInfo = { watch.openSystem(com.mrzekai.depoakilli.watch.WatchNotifier.appInfoIntent(watchContext)) },
        lookAtNew = {
            detailScreen = null
            selectedTabIndex = AppTab.HOME.ordinal
            if (!state.hasAllFilesAccess) requestAllFilesWithDisclosure() else viewModel.scan(ScanFocus.SMART)
        },
        openDetails = { detailScreen = DetailScreen.WATCH },
    )

    val hasInAppBackTarget =
        detailScreen != null ||
            state.storageReview.type != null ||
            state.smartCategoryReview != null ||
            selectedTabIndex != AppTab.HOME.ordinal

    BackHandler(enabled = hasInAppBackTarget) {
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

    // Faz 3A-2 Y7: the plan offers shrinking only while there are candidates and the switch is on.
    val videoEntry = videoResult
        ?.takeIf { com.mrzekai.depoakilli.pro.ProGainEstimator.VIDEO_COMPRESSION_AVAILABLE && it.candidates.isNotEmpty() }
        ?.let { result ->
            com.mrzekai.depoakilli.ui.smart.VideoShrinkEntry(result.totalSavingBytes, result.candidates.size) {
                detailScreen = DetailScreen.VIDEOS
            }
        }
    val similarEntry = similarResult
        ?.takeIf { com.mrzekai.depoakilli.pro.ProGainEstimator.SIMILAR_PHOTOS_AVAILABLE && it.groups.isNotEmpty() }
        ?.let { result ->
            com.mrzekai.depoakilli.ui.smart.SimilarEntry(result.trashBytes, result.trashCount, result.groups.size) {
                detailScreen = DetailScreen.SIMILAR
            }
        }
    androidx.compose.runtime.CompositionLocalProvider(
        com.mrzekai.depoakilli.ui.smart.LocalVideoShrinkEntry provides videoEntry,
        com.mrzekai.depoakilli.ui.smart.LocalSimilarEntry provides similarEntry,
    ) {
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
                        // Tabs carry their own large title or giant figure (Faz 5). The
                        // findings list has only the back arrow (C5): its giant figure is the title.
                        val findingsList = detailScreen == DetailScreen.CLEAN_RESULTS &&
                            state.scanFocus == ScanFocus.SMART &&
                            storageReviewTitle == null &&
                            categoryReviewTitle == null &&
                            folderTitle == null
                        if (detailScreen != null && !findingsList) {
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
                        // C6: a tool's "Refresh" sits at the right of the top bar.
                        if (
                            detailScreen == DetailScreen.CLEAN_RESULTS &&
                            state.scanFocus != ScanFocus.SMART &&
                            state.scanFocus != ScanFocus.DEEP &&
                            state.lastScanCompleted &&
                            !state.scanning &&
                            state.storageReview.type == null &&
                            state.smartCategoryReview == null
                        ) {
                            IconButton(onClick = { viewModel.refreshToolScan() }) {
                                Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.cache_refresh))
                            }
                        }
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
            AppBottomBar(
                detailScreenOpen = detailScreen != null,
                // C6: the black swipe screen keeps a black navigation bar.
                darkNavigationArea = swipeVisible,
                selectedTabIndex = selectedTabIndex,
                onSelectTab = { selectedTabIndex = it },
            )
        },
    ) { padding ->
        // C4: Apps (apps + app cache) and Storage (analyzer + storage change)
        // are one tool each, with two tabs on top.
        val hub = hubOf(detailScreen, state)
        Column(Modifier.padding(padding)) {
            if (hub != null) {
                HubTabs(
                    titles = hub.titleRes.map { stringResource(it) },
                    selected = hub.selected,
                    onSelect = { index -> openHubTab(hub, index) },
                )
            }
            Box(Modifier.weight(1f)) {
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
                        modifier = Modifier,
                        onToggleGroup = viewModel::toggleGroup,
                        onClearAppCaches = onClearAllAppCaches,
                        viewModel = viewModel,
                        onDeepCheck = onDeepDuplicateCheck,
                        onOpenAppInfo = onOpenAppInfo,
                        onCloseSwipe = ::navigateBackInApp,
                        autoOpenSuggestion = openSuggestionRequested,
                        onSuggestionOpened = { openSuggestionRequested = false },
                        scrollToApps = scrollToAppsRequested,
                        onScrolledToApps = { scrollToAppsRequested = false },
                        onOpenPaywall = ::openPaywall,
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
                        modifier = Modifier,
                        // C1/C3: the same ToolSuggestion call as the Tools tile.
                        suggested = state.smartSummary()?.plan
                            ?.let { ToolSuggestion.suggestedFor(SuggestionTool.WHATSAPP, it) }
                            .orEmpty(),
                    )

                    DetailScreen.APP_CACHE -> AppCacheManagerScreen(
                        state = state,
                        onRequestUsageAccess = ::requestUsageWithDisclosure,
                        onRefresh = { viewModel.refreshAppCaches(force = true) },
                        onClearAllAppCaches = onClearAllAppCaches,
                        onClearOwnCache = viewModel::clearOwnAppCache,
                        onOpenAppDetails = onOpenAppDetails,
                        modifier = Modifier,
                    )

                    DetailScreen.APP_MANAGER -> AppManagerScreen(
                        state = state,
                        onRequestUsageAccess = ::requestUsageWithDisclosure,
                        onRefresh = viewModel::refreshInstalledApps,
                        onUninstallApp = onUninstallApp,
                        onOpenAppInfo = onOpenAppInfo,
                        modifier = Modifier,
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
                        modifier = Modifier,
                    )

                    DetailScreen.SETTINGS -> SettingsDetailScreen(
                        isPro = pro.isPro,
                        onOpenPro = {
                            onOpenPro()
                            // Y3: below 2 GB of real gain the app does not sell; Y5 when Pro is on.
                            if (pro.isPro || !proEstimate.qualifies) detailScreen = DetailScreen.PRO else openPaywall()
                        },
                        onOpenLanguageSettings = onOpenLanguageSettings,
                        onRateApp = onRateApp,
                        onSendFeedback = onSendFeedback,
                        onShareApp = onShareApp,
                        onOpenPrivacyAccess = { detailScreen = DetailScreen.ACCESS },
                        onOpenLegalPage = ::openLegalPage,
                        modifier = Modifier,
                        keptItemCount = state.keptItemCount,
                        onClearKeptItems = viewModel::clearKeptItems,
                        excludedFolders = state.excludedFolders,
                        onIncludeFolder = viewModel::includeFolder,
                        hasAllFilesAccess = state.hasAllFilesAccess,
                        hasUsageAccess = state.hasUsageAccess,
                        watchOn = watch.snapshot.settings.enabled,
                        onOpenWatch = ::openWatch,
                        onOpenLearned = { detailScreen = DetailScreen.LEARNED },
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
                        modifier = Modifier,
                    )

                    DetailScreen.PRO -> when {
                        pro.isPro -> com.mrzekai.depoakilli.ui.pro.ProOnScreen(
                            state = pro,
                            monthBytes = state.freeTier.monthBytes(),
                            watchNoticesThisMonth = watch.snapshot.sentThisMonth(),
                            onManageSubscription = onManageSubscription,
                            onRestore = onRestorePurchases,
                            onAddTime = onBuyPlan,
                            onOpenLegalPage = ::openLegalPage,
                            modifier = Modifier,
                        )
                        !proEstimate.qualifies -> com.mrzekai.depoakilli.ui.pro.HonestProScreen(
                            gainBytes = proEstimate.totalBytes,
                            onOk = ::navigateBackInApp,
                            onSeePlans = ::openPaywall,
                            modifier = Modifier,
                        )
                        else -> LaunchedEffect(Unit) {
                            // Not Pro (any more) and a real gain: the paywall, never this page.
                            navigateBackInApp()
                            openPaywall()
                        }
                    }

                    DetailScreen.WATCH -> com.mrzekai.depoakilli.ui.watch.WatchScreen(
                        ui = watchUi,
                        actions = watchActions,
                        modifier = Modifier,
                    )

                    DetailScreen.VIDEOS -> if (shrinkApp != null) {
                        com.mrzekai.depoakilli.ui.video.VideoShrinkFlow(
                            repo = shrinkApp.videoShrink,
                            actions = com.mrzekai.depoakilli.ui.video.VideoShrinkActions(
                                isPro = pro.isPro,
                                videoTrialUsed = state.freeTier.videoTrialUsed,
                                freeBytes = { state.storage.availableBytes },
                                onOpenPaywall = ::openPaywall,
                                onTrialUsed = { viewModel.markVideoTrialUsed() },
                                onProBytes = { bytes -> viewModel.addProBytes(bytes) },
                                onDeleteOriginalsNow = { ids -> viewModel.emptyTrash(ids) },
                                onDone = { navigateBackInApp() },
                            ),
                            modifier = Modifier,
                        )
                    }

                    DetailScreen.SIMILAR -> if (shrinkApp != null) {
                        com.mrzekai.depoakilli.ui.photo.SimilarPhotosFlow(
                            repo = shrinkApp.similarPhotos,
                            actions = com.mrzekai.depoakilli.ui.photo.SimilarActions(
                                isPro = pro.isPro,
                                freeGroupsUsed = state.freeTier.similarGroupsUsed,
                                onOpenPaywall = ::openPaywall,
                                onFreeGroupUsed = { viewModel.markSimilarGroupUsed() },
                                onProBytes = { bytes -> viewModel.addProBytes(bytes) },
                                decisionsLeft = com.mrzekai.depoakilli.ui.photo.decisionsLeft(pro.isPro, state.freeTier.decisionsToday()),
                                onDecision = { kind, delete -> viewModel.countPhotoDecision(kind, delete) },
                                screenshots = state.summary.items
                                    .filter { it.assessment.group == com.mrzekai.depoakilli.model.CleanupGroup.SCREENSHOT }
                                    .takeIf { it.isNotEmpty() }
                                    ?.let { list -> list.size to list.sumOf { it.sizeBytes } },
                                onOpenScreenshots = {
                                    viewModel.openSwipeReview(com.mrzekai.depoakilli.model.CleanupGroup.SCREENSHOT)
                                    detailScreen = DetailScreen.CLEAN_RESULTS
                                },
                            ),
                            modifier = Modifier,
                        )
                    }

                    DetailScreen.PLAN -> if (shrinkApp != null) {
                        val learner by shrinkApp.preferences.state.collectAsStateWithLifecycle()
                        val saved by shrinkApp.personalPlan.state.collectAsStateWithLifecycle()
                        com.mrzekai.depoakilli.ui.plan.PersonalPlanFlow(
                            inputs = com.mrzekai.depoakilli.ui.plan.PlanInputs(
                                candidates = planCandidates(state, videoResult, similarResult, pro.isPro),
                                totalBytes = state.storage.totalBytes,
                                availableBytes = state.storage.availableBytes,
                                isPro = pro.isPro,
                                learner = learner,
                                saved = saved,
                                titleOf = { c -> planStepTitle(c.key) },
                            ),
                            actions = com.mrzekai.depoakilli.ui.plan.PlanActions(
                                onStart = { shrinkApp.personalPlan.save(it) },
                                onClear = { shrinkApp.personalPlan.clear() },
                                onOpenStep = { c ->
                                    when (c.key) {
                                        com.mrzekai.depoakilli.ui.plan.NO_LOSS_KEY -> {
                                            openSuggestionRequested = false
                                            openFindings()
                                        }
                                        com.mrzekai.depoakilli.ui.plan.SHRINK_KEY -> detailScreen = DetailScreen.VIDEOS
                                        com.mrzekai.depoakilli.ui.plan.SIMILAR_KEY -> detailScreen = DetailScreen.SIMILAR
                                        else -> com.mrzekai.depoakilli.model.CleanupGroup.entries.firstOrNull { it.name == c.key }?.let { group ->
                                            val items = state.summary.plan.askRows.firstOrNull { it.group == group }?.items
                                                ?: state.summary.items.filter { it.assessment.group.rowGroup == group }
                                            if (group in com.mrzekai.depoakilli.ui.review.SwipeReviewState.SWIPE_GROUPS) {
                                                viewModel.openSwipeReview(group)
                                            } else {
                                                viewModel.openPlanGroupGrid(group, items)
                                            }
                                            detailScreen = DetailScreen.CLEAN_RESULTS
                                        }
                                    }
                                },
                                onOpenFilters = { detailScreen = DetailScreen.FILTERS },
                                onOpenLearned = { detailScreen = DetailScreen.LEARNED },
                            ),
                            modifier = Modifier,
                        )
                    }

                    DetailScreen.FILTERS -> com.mrzekai.depoakilli.ui.plan.FiltersScreen(
                        count = { filter ->
                            val files = viewModel.filterFiles(filter)
                            com.mrzekai.depoakilli.ui.plan.FilterResult(files.size, files.sumOf { it.sizeBytes })
                        },
                        onShow = { filter, label ->
                            viewModel.openFilterReview(filter, label)
                            detailScreen = DetailScreen.CLEAN_RESULTS
                        },
                        modifier = Modifier,
                    )

                    DetailScreen.LEARNED -> if (shrinkApp != null) {
                        val learner by shrinkApp.preferences.state.collectAsStateWithLifecycle()
                        com.mrzekai.depoakilli.ui.plan.LearnedScreen(
                            learner = learner,
                            titleOf = { key -> planStepTitle(key) },
                            onReset = { shrinkApp.preferences.reset() },
                            modifier = Modifier,
                        )
                    }

                    DetailScreen.PRIVACY,
                    DetailScreen.TERMS,
                    DetailScreen.ABOUT -> LegalDetailScreen(
                        page = when (detailScreen) {
                            DetailScreen.PRIVACY -> LegalPage.PRIVACY
                            DetailScreen.TERMS -> LegalPage.TERMS
                            else -> LegalPage.ABOUT
                        },
                        info = state.deviceInfo,
                        modifier = Modifier,
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
                                // E1: "Apps hold X more" opens the findings at the apps section.
                                onOpenAppsSection = {
                                    scrollToAppsRequested = true
                                    openFindings()
                                },
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
                                modifier = Modifier,
                                proCard = if (
                                    com.mrzekai.depoakilli.pro.FreeTierRules.showProCard(
                                        isPro = pro.isPro,
                                        estimate = proEstimate,
                                        nowMillis = System.currentTimeMillis(),
                                        hiddenUntilMillis = state.freeTier.proCardHiddenUntil,
                                    )
                                ) {
                                    HomeProCard(
                                        estimate = proEstimate,
                                        trialDays = pro.plans.firstOrNull { it.kind == com.mrzekai.depoakilli.billing.ProPlanKind.YEARLY }?.trialDays,
                                        onOpenPaywall = ::openPaywall,
                                        onNotNow = viewModel::snoozeProCard,
                                    )
                                } else {
                                    null
                                },
                                // Faz 3A-1 Z1: the watch line, its week card or the one-time offer.
                                watchLine = if (watch.snapshot.settings.enabled) {
                                    { com.mrzekai.depoakilli.ui.watch.WatchHomeLine(watchUi, onOpen = ::openWatch) }
                                } else {
                                    null
                                },
                                watchCard = when {
                                    watch.snapshot.settings.enabled && watch.snapshot.growth != null -> {
                                        { com.mrzekai.depoakilli.ui.watch.WatchHomeCard(watchUi, watchActions, Modifier.padding(top = 14.dp)) }
                                    }
                                    !watch.snapshot.settings.enabled && !watch.snapshot.promoDone &&
                                        (state.freeTier.firstCleanupUsed || state.trashEntries.isNotEmpty()) -> {
                                        {
                                            com.mrzekai.depoakilli.ui.watch.WatchPromoCard(
                                                onOpen = {
                                                    watch.markPromoDone()
                                                    watchIntroOpen = true
                                                },
                                                onNotNow = watch.markPromoDone,
                                                modifier = Modifier.padding(top = 14.dp),
                                            )
                                        }
                                    }
                                    else -> null
                                },
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
                            onOpenAppManager = {
                                detailScreen = DetailScreen.APP_MANAGER
                                viewModel.refreshInstalledApps()
                            },
                            videoSavingBytes = videoResult?.totalSavingBytes,
                            onOpenVideos = { detailScreen = DetailScreen.VIDEOS },
                            onOpenPlan = { detailScreen = DetailScreen.PLAN },
                            onOpenFilters = { detailScreen = DetailScreen.FILTERS },
                            similarBytes = similarResult?.trashBytes,
                            onOpenSimilar = {
                                detailScreen = DetailScreen.SIMILAR
                                if (shrinkApp?.similarPhotos?.state?.value is com.mrzekai.depoakilli.photo.SimilarState.Idle) shrinkApp.similarPhotos.start()
                            },
                            modifier = Modifier,
                        )

                        AppTab.TRASH -> TrashScreen(
                            entries = state.trashEntries,
                            onRefresh = viewModel::refreshTrash,
                            onRestore = { ids -> viewModel.restoreTrash(ids) },
                            onDeleteForever = { ids -> viewModel.emptyTrash(ids) },
                            modifier = Modifier,
                        )
                    }
                }
            }
        }
    }

    // Faz 3A-1 Z2: over everything; "Not now" and Back close it.
    if (watchIntroOpen) {
        com.mrzekai.depoakilli.ui.watch.WatchIntroOverlay(
            onTurnOn = ::turnWatchOn,
            onNotNow = { watchIntroOpen = false },
        )
    }
    if (hibernationSheet) {
        com.mrzekai.depoakilli.ui.watch.WatchHibernationSheet(
            onOpenSetting = {
                hibernationSheet = false
                watch.openSystem(com.mrzekai.depoakilli.watch.WatchHibernation.settingsIntent(watchContext))
            },
            onDismiss = { hibernationSheet = false },
        )
    }

    // Y2: over everything (bottom bar included); ✕ and system Back close it.
    if (paywallOpen) {
        com.mrzekai.depoakilli.ui.pro.PaywallScreen(
            state = pro,
            facts = com.mrzekai.depoakilli.ui.pro.PaywallFacts(
                estimate = proEstimate,
                totalBytes = state.storage.totalBytes,
                availableBytes = state.storage.availableBytes,
                averagePhotoBytes = averagePhotoBytes,
            ),
            onBuy = onBuyPlan,
            onRestore = onRestorePurchases,
            onOpenLegalPage = { page ->
                paywallOpen = false
                openLegalPage(page)
            },
            onClose = { paywallOpen = false },
        )
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

    run {
        state.cleanupResult?.let { result ->
            val dismissResultAndReturn: () -> Unit = {
                val returnScreen = detailScreen
                val returnTabIndex = selectedTabIndex

                viewModel.dismissCleanupResult()
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
                onSystemDismiss = {
                    dismissResultAndReturn()
                },
                onUndo = viewModel::undoCleanup,
                onEmptyTrash = { viewModel.emptyTrash(result.trashEntryIds) },
                nextReview = if (result.kind == CleanupResultKind.FILES && state.scanFocus == ScanFocus.SMART) {
                    state.summary.plan.askRows.firstOrNull { it.bytes >= CleaningThresholds.GROUP_MIN_BYTES }
                } else {
                    null
                },
                // C2: once tier 1 is done, "next" is the "My pick" card.
                nextSuggestedBytes = state.planFigures()
                    ?.takeIf {
                        result.kind == CleanupResultKind.FILES &&
                            state.scanFocus == ScanFocus.SMART &&
                            it.showsSuggestion &&
                            it.safeBytes < CleaningThresholds.PLAN_MIN_BYTES &&
                            com.mrzekai.depoakilli.pro.FreeTierRules.canUseOneTapPick(pro.isPro, state.freeTier.firstCleanupUsed)
                    }
                    ?.suggestedBytes ?: 0L,
                onOpenSuggested = {
                    dismissResultAndReturn()
                    legalReturnScreen = null
                    detailScreen = DetailScreen.CLEAN_RESULTS
                    openSuggestionRequested = true
                },
                onReviewNext = {
                    val next = state.summary.plan.askRows.firstOrNull()
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
                onDone = {
                    val succeeded =
                        result.operationSucceeded &&
                            (
                                result.deletedCount > 0 ||
                                    result.deletedBytes > 0L ||
                                    result.kind == CleanupResultKind.SYSTEM_CACHE
                                )

                    dismissResultAndReturn()

                    if (succeeded && result.failedCount == 0) onCleanupSucceeded()
                    if (succeeded) onCleanupResultDismissed()
                },
            )
        }
    }

    }
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

/** Tool scans (everything but the Smart and Deep scans) end in a tool result list (B7). */
/** The two-tab tools of C4. */
internal enum class HubKind { APPS, STORAGE }

internal data class HubSpec(val kind: HubKind, val titleRes: List<Int>, val selected: Int)

/** Which two-tab tool (if any) the open detail screen belongs to. */
internal fun hubOf(detailScreen: DetailScreen?, state: CleanerUiState): HubSpec? = when {
    detailScreen == DetailScreen.APP_MANAGER ->
        HubSpec(HubKind.APPS, listOf(R.string.home_hard_apps, R.string.tool_app_cache), 0)
    detailScreen == DetailScreen.APP_CACHE ->
        HubSpec(HubKind.APPS, listOf(R.string.home_hard_apps, R.string.tool_app_cache), 1)
    detailScreen == DetailScreen.CLEAN_RESULTS &&
        state.scanFocus == ScanFocus.ANALYZE &&
        state.storageReview.type == null ->
        HubSpec(HubKind.STORAGE, listOf(R.string.storage_analyzer_title, R.string.storage_change_title), 0)
    detailScreen == DetailScreen.STORAGE_CHANGE ->
        HubSpec(HubKind.STORAGE, listOf(R.string.storage_analyzer_title, R.string.storage_change_title), 1)
    else -> null
}

/**
 * Faz 3C: the measured sources of space for the Personal Plan. Bytes come
 * from the current scans only; nothing here is a guess.
 */
private fun planCandidates(
    state: CleanerUiState,
    videos: com.mrzekai.depoakilli.video.VideoCandidateFinder.Result?,
    similar: com.mrzekai.depoakilli.photo.SimilarFinder.Result?,
    isPro: Boolean,
): List<com.mrzekai.depoakilli.plan.PlanCandidate> {
    val now = System.currentTimeMillis()
    val plan = state.summary.plan
    val out = mutableListOf<com.mrzekai.depoakilli.plan.PlanCandidate>()
    val auto = plan.autoItems
    if (auto.isNotEmpty()) {
        out += com.mrzekai.depoakilli.plan.PlanCandidate(
            com.mrzekai.depoakilli.ui.plan.NO_LOSS_KEY,
            com.mrzekai.depoakilli.plan.StepKind.NO_LOSS,
            auto.sumOf { it.sizeBytes },
            auto.size,
        )
    }
    if (com.mrzekai.depoakilli.pro.ProGainEstimator.VIDEO_COMPRESSION_AVAILABLE && videos != null && videos.candidates.isNotEmpty()) {
        out += com.mrzekai.depoakilli.plan.PlanCandidate(
            com.mrzekai.depoakilli.ui.plan.SHRINK_KEY,
            com.mrzekai.depoakilli.plan.StepKind.SHRINK,
            videos.totalSavingBytes,
            videos.candidates.size,
            proOnly = !isPro,
            estimate = true,
        )
    }
    if (com.mrzekai.depoakilli.pro.ProGainEstimator.SIMILAR_PHOTOS_AVAILABLE && similar != null && similar.groups.isNotEmpty()) {
        out += com.mrzekai.depoakilli.plan.PlanCandidate(
            com.mrzekai.depoakilli.ui.plan.SIMILAR_KEY,
            com.mrzekai.depoakilli.plan.StepKind.SIMILAR,
            similar.trashBytes,
            similar.trashCount,
            proOnly = !isPro,
        )
    }
    // "My pick" and "I'll ask you" rows, one step per kind (each opens its own review).
    plan.review.flatMap { it.items }.groupBy { it.assessment.group.rowGroup }.forEach { (group, items) ->
        out += com.mrzekai.depoakilli.plan.PlanCandidate(
            group.name,
            com.mrzekai.depoakilli.plan.StepKind.ASK,
            items.sumOf { it.sizeBytes },
            items.size,
            ageDays = items.map { it.ageDays(now) }.average().toLong(),
        )
    }
    return out
}

@androidx.compose.runtime.Composable
private fun planStepTitle(key: String): String = when (key) {
    com.mrzekai.depoakilli.ui.plan.NO_LOSS_KEY -> androidx.compose.ui.res.stringResource(R.string.plan_step_no_loss)
    com.mrzekai.depoakilli.ui.plan.SHRINK_KEY -> androidx.compose.ui.res.stringResource(R.string.video_title)
    com.mrzekai.depoakilli.ui.plan.SIMILAR_KEY -> androidx.compose.ui.res.stringResource(R.string.similar_title)
    else -> com.mrzekai.depoakilli.model.CleanupGroup.entries.firstOrNull { it.name == key }
        ?.let { androidx.compose.ui.res.stringResource(it.titleRes) } ?: key
}
