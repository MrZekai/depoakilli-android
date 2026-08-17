@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mrzekai.depoakilli.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Movie
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.mrzekai.depoakilli.model.AppCacheEntry
import com.mrzekai.depoakilli.model.AppCacheSnapshot
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.model.CleanCategory
import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.model.MemorySnapshot
import com.mrzekai.depoakilli.model.ScanSummary
import com.mrzekai.depoakilli.model.ScanFocus
import com.mrzekai.depoakilli.model.StorageSnapshot
import com.mrzekai.depoakilli.ui.theme.Amber400
import com.mrzekai.depoakilli.ui.theme.ElectricBlue
import com.mrzekai.depoakilli.ui.theme.Forest800
import com.mrzekai.depoakilli.ui.theme.Lime400
import com.mrzekai.depoakilli.ui.theme.Mint100
import com.mrzekai.depoakilli.ui.theme.Purple500
import com.mrzekai.depoakilli.ui.theme.Red400
import com.mrzekai.depoakilli.ui.theme.Rose500
import com.mrzekai.depoakilli.ui.theme.Teal500
import com.mrzekai.depoakilli.ui.theme.WhatsAppGreen

private enum class AppTab(@StringRes val titleRes: Int, val icon: ImageVector) {
    HOME(R.string.tab_home, Icons.Outlined.Home),
    CLEAN(R.string.tab_clean, Icons.Outlined.CleaningServices),
    TOOLS(R.string.tab_tools, Icons.Outlined.Settings),
}

