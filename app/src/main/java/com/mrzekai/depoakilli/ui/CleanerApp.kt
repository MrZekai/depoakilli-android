@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mrzekai.depoakilli.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Language
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import com.mrzekai.depoakilli.ads.MediumRectangleAd
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.model.CleanCategory
import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.model.MemorySnapshot
import com.mrzekai.depoakilli.model.ScanSummary
import com.mrzekai.depoakilli.model.StorageSnapshot
import com.mrzekai.depoakilli.ui.theme.Amber400
import com.mrzekai.depoakilli.ui.theme.Forest800
import com.mrzekai.depoakilli.ui.theme.Lime400
import com.mrzekai.depoakilli.ui.theme.Mint100

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
    onRequestMediaAccess: () -> Unit,
    onPrepareCleanup: () -> Unit,
    onClearAppCache: () -> Unit,
    onOptimizeMemory: () -> Unit,
    onOpenAppStorageDetails: () -> Unit,
    onOpenManageApps: () -> Unit,
    onOpenStorageSettings: () -> Unit,
    onOpenLanguageSettings: () -> Unit,
    onShowPrivacyOptions: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val hasMediaAccess = hasFullMediaAccess || hasLimitedMediaAccess
    val adsCanBeShown = canRequestAds && hasMediaAccess && !state.scanning
    val showAnchoredBanner = adsCanBeShown && selectedTabIndex != AppTab.HOME.ordinal

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name), fontWeight = FontWeight.Black)
                        Text(
                            stringResource(R.string.app_tagline),
                            style = MaterialTheme.typography.labelSmall,
                            color = Lime400,
                            letterSpacing = 1.2.sp,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding(),
            ) {
                BannerAd(canRequestAds = showAnchoredBanner)
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    AppTab.entries.forEachIndexed { index, tab ->
                        val tabTitle = stringResource(tab.titleRes)
                        NavigationBarItem(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            icon = { Icon(tab.icon, contentDescription = tabTitle) },
                            label = { Text(tabTitle) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        when (AppTab.entries[selectedTabIndex]) {
            AppTab.HOME -> HomeScreen(
                state = state,
                hasAccess = hasMediaAccess,
                showMediumRectangleAd = adsCanBeShown,
                onRequestAccess = onRequestMediaAccess,
                onScan = {
                    selectedTabIndex = AppTab.CLEAN.ordinal
                    viewModel.scan(limitedAccess = !hasFullMediaAccess)
                },
                onClearAppCache = onClearAppCache,
                onOptimizeMemory = onOptimizeMemory,
                modifier = Modifier.padding(padding),
            )

            AppTab.CLEAN -> CleanScreen(
                state = state,
                hasFullAccess = hasFullMediaAccess,
                hasLimitedAccess = hasLimitedMediaAccess,
                onRequestAccess = onRequestMediaAccess,
                onScan = { viewModel.scan(limitedAccess = !hasFullMediaAccess) },
                onToggleItem = viewModel::toggleItem,
                onToggleCategory = viewModel::toggleCategory,
                onClean = onPrepareCleanup,
                modifier = Modifier.padding(padding),
            )

            AppTab.TOOLS -> ToolsScreen(
                state = state,
                privacyOptionsRequired = privacyOptionsRequired,
                onClearAppCache = onClearAppCache,
                onOptimizeMemory = onOptimizeMemory,
                onOpenAppStorageDetails = onOpenAppStorageDetails,
                onOpenManageApps = onOpenManageApps,
                onOpenStorageSettings = onOpenStorageSettings,
                onOpenLanguageSettings = onOpenLanguageSettings,
                onShowPrivacyOptions = onShowPrivacyOptions,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun HomeScreen(
    state: CleanerUiState,
    hasAccess: Boolean,
    showMediumRectangleAd: Boolean,
    onRequestAccess: () -> Unit,
    onScan: () -> Unit,
    onClearAppCache: () -> Unit,
    onOptimizeMemory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { StorageHero(state.storage) }
        if (!hasAccess) {
            item { PermissionCard(onRequestAccess) }
        }
        item {
            Button(
                onClick = onScan,
                modifier = Modifier.fillMaxWidth().height(62.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(stringResource(R.string.scan_primary), fontWeight = FontWeight.Black)
                    Text(
                        stringResource(R.string.scan_primary_subtitle),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        item {
            MemoryCard(
                memory = state.memory,
                optimizing = state.optimizingMemory,
                onClick = onOptimizeMemory,
            )
        }
        item {
            Text(
                stringResource(R.string.cleaning_center),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickActionCard(
                    stringResource(R.string.photos),
                    stringResource(R.string.photos_subtitle),
                    Icons.Outlined.PhotoLibrary,
                    Modifier.weight(1f),
                    onScan,
                )
                QuickActionCard(
                    stringResource(R.string.large_videos),
                    stringResource(R.string.large_videos_subtitle),
                    Icons.Outlined.VideoFile,
                    Modifier.weight(1f),
                    onScan,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickActionCard(
                    stringResource(R.string.apk_packages),
                    stringResource(R.string.apk_packages_subtitle),
                    Icons.Outlined.Inventory2,
                    Modifier.weight(1f),
                    onScan,
                )
                QuickActionCard(
                    stringResource(R.string.downloads),
                    stringResource(R.string.downloads_subtitle),
                    Icons.Outlined.Folder,
                    Modifier.weight(1f),
                    onScan,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickActionCard(
                    stringResource(R.string.app_cache_quick),
                    stringResource(
                        R.string.app_cache_quick_subtitle,
                        ByteFormatter.format(state.ownCacheBytes),
                    ),
                    Icons.Outlined.CleaningServices,
                    Modifier.weight(1f),
                    onClearAppCache,
                )
                QuickActionCard(
                    stringResource(R.string.memory_tools_quick),
                    stringResource(R.string.memory_tools_quick_subtitle),
                    Icons.Outlined.Memory,
                    Modifier.weight(1f),
                    onOptimizeMemory,
                )
            }
        }
        if (state.lastScanCompleted) {
            item { LastScanCard(state.summary, onScan) }
        }
        if (showMediumRectangleAd) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.sponsored),
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    MediumRectangleAd(canRequestAds = true)
                }
            }
        }
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
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Memory,
                    contentDescription = null,
                    tint = if (memory.lowMemory) Amber400 else Lime400,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(
                        if (optimizing) R.string.memory_optimizing else R.string.memory_status,
                    ),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (optimizing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("%${(memory.usedFraction * 100).toInt()}", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { memory.usedFraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = if (memory.lowMemory) Amber400 else Lime400,
                trackColor = Forest800,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(
                    R.string.memory_available,
                    ByteFormatter.format(memory.availableBytes),
                    ByteFormatter.format(memory.appUsedBytes),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Surface(color = Forest800, shape = CircleShape) {
                Icon(icon, contentDescription = null, tint = Lime400, modifier = Modifier.padding(10.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
            onToggleItem = onToggleItem,
            onToggleCategory = onToggleCategory,
            onScan = onScan,
            onClean = onClean,
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
                onClick = if (hasAccess) onScan else onRequestAccess,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (hasAccess) R.string.start_scan else R.string.grant_and_scan,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ScanResults(
    summary: ScanSummary,
    onToggleItem: (String) -> Unit,
    onToggleCategory: (CleanCategory) -> Unit,
    onScan: () -> Unit,
    onClean: () -> Unit,
    modifier: Modifier,
) {
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
                            stringResource(R.string.cleanable_space),
                            style = MaterialTheme.typography.labelMedium,
                            color = Mint100,
                        )
                        Text(
                            ByteFormatter.format(summary.selectedBytes),
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
                    }
                }
            }
            if (summary.items.isEmpty()) {
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
}

@Composable
private fun ToolsScreen(
    state: CleanerUiState,
    privacyOptionsRequired: Boolean,
    onClearAppCache: () -> Unit,
    onOptimizeMemory: () -> Unit,
    onOpenAppStorageDetails: () -> Unit,
    onOpenManageApps: () -> Unit,
    onOpenStorageSettings: () -> Unit,
    onOpenLanguageSettings: () -> Unit,
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
                title = stringResource(R.string.clear_app_cache),
                subtitle = stringResource(
                    R.string.clear_app_cache_subtitle,
                    ByteFormatter.format(state.ownCacheBytes),
                ),
                icon = Icons.Outlined.CleaningServices,
                onClick = onClearAppCache,
            )
        }
        item {
            ToolRow(
                title = stringResource(R.string.app_storage_details),
                subtitle = stringResource(R.string.app_storage_details_subtitle),
                icon = Icons.Outlined.Info,
                onClick = onOpenAppStorageDetails,
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
        item {
            ToolRow(
                title = stringResource(R.string.memory_and_apps),
                subtitle = stringResource(
                    R.string.memory_and_apps_subtitle,
                    ByteFormatter.format(state.memory.availableBytes),
                    ByteFormatter.format(state.memory.totalBytes),
                ),
                icon = Icons.Outlined.Memory,
                onClick = onOpenManageApps,
            )
        }
        item {
            ToolRow(
                title = stringResource(R.string.language_settings),
                subtitle = stringResource(R.string.language_settings_subtitle),
                icon = Icons.Outlined.Language,
                onClick = onOpenLanguageSettings,
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
                Text(
                    stringResource(R.string.honest_memory_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
