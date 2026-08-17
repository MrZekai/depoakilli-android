package com.mrzekai.depoakilli.ui

import androidx.annotation.StringRes
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
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.VideoFile
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
    SECURITY(R.string.tab_security, Icons.Outlined.Security),
    PROFILE(R.string.tab_profile, Icons.Outlined.Android),
}

private enum class DetailScreen(@StringRes val titleRes: Int) {
    CLEAN_RESULTS(R.string.clean_results_title),
    WHATSAPP(R.string.whatsapp_cleaner_title),
    APP_CACHE(R.string.cache_manager_title),
    APP_MANAGER(R.string.app_manager_title),
    SETTINGS(R.string.settings_title),
    PRIVACY(R.string.privacy_policy),
    TERMS(R.string.terms_of_service),
    ABOUT(R.string.about_app),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanerApp(
    viewModel: CleanerViewModel,
    canRequestAds: Boolean,
    privacyOptionsRequired: Boolean,
    onRequestAllFilesAccess: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onClearAllAppCaches: () -> Unit,
    onPrepareCleanup: () -> Unit,
    onOptimizeMemory: () -> Unit,
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
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    fun launchScan(focus: ScanFocus) {
        detailScreen = DetailScreen.CLEAN_RESULTS
        if (!state.hasAllFilesAccess) {
            viewModel.queueScanAfterPermission(focus)
            onRequestAllFilesAccess()
        } else {
            viewModel.scan(focus)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (detailScreen != null || AppTab.entries[selectedTabIndex] != AppTab.HOME) {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(detailScreen?.titleRes ?: AppTab.entries[selectedTabIndex].titleRes),
                            fontWeight = FontWeight.Black,
                        )
                    },
                    navigationIcon = {
                        if (detailScreen != null) {
                            IconButton(onClick = { detailScreen = null }) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            }
        },
        bottomBar = {
            if (detailScreen == null) {
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .navigationBarsPadding(),
                ) {
                    BannerAd(canRequestAds = canRequestAds)
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .18f))
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
            }
        },
    ) { padding ->
        when (detailScreen) {
            DetailScreen.CLEAN_RESULTS -> CleanScreen(
                state = state,
                onRequestAllFilesAccess = onRequestAllFilesAccess,
                onScan = { viewModel.scan(state.scanFocus) },
                onToggleItem = viewModel::toggleItem,
                onToggleCategory = viewModel::toggleCategory,
                onClean = onPrepareCleanup,
                modifier = Modifier.padding(padding),
            )

            DetailScreen.WHATSAPP -> WhatsAppCleanerDetailScreen(
                state = state,
                onRequestAccess = onRequestAllFilesAccess,
                onScan = viewModel::scanWhatsAppLibrary,
                onToggleItem = viewModel::toggleWhatsAppItem,
                onToggleCategory = viewModel::toggleWhatsAppCategory,
                onDeleteSelected = viewModel::deleteSelectedWhatsApp,
                modifier = Modifier.padding(padding),
            )

            DetailScreen.APP_CACHE -> AppCacheManagerScreen(
                state = state,
                onRequestUsageAccess = onRequestUsageAccess,
                onRefresh = { viewModel.refreshAppCaches(force = true) },
                onClearAllAppCaches = onClearAllAppCaches,
                onClearOwnCache = viewModel::clearOwnAppCache,
                modifier = Modifier.padding(padding),
            )

            DetailScreen.APP_MANAGER -> AppManagerScreen(
                state = state,
                onRequestUsageAccess = onRequestUsageAccess,
                onRefresh = viewModel::refreshInstalledApps,
                onUninstallApp = onUninstallApp,
                modifier = Modifier.padding(padding),
            )

            DetailScreen.SETTINGS -> SettingsDetailScreen(
                privacyOptionsRequired = privacyOptionsRequired,
                onOpenLanguageSettings = onOpenLanguageSettings,
                onRateApp = onRateApp,
                onSendFeedback = onSendFeedback,
                onShareApp = onShareApp,
                onShowPrivacyOptions = onShowPrivacyOptions,
                onOpenLegalPage = { page ->
                    detailScreen = when (page) {
                        LegalPage.PRIVACY -> DetailScreen.PRIVACY
                        LegalPage.TERMS -> DetailScreen.TERMS
                        LegalPage.ABOUT -> DetailScreen.ABOUT
                    }
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
                    onOpenProfile = { selectedTabIndex = AppTab.PROFILE.ordinal },
                    onSmartClean = { launchScan(ScanFocus.SMART) },
                    onOpenWhatsApp = {
                        detailScreen = DetailScreen.WHATSAPP
                        if (state.hasWhatsAppAccess) viewModel.scanWhatsAppLibrary()
                    },
                    onDuplicates = { launchScan(ScanFocus.DUPLICATES) },
                    onLargeFiles = { launchScan(ScanFocus.LARGE_FILES) },
                    onApks = { launchScan(ScanFocus.APKS) },
                    onMedia = { launchScan(ScanFocus.MEDIA) },
                    onDeepClean = { launchScan(ScanFocus.DEEP) },
                    onOpenAppCache = { detailScreen = DetailScreen.APP_CACHE },
                    onRamOptimize = onOptimizeMemory,
                    modifier = Modifier.padding(padding),
                )

                AppTab.TOOLS -> DeviceCenterScreen(
                    state = state,
                    onScan = ::launchScan,
                    onRequestAllFilesAccess = onRequestAllFilesAccess,
                    onOpenWhatsApp = {
                        detailScreen = DetailScreen.WHATSAPP
                        if (state.hasWhatsAppAccess) viewModel.scanWhatsAppLibrary()
                    },
                    onOpenCache = { detailScreen = DetailScreen.APP_CACHE },
                    onOpenAppManager = {
                        detailScreen = DetailScreen.APP_MANAGER
                        viewModel.refreshInstalledApps()
                    },
                    onOptimizeMemory = onOptimizeMemory,
                    onOpenSettings = { selectedTabIndex = AppTab.PROFILE.ordinal },
                    modifier = Modifier.padding(padding),
                )

                AppTab.SECURITY -> SecurityCenterScreen(
                    state = state,
                    onRequestAllFilesAccess = onRequestAllFilesAccess,
                    onRequestUsageAccess = onRequestUsageAccess,
                    onOpenPrivacy = { detailScreen = DetailScreen.PRIVACY },
                    modifier = Modifier.padding(padding),
                )

                AppTab.PROFILE -> SettingsDetailScreen(
                    privacyOptionsRequired = privacyOptionsRequired,
                    onOpenLanguageSettings = onOpenLanguageSettings,
                    onRateApp = onRateApp,
                    onSendFeedback = onSendFeedback,
                    onShareApp = onShareApp,
                    onShowPrivacyOptions = onShowPrivacyOptions,
                    onOpenLegalPage = { page ->
                        detailScreen = when (page) {
                            LegalPage.PRIVACY -> DetailScreen.PRIVACY
                            LegalPage.TERMS -> DetailScreen.TERMS
                            LegalPage.ABOUT -> DetailScreen.ABOUT
                        }
                    },
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    state: CleanerUiState,
    onScan: (ScanFocus) -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onOpenWhatsApp: () -> Unit,
    onOpenCache: () -> Unit,
    onOptimizeMemory: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SmartCleanerHero(
                state = state,
                onScan = { onScan(ScanFocus.SMART) },
                onOpenSettings = onOpenSettings,
            )
        }
        if (!state.hasAllFilesAccess) {
            item { AllFilesAccessCard(onRequestAllFilesAccess) }
        }
        item {
            Text(
                stringResource(R.string.cleaning_tools_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
            )
            Text(
                stringResource(R.string.cleaning_tools_subtitle_v050),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            ToolGridRow(
                left = ToolSpec(
                    R.string.junk_cleaner_title,
                    R.string.junk_cleaner_subtitle_v050,
                    Icons.Outlined.DeleteSweep,
                    Color(0xFFEA6A22),
                ) { onScan(ScanFocus.JUNK) },
                right = ToolSpec(
                    R.string.duplicates_tool_title,
                    R.string.duplicates_tool_subtitle_v050,
                    Icons.Outlined.ContentCopy,
                    Color(0xFF7047E8),
                ) { onScan(ScanFocus.DUPLICATES) },
            )
        }
        item {
            ToolGridRow(
                left = ToolSpec(
                    R.string.large_files_tool_title,
                    R.string.large_files_tool_subtitle_v050,
                    Icons.Outlined.VideoFile,
                    Color(0xFFEA3E5C),
                ) { onScan(ScanFocus.LARGE_FILES) },
                right = ToolSpec(
                    R.string.media_cleaner_title,
                    R.string.media_cleaner_subtitle,
                    Icons.Outlined.PhotoLibrary,
                    Color(0xFF0B8DD8),
                ) { onScan(ScanFocus.MEDIA) },
            )
        }
        item {
            WideToolCard(
                title = stringResource(R.string.whatsapp_cleaner_title),
                subtitle = stringResource(R.string.whatsapp_cleaner_subtitle_v050),
                icon = Icons.Outlined.Chat,
                accent = WhatsAppGreen,
                onClick = onOpenWhatsApp,
            )
        }
        item {
            WideToolCard(
                title = stringResource(R.string.deep_cache_title),
                subtitle = if (state.appCache.totalCacheBytes > 0L) {
                    stringResource(R.string.deep_cache_measured, ByteFormatter.format(state.appCache.totalCacheBytes))
                } else {
                    stringResource(R.string.deep_cache_subtitle)
                },
                icon = Icons.Outlined.CleaningServices,
                accent = Color(0xFF1253D8),
                onClick = onOpenCache,
            )
        }
        item {
            WideToolCard(
                title = stringResource(R.string.downloads_apk_title),
                subtitle = stringResource(R.string.downloads_apk_subtitle),
                icon = Icons.Outlined.Download,
                accent = Color(0xFF805313),
                onClick = { onScan(ScanFocus.DOWNLOADS) },
            )
        }
        item {
            WideToolCard(
                title = stringResource(R.string.ram_optimizer_title_v050),
                subtitle = stringResource(R.string.ram_optimizer_subtitle_v050, ByteFormatter.format(state.memory.availableBytes)),
                icon = Icons.Outlined.Memory,
                accent = Color(0xFF08A875),
                onClick = onOptimizeMemory,
            )
        }
    }
}

@Composable
private fun SmartCleanerHero(
    state: CleanerUiState,
    onScan: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Card(shape = RoundedCornerShape(30.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF07143B), Color(0xFF0A4ED7), Color(0xFF13B78D)),
                    ),
                )
                .padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = Color.White.copy(alpha = .14f), shape = CircleShape) {
                        Icon(
                            Icons.Outlined.Security,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.padding(11.dp).size(28.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.app_name), color = Color.White.copy(alpha = .82f), fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(R.string.hero_performance_v050),
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = null, tint = Color.White)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StorageRing(state.storage)
                    Column(
                        modifier = Modifier.weight(1f).padding(start = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Text(
                            stringResource(R.string.storage_free, ByteFormatter.format(state.storage.availableBytes)),
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            stringResource(R.string.deep_cache_home_metric, ByteFormatter.format(state.appCache.totalCacheBytes)),
                            color = Color.White.copy(alpha = .82f),
                        )
                        Text(
                            if (state.hasAllFilesAccess) stringResource(R.string.full_storage_access_ready) else stringResource(R.string.full_storage_access_needed),
                            color = if (state.hasAllFilesAccess) Color(0xFF8FF7C2) else Color(0xFFFFD37A),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Button(
                    onClick = onScan,
                    enabled = !state.scanning,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (state.scanning) stringResource(R.string.smart_clean_scanning) else stringResource(R.string.smart_clean_action_v050),
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
    }
}

@Composable
private fun StorageRing(storage: StorageSnapshot) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(112.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 11.dp.toPx()
            drawArc(
                Color.White.copy(alpha = .18f), -90f, 360f, false,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            drawArc(
                Lime400, -90f, 360f * storage.usedFraction.coerceIn(0f, 1f), false,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${(storage.usedFraction * 100).toInt()}%", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
            Text(stringResource(R.string.home_storage_label), color = Color.White.copy(alpha = .75f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun AllFilesAccessCard(onRequest: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2414)),
        shape = RoundedCornerShape(22.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Security, contentDescription = null, tint = Color(0xFF9A6500))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.all_files_access_title), fontWeight = FontWeight.Black)
                Text(
                    stringResource(R.string.all_files_access_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onRequest) { Text(stringResource(R.string.grant_access)) }
        }
    }
}

private data class ToolSpec(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val icon: ImageVector,
    val accent: Color,
    val action: () -> Unit,
)

@Composable
private fun ToolGridRow(left: ToolSpec, right: ToolSpec) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SmallToolCard(left, Modifier.weight(1f))
        SmallToolCard(right, Modifier.weight(1f))
    }
}

@Composable
private fun SmallToolCard(spec: ToolSpec, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(170.dp),
        onClick = spec.action,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = spec.accent.copy(alpha = .10f)),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(15.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(color = spec.accent, shape = RoundedCornerShape(16.dp)) {
                Icon(spec.icon, contentDescription = null, tint = Color.White, modifier = Modifier.padding(10.dp).size(28.dp))
            }
            Column {
                Text(stringResource(spec.titleRes), fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(spec.subtitleRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun WideToolCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .border(1.dp, accent.copy(alpha = .12f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(color = accent, shape = RoundedCornerShape(16.dp)) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.padding(11.dp).size(27.dp))
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Black)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = accent)
    }
}

@Composable
private fun CleanScreen(
    state: CleanerUiState,
    onRequestAllFilesAccess: () -> Unit,
    onScan: () -> Unit,
    onToggleItem: (String) -> Unit,
    onToggleCategory: (CleanCategory) -> Unit,
    onClean: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            onClean = onClean,
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