@Composable
fun CleanerApp(
    viewModel: CleanerViewModel,
    hasFullMediaAccess: Boolean,
    hasLimitedMediaAccess: Boolean,
    canRequestAds: Boolean,
    privacyOptionsRequired: Boolean,
    onRequestMediaAccess: (ScanFocus) -> Unit,
    onRequestWhatsAppAccess: () -> Unit,
    onPrepareCleanup: () -> Unit,
    onClearAppCache: () -> Unit,
    onRefreshAppCaches: () -> Unit,
    onOptimizeMemory: () -> Unit,
    onOpenPackageStorageDetails: (String) -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenStorageSettings: () -> Unit,
    onShowPrivacyOptions: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val hasMediaAccess = hasFullMediaAccess || hasLimitedMediaAccess
    val adsCanBeShown = canRequestAds && !state.scanning
    val showAnchoredBanner = adsCanBeShown

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (AppTab.entries[selectedTabIndex] != AppTab.HOME) {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(AppTab.entries[selectedTabIndex].titleRes),
                            fontWeight = FontWeight.Black,
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                )
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding(),
            ) {
                BannerAd(canRequestAds = showAnchoredBanner)
                NavigationBar(containerColor = Color.White) {
                    AppTab.entries.forEachIndexed { index, tab ->
                        val tabTitle = stringResource(tab.titleRes)
                        NavigationBarItem(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            icon = { Icon(tab.icon, contentDescription = tabTitle) },
                            label = { Text(tabTitle) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ElectricBlue,
                                selectedTextColor = ElectricBlue,
                                indicatorColor = Color(0xFFE2ECFF),
                                unselectedIconColor = Color(0xFF566176),
                                unselectedTextColor = Color(0xFF566176),
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        when (AppTab.entries[selectedTabIndex]) {
            AppTab.HOME -> HomeScreen(
                state = state,
                hasMediaAccess = hasMediaAccess,
                onRequestMediaAccess = { focus ->
                    selectedTabIndex = AppTab.CLEAN.ordinal
                    onRequestMediaAccess(focus)
                },
                onRequestWhatsAppAccess = {
                    selectedTabIndex = AppTab.CLEAN.ordinal
                    onRequestWhatsAppAccess()
                },
                onScan = { focus ->
                    selectedTabIndex = AppTab.CLEAN.ordinal
                    viewModel.scan(
                        limitedAccess = hasLimitedMediaAccess,
                        focus = focus,
                    )
                },
                onOptimizeMemory = onOptimizeMemory,
                onOpenCacheManager = { selectedTabIndex = AppTab.TOOLS.ordinal },
                onOpenTools = { selectedTabIndex = AppTab.TOOLS.ordinal },
                modifier = Modifier.padding(padding),
            )

            AppTab.CLEAN -> CleanScreen(
                state = state,
                hasFullAccess = hasFullMediaAccess,
                hasLimitedAccess = hasLimitedMediaAccess,
                onRequestAccess = { onRequestMediaAccess(state.scanFocus) },
                onScan = {
                    viewModel.scan(
                        limitedAccess = hasLimitedMediaAccess,
                        focus = state.scanFocus,
                    )
                },
                onToggleItem = viewModel::toggleItem,
                onToggleCategory = viewModel::toggleCategory,
                onClean = onPrepareCleanup,
                onOpenCacheManager = { selectedTabIndex = AppTab.TOOLS.ordinal },
                modifier = Modifier.padding(padding),
            )

            AppTab.TOOLS -> ToolsScreen(
                state = state,
                privacyOptionsRequired = privacyOptionsRequired,
                onClearAppCache = onClearAppCache,
                onRefreshAppCaches = onRefreshAppCaches,
                onOptimizeMemory = onOptimizeMemory,
                onOpenPackageStorageDetails = onOpenPackageStorageDetails,
                onOpenUsageAccessSettings = onOpenUsageAccessSettings,
                onOpenStorageSettings = onOpenStorageSettings,
                onShowPrivacyOptions = onShowPrivacyOptions,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun HomeScreen(
    state: CleanerUiState,
    hasMediaAccess: Boolean,
    onRequestMediaAccess: (ScanFocus) -> Unit,
    onRequestWhatsAppAccess: () -> Unit,
    onScan: (ScanFocus) -> Unit,
    onOptimizeMemory: () -> Unit,
    onOpenCacheManager: () -> Unit,
    onOpenTools: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ModernHomeHero(
                storage = state.storage,
                memory = state.memory,
                scanning = state.scanning,
                onScan = { onScan(ScanFocus.SMART) },
                onOpenTools = onOpenTools,
            )
        }
        item {
            Box(Modifier.padding(horizontal = 14.dp)) {
                HomeToolMasonry(
                    state = state,
                    onJunk = { onScan(ScanFocus.JUNK) },
                    onRam = onOptimizeMemory,
                    onDuplicates = {
                        if (hasMediaAccess) {
                            onScan(ScanFocus.DUPLICATES)
                        } else {
                            onRequestMediaAccess(ScanFocus.DUPLICATES)
                        }
                    },
                )
            }
        }
        item {
            Box(Modifier.padding(horizontal = 14.dp)) {
                HomeToolRow(
                    title = stringResource(R.string.whatsapp_cleaner_title),
                    subtitle = stringResource(
                        if (state.hasWhatsAppAccess) {
                            R.string.whatsapp_cleaner_connected
                        } else {
                            R.string.whatsapp_home_subtitle
                        },
                    ),
                    icon = Icons.Outlined.Chat,
                    accent = WhatsAppGreen,
                    background = listOf(Color(0xFFF0FFF6), Color(0xFFE7FBF0)),
                    onClick = {
                        if (state.hasWhatsAppAccess) {
                            onScan(ScanFocus.WHATSAPP)
                        } else {
                            onRequestWhatsAppAccess()
                        }
                    },
                )
            }
        }
        item {
            Box(Modifier.padding(horizontal = 14.dp)) {
                HomeToolRow(
                    title = stringResource(R.string.large_files_tool_title),
                    subtitle = stringResource(R.string.large_files_home_subtitle),
                    icon = Icons.Outlined.Folder,
                    accent = Amber400,
                    background = listOf(Color(0xFFFFFBEE), Color(0xFFFFF4D9)),
                    onClick = {
                        if (hasMediaAccess) {
                            onScan(ScanFocus.LARGE_FILES)
                        } else {
                            onRequestMediaAccess(ScanFocus.LARGE_FILES)
                        }
                    },
                )
            }
        }
        item {
            Box(Modifier.padding(horizontal = 14.dp)) {
                HomeToolRow(
                    title = stringResource(R.string.cache_tool_title),
                    subtitle = if (state.appCache.accessGranted) {
                        stringResource(
                            R.string.cache_home_measured,
                            ByteFormatter.format(state.appCache.totalCacheBytes),
                        )
                    } else {
                        stringResource(R.string.cache_home_subtitle)
                    },
                    icon = Icons.Outlined.CleaningServices,
                    accent = ElectricBlue,
                    background = listOf(Color(0xFFF2F7FF), Color(0xFFE8F1FF)),
                    onClick = onOpenCacheManager,
                )
            }
        }
        if (state.lastScanCompleted) {
            item {
                Box(Modifier.padding(horizontal = 14.dp)) {
                    LastScanCard(state.summary) { onScan(state.scanFocus) }
                }
            }
        }
    }
}

@Composable
private fun ModernHomeHero(
    storage: StorageSnapshot,
    memory: MemorySnapshot,
    scanning: Boolean,
    onScan: () -> Unit,
    onOpenTools: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 34.dp, bottomEnd = 34.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF090C48),
                        Color(0xFF0758D8),
                        Color(0xFF12CFC4),
                    ),
                    start = Offset.Zero,
                    end = Offset(1200f, 1200f),
                ),
            )
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFF54F0E4),
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.app_name),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.weight(1f))
                Surface(
                    modifier = Modifier
                        .size(42.dp)
                        .clickable(onClick = onOpenTools),
                    shape = CircleShape,
                    color = Color.White.copy(alpha = .12f),
                ) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.tab_tools),
                        tint = Color.White,
                        modifier = Modifier.padding(9.dp),
                    )
                }
            }
            DeviceSummaryBar(storage = storage, memory = memory)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(
                            if (scanning) R.string.smart_clean_scanning_title else R.string.smart_clean_title,
                        ),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 34.sp,
                        lineHeight = 36.sp,
                    )
                    Box(
                        Modifier
                            .width(58.dp)
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF38E7D5)),
                    )
                    Text(
                        stringResource(R.string.hero_performance),
                        color = Color.White.copy(alpha = .82f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                HeroShieldIllustration(scanning = scanning)
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .clickable(enabled = !scanning, onClick = onScan),
                shape = RoundedCornerShape(34.dp),
                color = Color.White,
                shadowElevation = 10.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(
                            if (scanning) R.string.smart_clean_scanning else R.string.smart_scan_home_action,
                        ),
                        modifier = Modifier.weight(1f),
                        color = Color(0xFF0B1648),
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                    )
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF13C7BD),
                        modifier = Modifier.size(46.dp),
                    ) {
                        if (scanning) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 3.dp,
                                modifier = Modifier.padding(11.dp),
                            )
                        } else {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowForward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceSummaryBar(
    storage: StorageSnapshot,
    memory: MemorySnapshot,
) {
    Surface(
        color = Color(0xFF07145C).copy(alpha = .64f),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            HeroMetric(
                icon = Icons.Outlined.Storage,
                label = stringResource(R.string.home_storage_label),
                value = stringResource(
                    R.string.home_storage_percent,
                    (storage.usedFraction * 100).toInt(),
                ),
                progress = storage.usedFraction,
                accent = Color(0xFF22D7C4),
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .width(1.dp)
                    .height(52.dp)
                    .background(Color.White.copy(alpha = .28f)),
            )
            HeroMetric(
                icon = Icons.Outlined.Memory,
                label = stringResource(R.string.home_ram_label),
                value = ByteFormatter.format(memory.availableBytes),
                progress = if (memory.totalBytes == 0L) 0f else {
                    memory.availableBytes.toFloat() / memory.totalBytes.toFloat()
                },
                accent = Color(0xFF2CA8FF),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HeroMetric(
    icon: ImageVector,
    label: String,
    value: String,
    progress: Float,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(14.dp), color = Color.White) {
            Icon(
                icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.padding(8.dp).size(24.dp),
            )
        }
        Spacer(Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, color = accent, fontWeight = FontWeight.Black, fontSize = 17.sp)
                Spacer(Modifier.width(5.dp))
                Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = .16f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0.04f, 1f))
                        .height(7.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
            }
        }
    }
}

