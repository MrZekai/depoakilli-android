package com.mrzekai.depoakilli.ui

import androidx.annotation.StringRes
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.ads.BannerAd
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.model.CleanCategory
import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.model.ScanFocus
import com.mrzekai.depoakilli.model.ScanSummary
import com.mrzekai.depoakilli.model.StorageSnapshot
import com.mrzekai.depoakilli.ui.theme.ElectricBlue
import com.mrzekai.depoakilli.ui.theme.Lime400
import com.mrzekai.depoakilli.ui.theme.WhatsAppGreen

private enum class AppTab(@StringRes val titleRes: Int, val icon: ImageVector) {
    HOME(R.string.tab_home, Icons.Outlined.Home),
    TOOLS(R.string.tab_tools, Icons.Outlined.CleaningServices),
    ME(R.string.tab_profile, Icons.Outlined.Android),
}

private enum class DetailScreen(@StringRes val titleRes: Int) {
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

private enum class AccessDisclosure {
    ALL_FILES,
    USAGE_ACCESS,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanerApp(
    viewModel: CleanerViewModel,
    canRequestAds: Boolean,
    fullScreenAdActive: Boolean,
    privacyOptionsRequired: Boolean,
    onRequestAllFilesAccess: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onClearAllAppCaches: () -> Unit,
    onOpenAppDetails: (String) -> Unit,
    onPrepareCleanup: (Set<String>?) -> Unit,
    onPrepareStorageCleanup: () -> Unit,
    onPrepareWhatsAppCleanup: ((Boolean) -> Unit) -> Unit,
    onCleanupResultDismissed: () -> Unit,
    onUninstallApp: (String) -> Unit,
    onOpenLanguageSettings: () -> Unit,
    onShowPrivacyOptions: () -> Unit,
    onRateApp: () -> Unit,
    onSendFeedback: () -> Unit,
    onShareApp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var detailScreen by rememberSaveable { mutableStateOf<DetailScreen?>(null) }
    var legalReturnScreen by rememberSaveable { mutableStateOf<DetailScreen?>(null) }
    var accessDisclosure by rememberSaveable { mutableStateOf<AccessDisclosure?>(null) }
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
            detailScreen == DetailScreen.CLEAN_RESULTS && state.storageReview.type != null ->
                viewModel.closeStorageReview()
            detailScreen == DetailScreen.CLEAN_RESULTS && state.smartCategoryReview != null ->
                viewModel.closeSmartCategoryReview()
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

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (detailScreen != DetailScreen.WHATSAPP && (detailScreen != null || AppTab.entries[selectedTabIndex] != AppTab.HOME)) {
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
                        Text(
                            stringResource(titleRes),
                            fontWeight = FontWeight.Black,
                        )
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
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            }
        },
        bottomBar = {
            val screenAllowsBanner = when (detailScreen) {
                null -> when (AppTab.entries[selectedTabIndex]) {
                    AppTab.HOME,
                    AppTab.TOOLS -> true
                    AppTab.ME -> false
                }
                DetailScreen.CLEAN_RESULTS -> state.hasAllFilesAccess
                DetailScreen.WHATSAPP -> state.hasWhatsAppAccess
                DetailScreen.APP_CACHE,
                DetailScreen.APP_MANAGER,
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
                    !state.scanning &&
                    !state.dashboardRefreshing &&
                    !state.whatsAppScanning &&
                    !state.cleanupInProgress &&
                    state.cleanupResult == null

            if (detailScreen == null) {
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .navigationBarsPadding(),
                ) {
                    if (bannerAllowed) {
                        BannerAd(canRequestAds = true)
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .18f))
                    }
                    NavigationBar(containerColor = Color(0xFF07132C)) {
                        AppTab.entries.forEachIndexed { index, tab ->
                            val title = stringResource(tab.titleRes)
                            NavigationBarItem(
                                selected = selectedTabIndex == index,
                                onClick = { selectedTabIndex = index },
                                icon = { Icon(tab.icon, contentDescription = title) },
                                label = { Text(title) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = ElectricBlue,
                                    selectedTextColor = ElectricBlue,
                                    indicatorColor = Color(0xFF123A75),
                                ),
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .background(Color(0xFF06112A))
                        .navigationBarsPadding(),
                ) {
                    if (bannerAllowed) {
                        HorizontalDivider(color = Color.White.copy(alpha = .10f))
                        Spacer(Modifier.height(6.dp))
                        BannerAd(canRequestAds = true)
                    }
                }
            }
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
                onOpenLanguageSettings = onOpenLanguageSettings,
                onRateApp = onRateApp,
                onSendFeedback = onSendFeedback,
                onShareApp = onShareApp,
                onShowPrivacyOptions = onShowPrivacyOptions,
                onOpenPrivacyAccess = { detailScreen = DetailScreen.ACCESS },
                onOpenLegalPage = ::openLegalPage,
                modifier = Modifier.padding(padding),
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
                AppTab.HOME -> NeonDashboardScreen(
                    state = state,
                    onSmartClean = { launchScan(ScanFocus.SMART) },
                    onOpenWhatsApp = {
                        detailScreen = DetailScreen.WHATSAPP
                        if (state.hasWhatsAppAccess) viewModel.scanWhatsAppLibrary()
                    },
                    onLargeFiles = { launchScan(ScanFocus.LARGE_FILES) },
                    onApks = { launchScan(ScanFocus.APKS) },
                    onOpenAppCache = {
                        detailScreen = DetailScreen.APP_CACHE
                        viewModel.refreshAppCaches()
                    },
                    onOpenStorageChange = { detailScreen = DetailScreen.STORAGE_CHANGE },
                    onDownloads = { launchScan(ScanFocus.DOWNLOADS) },
                    onOpenTools = { selectedTabIndex = AppTab.TOOLS.ordinal },
                    onRefresh = viewModel::refreshDashboard,
                    modifier = Modifier.padding(padding),
                )

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

                AppTab.ME -> SettingsDetailScreen(
                    privacyOptionsRequired = privacyOptionsRequired,
                    onOpenLanguageSettings = onOpenLanguageSettings,
                    onRateApp = onRateApp,
                    onSendFeedback = onSendFeedback,
                    onShareApp = onShareApp,
                    onShowPrivacyOptions = onShowPrivacyOptions,
                    onOpenPrivacyAccess = { detailScreen = DetailScreen.ACCESS },
                    onOpenLegalPage = ::openLegalPage,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }

    accessDisclosure?.let { disclosure ->
        AlertDialog(
            onDismissRequest = { accessDisclosure = null },
            title = {
                Text(
                    stringResource(
                        if (disclosure == AccessDisclosure.ALL_FILES) {
                            R.string.disclosure_all_files_title
                        } else {
                            R.string.disclosure_usage_title
                        },
                    ),
                    fontWeight = FontWeight.Black,
                )
            },
            text = {
                Text(
                    stringResource(
                        if (disclosure == AccessDisclosure.ALL_FILES) {
                            R.string.disclosure_all_files_body
                        } else {
                            R.string.disclosure_usage_body
                        },
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        accessDisclosure = null
                        if (disclosure == AccessDisclosure.ALL_FILES) {
                            onRequestAllFilesAccess()
                        } else {
                            onRequestUsageAccess()
                        }
                    },
                ) {
                    Text(stringResource(R.string.disclosure_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { accessDisclosure = null }) {
                    Text(stringResource(R.string.disclosure_not_now))
                }
            },
        )
    }

    if (!fullScreenAdActive) {
        state.cleanupResult?.let { result ->
            val dismissResultAndReturn: () -> Unit = {
                val returnScreen = detailScreen
                val returnTabIndex = selectedTabIndex

                viewModel.dismissCleanupResult()
                legalReturnScreen = null

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
                onSystemDismiss = {
                    dismissResultAndReturn()
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

                    if (eligibleForNaturalBreakAd && !resultAdPresented) {
                        onCleanupResultDismissed()
                    }
                },
            )
        }
    }

        if (fullScreenAdActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CircularProgressIndicator(color = ElectricBlue)
                    Text(
                        stringResource(R.string.app_name),
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
    }
}

@Composable
private fun CleanScreen(
    state: CleanerUiState,
    onRequestAllFilesAccess: () -> Unit,
    onScan: () -> Unit,
    onToggleItem: (String) -> Unit,
    onToggleCategory: (CleanCategory) -> Unit,
    onSetItemsSelected: (Set<String>, Boolean) -> Unit,
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
        )
        return
    }

    if (state.scanFocus in setOf(
            ScanFocus.DEEP,
            ScanFocus.JUNK,
            ScanFocus.DUPLICATES,
            ScanFocus.LARGE_FILES,
            ScanFocus.MEDIA,
            ScanFocus.SCREENSHOTS,
            ScanFocus.DOWNLOADS,
            ScanFocus.APKS,
        )
    ) {
        PremiumCleanerToolScreen(
            state = state,
            onRequestAllFilesAccess = onRequestAllFilesAccess,
            onScan = onScan,
            onToggleItem = onToggleItem,
            onSetItemsSelected = onSetItemsSelected,
            onClean = onClean,
            modifier = modifier,
        )
        return
    }

    when {
        !state.hasAllFilesAccess -> Box(modifier.fillMaxSize().padding(22.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(Icons.Outlined.Security, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(54.dp))
                Text(stringResource(R.string.all_files_access_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(stringResource(R.string.all_files_access_clean_screen), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onRequestAllFilesAccess) { Text(stringResource(R.string.grant_access)) }
            }
        }

        state.scanning -> ScanningState(state = state, modifier = modifier)
        !state.lastScanCompleted -> EmptyScanState(state = state, onScan = onScan, modifier = modifier)
        else -> ScanResults(
            state = state,
            onScan = onScan,
            onToggleItem = onToggleItem,
            onToggleCategory = onToggleCategory,
            onClean = { onClean(null) },
            modifier = modifier,
        )
    }
}

@Composable
private fun ScanningState(state: CleanerUiState, modifier: Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(58.dp))
            Text(stringResource(R.string.scan_in_progress_v050), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(
                stringResource(R.string.scan_live_counter, state.scanProgressFiles, state.scanProgressDirectories),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(R.string.scan_private), style = MaterialTheme.typography.bodySmall, color = ElectricBlue)
        }
    }
}

@Composable
private fun EmptyScanState(state: CleanerUiState, onScan: () -> Unit, modifier: Modifier) {
    Box(modifier.fillMaxSize().padding(22.dp), contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(28.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(categoryFocusIcon(state.scanFocus), contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(56.dp))
                Text(stringResource(scanFocusTitleRes(state.scanFocus)), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(stringResource(R.string.safe_ai_cleaning_description_v050), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.start_scan)) }
            }
        }
    }
}

@Composable
private fun ScanResults(
    state: CleanerUiState,
    onScan: () -> Unit,
    onToggleItem: (String) -> Unit,
    onToggleCategory: (CleanCategory) -> Unit,
    onClean: () -> Unit,
    modifier: Modifier,
) {
    val summary = state.summary
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScanSummaryHero(summary, state.scanFocus)
        }
        if (summary.storageTypes.isNotEmpty()) {
            item {
                Card(shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(stringResource(R.string.storage_analyzer_title), fontWeight = FontWeight.Black)
                        summary.storageTypes.take(7).forEach { stat ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(stat.type.titleRes), style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "${stat.fileCount} • ${ByteFormatter.format(stat.totalBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (summary.items.isEmpty() && state.scanFocus != ScanFocus.ANALYZE) {
            item {
                Text(stringResource(R.string.no_safe_suggestions), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (summary.items.isNotEmpty()) {
            summary.byCategory.forEach { (category, categoryItems) ->
                item(key = "header-${category.name}") {
                    CategoryHeader(category, categoryItems, onToggle = { onToggleCategory(category) })
                }
                items(categoryItems, key = CleanableItem::id) { item ->
                    FileResultRow(item, onToggleItem)
                }
            }
        }
        if (summary.scanLimitReached) {
            item { Text(stringResource(R.string.scan_limit_note_v050), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
        item {
            TextButton(onClick = onScan, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.scan_again)) }
        }
        if (summary.selectedItems.isNotEmpty()) {
            item {
                Button(onClick = onClean, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    Text(stringResource(R.string.clean_action, ByteFormatter.format(summary.selectedBytes)), fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun ScanSummaryHero(summary: ScanSummary, focus: ScanFocus) {
    Card(shape = RoundedCornerShape(26.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(Color(0xFF071B55), Color(0xFF0A67DF))))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(stringResource(scanFocusTitleRes(focus)), color = Color.White.copy(alpha = .8f), fontWeight = FontWeight.Bold)
            Text(ByteFormatter.format(summary.selectedBytes), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
            Text(stringResource(R.string.cleanable_space), color = Color(0xFF99F2C1), fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.scan_stats_v050, summary.scannedFileCount, ByteFormatter.format(summary.scannedBytes), summary.selectedItems.size),
                color = Color.White.copy(alpha = .8f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun CategoryHeader(
    category: CleanCategory,
    items: List<CleanableItem>,
    onToggle: () -> Unit,
) {
    val allSelected = items.isNotEmpty() && items.all(CleanableItem::selected)
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(categoryIcon(category), contentDescription = null, tint = ElectricBlue)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(category.titleRes), fontWeight = FontWeight.Black)
            Text(
                stringResource(R.string.category_summary, items.size, ByteFormatter.format(items.sumOf(CleanableItem::sizeBytes))),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Checkbox(checked = allSelected, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun FileResultRow(item: CleanableItem, onToggleItem: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onToggleItem(item.id) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = item.selected, onCheckedChange = { onToggleItem(item.id) })
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    item.relativePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(item.assessment.reasonRes, *item.assessment.reasonArgs.toTypedArray()),
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricBlue,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                item.protectedDuplicateName?.let {
                    Text(
                        stringResource(R.string.duplicate_original_kept, it),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF0C8A5B),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(ByteFormatter.format(item.sizeBytes), fontWeight = FontWeight.Black)
        }
    }
}

private fun scanFocusTitleRes(focus: ScanFocus): Int = when (focus) {
    ScanFocus.SMART -> R.string.scan_focus_smart
    ScanFocus.DEEP -> R.string.scan_focus_deep
    ScanFocus.JUNK -> R.string.scan_focus_junk
    ScanFocus.DUPLICATES -> R.string.scan_focus_duplicates
    ScanFocus.LARGE_FILES -> R.string.scan_focus_large
    ScanFocus.WHATSAPP -> R.string.scan_focus_whatsapp
    ScanFocus.MEDIA -> R.string.scan_focus_media
    ScanFocus.SCREENSHOTS -> R.string.category_screenshots
    ScanFocus.DOWNLOADS -> R.string.scan_focus_downloads
    ScanFocus.APKS -> R.string.scan_focus_apks
    ScanFocus.ANALYZE -> R.string.scan_focus_analyze
}

private fun categoryFocusIcon(focus: ScanFocus): ImageVector = when (focus) {
    ScanFocus.SMART -> Icons.Outlined.AutoAwesome
    ScanFocus.DEEP -> Icons.Outlined.CleaningServices
    ScanFocus.JUNK -> Icons.Outlined.DeleteSweep
    ScanFocus.DUPLICATES -> Icons.Outlined.ContentCopy
    ScanFocus.LARGE_FILES -> Icons.Outlined.VideoFile
    ScanFocus.WHATSAPP -> Icons.Outlined.Chat
    ScanFocus.MEDIA -> Icons.Outlined.PhotoLibrary
    ScanFocus.SCREENSHOTS -> Icons.Outlined.PhotoLibrary
    ScanFocus.DOWNLOADS -> Icons.Outlined.Download
    ScanFocus.APKS -> Icons.Outlined.Android
    ScanFocus.ANALYZE -> Icons.Outlined.Storage
}

private fun categoryIcon(category: CleanCategory): ImageVector = when (category) {
    CleanCategory.JUNK -> Icons.Outlined.DeleteSweep
    CleanCategory.DUPLICATE -> Icons.Outlined.ContentCopy
    CleanCategory.SCREENSHOT -> Icons.Outlined.PhotoLibrary
    CleanCategory.LARGE_FILE -> Icons.Outlined.VideoFile
    CleanCategory.OLD_DOWNLOAD -> Icons.Outlined.Folder
    CleanCategory.APK_PACKAGE -> Icons.Outlined.Android
    CleanCategory.APP_CACHE -> Icons.Outlined.CleaningServices
    CleanCategory.WHATSAPP_MEDIA -> Icons.Outlined.Chat
}