@Composable
private fun HeroShieldIllustration(scanning: Boolean) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(142.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(color = Color(0xFF23E1D3).copy(alpha = .18f), radius = size.minDimension * .46f)
            drawArc(
                color = Color.White.copy(alpha = .65f),
                startAngle = 205f,
                sweepAngle = 245f,
                useCenter = false,
                topLeft = Offset(size.width * .06f, size.height * .20f),
                size = Size(size.width * .88f, size.height * .60f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        Icon(
            Icons.Outlined.Security,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(112.dp),
        )
        if (scanning) {
            CircularProgressIndicator(
                color = Color(0xFF49F1E4),
                strokeWidth = 5.dp,
                modifier = Modifier.size(58.dp),
            )
        } else {
            Surface(shape = CircleShape, color = Color(0xFF115EDC), modifier = Modifier.size(58.dp)) {
                Icon(
                    Icons.Outlined.CleaningServices,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun HomeToolMasonry(
    state: CleanerUiState,
    onJunk: () -> Unit,
    onRam: () -> Unit,
    onDuplicates: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(276.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HomeFeatureCard(
            title = stringResource(R.string.junk_cleaner_title),
            subtitle = stringResource(R.string.junk_home_subtitle),
            badge = stringResource(R.string.reclaim_space),
            icon = Icons.Outlined.DeleteSweep,
            colors = listOf(Color(0xFF0757E8), Color(0xFF0E87F3)),
            onClick = onJunk,
            modifier = Modifier.weight(1f).fillMaxSize(),
            large = true,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HomeFeatureCard(
                title = stringResource(R.string.ram_booster_title),
                subtitle = if (state.optimizingMemory) {
                    stringResource(R.string.memory_optimizing)
                } else {
                    stringResource(
                        R.string.ram_available_home,
                        ByteFormatter.format(state.memory.availableBytes),
                    )
                },
                icon = Icons.Outlined.Bolt,
                colors = listOf(Color(0xFF08B978), Color(0xFF22D999)),
                enabled = !state.optimizingMemory,
                onClick = onRam,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            HomeFeatureCard(
                title = stringResource(R.string.duplicates_tool_title),
                subtitle = stringResource(R.string.duplicates_home_subtitle),
                icon = Icons.Outlined.ContentCopy,
                colors = listOf(Color(0xFF7047E8), Color(0xFF9A6CF4)),
                onClick = onDuplicates,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}

@Composable
private fun HomeFeatureCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    colors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    large: Boolean = false,
    badge: String? = null,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(colors))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    title,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = .86f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = .96f),
                    modifier = Modifier.size(if (large) 104.dp else 60.dp),
                )
            }
            if (badge != null) {
                Surface(shape = CircleShape, color = Color(0xFF092B77).copy(alpha = .72f)) {
                    Text(
                        badge,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeToolRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    background: List<Color>,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.horizontalGradient(background))
            .border(1.dp, accent.copy(alpha = .12f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = RoundedCornerShape(17.dp), color = accent) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.padding(12.dp).size(28.dp),
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                title,
                color = Color(0xFF121D35),
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                color = Color(0xFF5F6B80),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            icon,
            contentDescription = null,
            tint = accent.copy(alpha = .26f),
            modifier = Modifier.size(48.dp),
        )
        Icon(
            Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = null,
            tint = Color(0xFF526079),
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
private fun StorageHero(storage: StorageSnapshot) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(28.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(132.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 13.dp.toPx()
                    drawArc(
                        color = Forest800,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = Lime400,
                        startAngle = -90f,
                        sweepAngle = 360f * storage.usedFraction.coerceIn(0f, 1f),
                        useCenter = false,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "%${(storage.usedFraction * 100).toInt()}",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        stringResource(R.string.storage_full),
                        style = MaterialTheme.typography.labelSmall,
                        color = Lime400,
                    )
                }
            }
            Spacer(Modifier.width(22.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.phone_storage),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.storage_used, ByteFormatter.format(storage.usedBytes)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.storage_free, ByteFormatter.format(storage.availableBytes)),
                    color = Lime400,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.storage_total, ByteFormatter.format(storage.totalBytes)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(onRequestAccess: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(22.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Security, contentDescription = null, tint = Lime400)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.media_access_required), fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.media_access_explanation),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onRequestAccess) {
                Text(stringResource(R.string.grant_access))
            }
        }
    }
}

@Composable
private fun AppCacheOverviewCard(
    cache: AppCacheSnapshot,
    scanning: Boolean,
    onClick: () -> Unit,
) {
    val subtitle = when {
        !cache.supported -> stringResource(R.string.cache_unsupported)
        scanning -> stringResource(R.string.cache_scanning)
        !cache.accessGranted -> stringResource(R.string.cache_access_explanation)
        cache.totalCacheBytes == 0L -> stringResource(R.string.cache_empty)
        else -> stringResource(
            R.string.cache_summary,
            ByteFormatter.format(cache.totalCacheBytes),
            cache.entries.size,
        )
    }
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(22.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape) {
                Icon(
                    Icons.Outlined.CleaningServices,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(10.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.cache_manager_title),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (scanning) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
            }
        }
    }
}

@Composable
private fun MemoryCard(
    memory: MemorySnapshot,
    optimizing: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        enabled = !optimizing,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(color = Forest800, shape = CircleShape) {
                Icon(
                    Icons.Outlined.Memory,
                    contentDescription = null,
                    tint = if (memory.lowMemory) Amber400 else Lime400,
                    modifier = Modifier.padding(10.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(
                        if (optimizing) R.string.memory_optimizing else R.string.memory_status,
                    ),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(
                        R.string.memory_compact_subtitle,
                        ByteFormatter.format(memory.availableBytes),
                        ByteFormatter.format(memory.appUsedBytes),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (optimizing) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Text("%${(memory.usedFraction * 100).toInt()}", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LastScanCard(summary: ScanSummary, onScan: () -> Unit) {
    Card(
        onClick = onScan,
        colors = CardDefaults.cardColors(containerColor = Forest800),
        shape = RoundedCornerShape(22.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.DeleteSweep, contentDescription = null, tint = Lime400)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.last_scan), fontWeight = FontWeight.Bold)
                Text(
                    stringResource(
                        R.string.last_scan_summary,
                        summary.items.size,
                        ByteFormatter.format(summary.totalSuggestedBytes),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
        }
    }
}

@Composable
private fun CleanScreen(
    state: CleanerUiState,
    hasFullAccess: Boolean,
    hasLimitedAccess: Boolean,
    onRequestAccess: () -> Unit,
    onScan: () -> Unit,
    onToggleItem: (String) -> Unit,
    onToggleCategory: (CleanCategory) -> Unit,
    onClean: () -> Unit,
    onOpenCacheManager: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.scanning -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Lime400, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(20.dp))
                Text(stringResource(R.string.scan_in_progress), fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.scan_private),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        !state.lastScanCompleted -> EmptyScanState(
            hasAccess = hasFullAccess || hasLimitedAccess,
            onRequestAccess = onRequestAccess,
            onScan = onScan,
            modifier = modifier,
        )

        else -> ScanResults(
            summary = state.summary,
            appCache = state.appCache,
            includeAppCache = state.scanFocus == ScanFocus.SMART,
            scanFocus = state.scanFocus,
            onToggleItem = onToggleItem,
            onToggleCategory = onToggleCategory,
            onScan = onScan,
            onClean = onClean,
            onOpenCacheManager = onOpenCacheManager,
            modifier = modifier,
        )
    }
}

@Composable
private fun EmptyScanState(
    hasAccess: Boolean,
    onRequestAccess: () -> Unit,
    onScan: () -> Unit,
    modifier: Modifier,
) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(color = Forest800, shape = CircleShape) {
                Icon(
                    Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = Lime400,
                    modifier = Modifier.padding(24.dp).size(56.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.safe_ai_cleaning),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.safe_ai_cleaning_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onScan,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.start_scan))
            }
            if (!hasAccess) {
                TextButton(onClick = onRequestAccess) {
                    Text(stringResource(R.string.enable_media_scan))
                }
            }
        }
    }
}

@Composable
private fun ScanResults(
    summary: ScanSummary,
    appCache: AppCacheSnapshot,
    includeAppCache: Boolean,
    scanFocus: ScanFocus,
    onToggleItem: (String) -> Unit,
    onToggleCategory: (CleanCategory) -> Unit,
    onScan: () -> Unit,
    onClean: () -> Unit,
    onOpenCacheManager: () -> Unit,
    modifier: Modifier,
) {
    val reviewedAppCacheBytes = if (includeAppCache) appCache.totalCacheBytes else 0L
    Column(modifier.fillMaxSize()) {
        if (summary.limitedAccess) {
            Surface(color = Amber400.copy(alpha = .14f), modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.limited_access_notice),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    color = Amber400,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Forest800),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        Text(
                            stringResource(scanFocusTitleRes(scanFocus)),
                            style = MaterialTheme.typography.labelMedium,
                            color = Mint100,
                        )
                        Text(
                            ByteFormatter.format(
                                summary.selectedBytes + reviewedAppCacheBytes,
                            ),
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Black,
                            color = Lime400,
                        )
                        Text(
                            stringResource(
                                R.string.scan_stats,
                                summary.scannedFileCount,
                                summary.selectedItems.size,
                            ),
                        )
                        Text(
                            stringResource(
                                R.string.scan_space_breakdown,
                                ByteFormatter.format(summary.selectedBytes),
                                ByteFormatter.format(reviewedAppCacheBytes),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (
                includeAppCache &&
                (!appCache.accessGranted || appCache.totalCacheBytes > 0L)
            ) {
                item {
                    CacheScanResultCard(appCache, onOpenCacheManager)
                }
            }
            if (
                summary.items.isEmpty() &&
                (!includeAppCache || (appCache.accessGranted && appCache.totalCacheBytes == 0L))
            ) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(
                            Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                Icons.Outlined.Security,
                                contentDescription = null,
                                tint = Lime400,
                                modifier = Modifier.size(42.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                stringResource(R.string.no_safe_suggestions),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
            summary.byCategory.forEach { (category, categoryItems) ->
                item {
                    CategoryHeader(category, categoryItems, onToggleCategory)
                }
                items(categoryItems, key = CleanableItem::id) { item ->
                    FileResultRow(item, onToggleItem)
                }
            }
            item {
                TextButton(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.scan_again))
                }
            }
        }
        AnimatedVisibility(summary.selectedItems.isNotEmpty()) {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
                Button(
                    onClick = onClean,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.clean_action, ByteFormatter.format(summary.selectedBytes)),
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
    }
}

@StringRes
private fun scanFocusTitleRes(focus: ScanFocus): Int = when (focus) {
    ScanFocus.SMART -> R.string.scan_focus_smart
    ScanFocus.JUNK -> R.string.scan_focus_junk
    ScanFocus.DUPLICATES -> R.string.scan_focus_duplicates
    ScanFocus.LARGE_FILES -> R.string.scan_focus_large
    ScanFocus.WHATSAPP -> R.string.scan_focus_whatsapp
}

@Composable
private fun CacheScanResultCard(
    cache: AppCacheSnapshot,
    onOpenCacheManager: () -> Unit,
) {
    val title = if (cache.accessGranted) {
        stringResource(R.string.cache_detected_title)
    } else {
        stringResource(R.string.cache_access_required)
    }
    val description = when {
        !cache.supported -> stringResource(R.string.cache_unsupported)
        !cache.accessGranted -> stringResource(R.string.cache_scan_not_enabled)
        else -> stringResource(
            R.string.cache_detected_description,
            ByteFormatter.format(cache.totalCacheBytes),
            cache.entries.size,
        )
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CleaningServices, contentDescription = null, tint = Lime400)
                Spacer(Modifier.width(10.dp))
                Text(title, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onOpenCacheManager, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.review_cache))
            }
        }
    }
}

@Composable
private fun CategoryHeader(
    category: CleanCategory,
    items: List<CleanableItem>,
    onToggleCategory: (CleanCategory) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleCategory(category) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(category.titleRes), fontWeight = FontWeight.Bold)
            Text(
                stringResource(
                    R.string.category_summary,
                    items.size,
                    ByteFormatter.format(items.sumOf { it.sizeBytes }),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Checkbox(
            checked = items.all(CleanableItem::selected),
            onCheckedChange = { onToggleCategory(category) },
        )
    }
}

@Composable
private fun FileResultRow(item: CleanableItem, onToggleItem: (String) -> Unit) {
    val reason = stringResource(
        item.assessment.reasonRes,
        *item.assessment.reasonArgs.toTypedArray(),
    )
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleItem(item.id) }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(color = Forest800, shape = RoundedCornerShape(12.dp)) {
                Icon(
                    imageVector = categoryIcon(item.assessment.category),
                    contentDescription = null,
                    tint = Lime400,
                    modifier = Modifier.padding(10.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(
                        R.string.file_confidence,
                        ByteFormatter.format(item.sizeBytes),
                        item.assessment.safetyScore,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.assessment.safetyScore >= 85) Lime400 else Amber400,
                )
            }
            Checkbox(
                checked = item.selected,
                onCheckedChange = { onToggleItem(item.id) },
            )
        }
    }
}

private fun categoryIcon(category: CleanCategory): ImageVector = when (category) {
    CleanCategory.DUPLICATE -> Icons.Outlined.PhotoLibrary
    CleanCategory.SCREENSHOT -> Icons.Outlined.PhotoLibrary
    CleanCategory.LARGE_VIDEO -> Icons.Outlined.VideoFile
    CleanCategory.OLD_DOWNLOAD -> Icons.Outlined.Folder
    CleanCategory.APK_PACKAGE -> Icons.Outlined.Android
    CleanCategory.APP_CACHE -> Icons.Outlined.CleaningServices
    CleanCategory.WHATSAPP_MEDIA -> Icons.Outlined.Chat
}

@Composable
private fun ToolsScreen(
    state: CleanerUiState,
    privacyOptionsRequired: Boolean,
    onClearAppCache: () -> Unit,
    onRefreshAppCaches: () -> Unit,
    onOptimizeMemory: () -> Unit,
    onOpenPackageStorageDetails: (String) -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenStorageSettings: () -> Unit,
    onShowPrivacyOptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                stringResource(R.string.tools_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
            )
            Text(
                stringResource(R.string.tools_subtitle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            CacheManagerPanel(
                cache = state.appCache,
                scanning = state.scanningAppCaches,
                onGrantAccess = onOpenUsageAccessSettings,
                onRefresh = onRefreshAppCaches,
            )
        }
        if (state.appCache.accessGranted && state.appCache.entries.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.cache_other_apps),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.cache_open_settings_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(
                items = state.appCache.entries.take(MAX_DISPLAYED_CACHE_APPS),
                key = AppCacheEntry::packageName,
            ) { entry ->
                AppCacheRow(
                    entry = entry,
                    onClick = { onOpenPackageStorageDetails(entry.packageName) },
                )
            }
        }
        item {
            ToolRow(
                title = stringResource(R.string.cache_own_app),
                subtitle = stringResource(
                    R.string.cache_own_app_subtitle,
                    ByteFormatter.format(state.ownCacheBytes),
                ),
                icon = Icons.Outlined.CleaningServices,
                onClick = onClearAppCache,
            )
        }
        item {
            ToolRow(
                title = if (state.optimizingMemory) {
                    stringResource(R.string.memory_optimizing)
                } else {
                    stringResource(R.string.memory_optimizer)
                },
                subtitle = stringResource(
                    R.string.memory_optimizer_subtitle,
                    ByteFormatter.format(state.memory.availableBytes),
                    ByteFormatter.format(state.memory.appUsedBytes),
                ),
                icon = Icons.Outlined.Memory,
                onClick = onOptimizeMemory,
                enabled = !state.optimizingMemory,
            )
        }
        item {
            ToolRow(
                title = stringResource(R.string.storage_settings),
                subtitle = stringResource(R.string.storage_settings_subtitle),
                icon = Icons.Outlined.Storage,
                onClick = onOpenStorageSettings,
            )
        }
        if (privacyOptionsRequired) {
            item {
                ToolRow(
                    title = stringResource(R.string.ad_privacy_preferences),
                    subtitle = stringResource(R.string.ad_privacy_preferences_subtitle),
                    icon = Icons.Outlined.Security,
                    onClick = onShowPrivacyOptions,
                )
            }
        }
        item {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .25f))
        }
        item {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = Lime400)
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.cache_manual_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.honest_memory_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CacheManagerPanel(
    cache: AppCacheSnapshot,
    scanning: Boolean,
    onGrantAccess: () -> Unit,
    onRefresh: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape) {
                    Icon(
                        Icons.Outlined.CleaningServices,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(10.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.cache_manager_title),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.cache_manager_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when {
                !cache.supported -> Text(
                    stringResource(R.string.cache_unsupported),
                    style = MaterialTheme.typography.bodySmall,
                )

                scanning -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.cache_scanning))
                }

                !cache.accessGranted -> {
                    Text(
                        stringResource(R.string.cache_access_explanation),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(onClick = onGrantAccess, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.cache_grant_action))
                    }
                }

                else -> {
                    Text(
                        if (cache.totalCacheBytes == 0L) {
                            stringResource(R.string.cache_empty)
                        } else {
                            stringResource(
                                R.string.cache_summary,
                                ByteFormatter.format(cache.totalCacheBytes),
                                cache.entries.size,
                            )
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(onClick = onRefresh, modifier = Modifier.align(Alignment.End)) {
                        Text(stringResource(R.string.cache_refresh))
                    }
                }
            }
        }
    }
}

@Composable
private fun AppCacheRow(
    entry: AppCacheEntry,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(color = Forest800, shape = CircleShape) {
                Icon(
                    Icons.Outlined.Android,
                    contentDescription = null,
                    tint = Lime400,
                    modifier = Modifier.padding(9.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.label,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    ByteFormatter.format(entry.cacheBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
        }
    }
}

@Composable
private fun ToolRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(color = Forest800, shape = CircleShape) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Lime400,
                    modifier = Modifier.padding(10.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
        }
    }
}

private const val MAX_DISPLAYED_CACHE_APPS = 12
