package com.mrzekai.depoakilli.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import android.util.Size
import android.view.LayoutInflater
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.model.CleanCategory
import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.model.ScanFocus
import com.mrzekai.depoakilli.model.ScanSummary
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val ToolBackground = Color(0xFF030A1D)
private val ToolCard = Color(0xFF081A38)
private val ToolCardAlt = Color(0xFF0C2348)
private val ToolTextSecondary = Color(0xFFA9B9D4)
private val ToolGreen = Color(0xFF24E58A)
private val ToolCyan = Color(0xFF20C8FF)
private val ToolPurple = Color(0xFF9A63FF)
private val ToolAmber = Color(0xFFFFB21A)
private val ToolRed = Color(0xFFFF5D68)

private enum class ToolSortMode {
    LARGEST,
    NEWEST,
    SELECTED,
}

private data class ToolVisualConfig(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val icon: ImageVector,
    val accent: Color,
    val accent2: Color,
)

private data class ToolSection(
    val key: String,
    val title: String,
    val subtitle: String,
    val accent: Color,
    val items: List<CleanableItem>,
)

private object ToolThumbnailCache {
    private val cache = object : LruCache<String, ImageBitmap>(72) {}

    fun get(key: String): ImageBitmap? = synchronized(cache) { cache.get(key) }

    fun put(key: String, bitmap: ImageBitmap) {
        synchronized(cache) { cache.put(key, bitmap) }
    }

    fun clear() {
        synchronized(cache) { cache.evictAll() }
    }
}

internal fun releasePremiumToolThumbnailMemory() {
    ToolThumbnailCache.clear()
}

@Composable
internal fun PremiumCleanerToolScreen(
    state: CleanerUiState,
    onRequestAllFilesAccess: () -> Unit,
    onScan: () -> Unit,
    onToggleItem: (String) -> Unit,
    onSetItemsSelected: (Set<String>, Boolean) -> Unit,
    onClean: (Set<String>?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = ToolBackground) {
        when {
            !state.hasAllFilesAccess -> PremiumToolAccessRequired(
                focus = state.scanFocus,
                onRequest = onRequestAllFilesAccess,
            )
            state.scanning -> PremiumToolScanning(state)
            !state.lastScanCompleted -> PremiumToolEmpty(state.scanFocus, onScan)
            else -> PremiumToolResults(
                focus = state.scanFocus,
                summary = state.summary,
                cleanupInProgress = state.cleanupInProgress,
                onScan = onScan,
                onToggleItem = onToggleItem,
                onSetItemsSelected = onSetItemsSelected,
                onClean = onClean,
            )
        }
    }
}

@Composable
private fun PremiumToolAccessRequired(
    focus: ScanFocus,
    onRequest: () -> Unit,
) {
    val config = toolConfig(focus)
    Box(Modifier.fillMaxSize().padding(22.dp), contentAlignment = Alignment.Center) {
        Card(
            colors = CardDefaults.cardColors(containerColor = ToolCard),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, config.accent.copy(alpha = .35f)),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(color = config.accent.copy(alpha = .16f), shape = CircleShape) {
                    Icon(config.icon, contentDescription = null, tint = config.accent, modifier = Modifier.padding(20.dp).size(46.dp))
                }
                Text(stringResource(R.string.all_files_access_title), color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(stringResource(R.string.all_files_access_clean_screen), color = ToolTextSecondary)
                Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.grant_access))
                }
            }
        }
    }
}

@Composable
private fun PremiumToolScanning(state: CleanerUiState) {
    val config = toolConfig(state.scanFocus)
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(
            shape = RoundedCornerShape(30.dp),
            colors = CardDefaults.cardColors(containerColor = ToolCard),
            border = BorderStroke(1.dp, config.accent.copy(alpha = .34f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(listOf(config.accent.copy(alpha = .62f), Color(0xFF0A2A65), config.accent2.copy(alpha = .55f))))
                    .padding(22.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(100.dp),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = .15f),
                            strokeWidth = 8.dp,
                        )
                        Surface(color = Color(0xCC06132D), shape = CircleShape) {
                            Icon(config.icon, contentDescription = null, tint = Color.White, modifier = Modifier.padding(18.dp).size(38.dp))
                        }
                    }
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(stringResource(config.titleRes), color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        Text(stringResource(R.string.premium_tool_scanning), color = Color.White.copy(alpha = .88f), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PremiumMetricCard(
                value = state.scanProgressFiles.toString(),
                label = stringResource(R.string.smart_scan_files_label),
                icon = Icons.Outlined.InsertDriveFile,
                accent = config.accent,
                modifier = Modifier.weight(1f),
            )
            PremiumMetricCard(
                value = state.scanProgressDirectories.toString(),
                label = stringResource(R.string.smart_scan_folders_label),
                icon = Icons.Outlined.Folder,
                accent = config.accent2,
                modifier = Modifier.weight(1f),
            )
        }

        Surface(color = ToolCard, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = .08f))) {
            Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Security, contentDescription = null, tint = ToolGreen)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(stringResource(R.string.premium_tool_on_device_title), color = Color.White, fontWeight = FontWeight.Black)
                    Text(stringResource(R.string.scan_private), color = ToolTextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun PremiumMetricCard(
    value: String,
    label: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.height(92.dp),
        colors = CardDefaults.cardColors(containerColor = ToolCard),
        shape = RoundedCornerShape(21.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .07f)),
    ) {
        Row(Modifier.fillMaxSize().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = accent.copy(alpha = .15f), shape = RoundedCornerShape(14.dp)) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.padding(10.dp).size(24.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(value, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text(label, color = ToolTextSecondary, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun PremiumToolEmpty(focus: ScanFocus, onScan: () -> Unit) {
    val config = toolConfig(focus)
    Box(Modifier.fillMaxSize().padding(22.dp), contentAlignment = Alignment.Center) {
        Card(
            colors = CardDefaults.cardColors(containerColor = ToolCard),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, config.accent.copy(alpha = .35f)),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(color = config.accent.copy(alpha = .14f), shape = CircleShape) {
                    Icon(config.icon, contentDescription = null, tint = config.accent, modifier = Modifier.padding(22.dp).size(48.dp))
                }
                Text(stringResource(config.titleRes), color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(stringResource(config.subtitleRes), color = ToolTextSecondary)
                Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.start_scan)) }
            }
        }
    }
}

@Composable
private fun PremiumToolResults(
    focus: ScanFocus,
    summary: ScanSummary,
    cleanupInProgress: Boolean,
    onScan: () -> Unit,
    onToggleItem: (String) -> Unit,
    onSetItemsSelected: (Set<String>, Boolean) -> Unit,
    onClean: (Set<String>?) -> Unit,
) {
    val config = toolConfig(focus)
    var sortName by rememberSaveable(focus.name) { mutableStateOf(ToolSortMode.LARGEST.name) }
    var detailKey by rememberSaveable(focus.name) { mutableStateOf<String?>(null) }
    var previewId by rememberSaveable(focus.name) { mutableStateOf<String?>(null) }
    var showCleanupConfirmation by rememberSaveable(focus.name) { mutableStateOf(false) }
    val sortMode = ToolSortMode.valueOf(sortName)

    val sections = premiumSections(focus, summary)
    val activeDetail = sections.firstOrNull { it.key == detailKey }
    val previewItem = summary.items.firstOrNull { it.id == previewId }

    if (previewItem != null) {
        PremiumToolPreviewDialog(
            item = previewItem,
            accent = config.accent,
            onToggleSelection = { onToggleItem(previewItem.id) },
            onDismiss = { previewId = null },
        )
    }

    if (showCleanupConfirmation) {
        PremiumCleanupConfirmationDialog(
            focus = focus,
            selectedItems = summary.selectedItems,
            accent = config.accent,
            cleanupInProgress = cleanupInProgress,
            onConfirm = {
                showCleanupConfirmation = false
                onClean(null)
            },
            onDismiss = { showCleanupConfirmation = false },
        )
    }

    if (activeDetail != null) {
        PremiumToolDetailPage(
            section = activeDetail,
            focus = focus,
            sortMode = sortMode,
            cleanupInProgress = cleanupInProgress,
            selectedCount = summary.selectedItems.size,
            selectedBytes = summary.selectedBytes,
            onSortMode = { sortName = it.name },
            onBack = { detailKey = null },
            onPreview = { previewId = it.id },
            onToggleItem = onToggleItem,
            onToggleAll = { setSectionSelection(activeDetail.items, onSetItemsSelected) },
            onClean = { showCleanupConfirmation = true },
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { PremiumToolHero(focus, summary, config) }
            item {
                PremiumSortRow(sortMode = sortMode, onChange = { sortName = it.name })
            }

            if (summary.items.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ToolCard),
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = ToolGreen, modifier = Modifier.size(46.dp))
                            Spacer(Modifier.height(10.dp))
                            Text(stringResource(R.string.premium_tool_nothing_found), color = Color.White, fontWeight = FontWeight.Black)
                            Text(stringResource(R.string.premium_tool_nothing_found_detail), color = ToolTextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                items(sections, key = ToolSection::key) { section ->
                    PremiumToolSectionCard(
                        section = section.copy(items = sortItems(section.items, sortMode)),
                        focus = focus,
                        onViewAll = { detailKey = section.key },
                        onPreview = { previewId = it.id },
                        onToggleItem = onToggleItem,
                        onToggleAll = { setSectionSelection(section.items, onSetItemsSelected) },
                    )
                }
            }

            if (summary.scanLimitReached) {
                item {
                    Text(stringResource(R.string.scan_limit_note_v050), color = ToolAmber, style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                TextButton(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.scan_again), color = config.accent)
                }
            }
        }

        PremiumToolBottomAction(
            selectedCount = summary.selectedItems.size,
            selectedBytes = summary.selectedBytes,
            accent = config.accent,
            accent2 = config.accent2,
            cleanupInProgress = cleanupInProgress,
            onClean = { showCleanupConfirmation = true },
        )
    }
}

@Composable
private fun PremiumCleanupConfirmationDialog(
    focus: ScanFocus,
    selectedItems: List<CleanableItem>,
    accent: Color,
    cleanupInProgress: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedBytes = selectedItems.sumOf { it.sizeBytes }
    val config = toolConfig(focus)

    Dialog(
        onDismissRequest = {
            if (!cleanupInProgress) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xB8000615))
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = ToolCard),
                border = BorderStroke(1.dp, accent.copy(alpha = .45f)),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = accent.copy(alpha = .16f), shape = CircleShape) {
                            Icon(
                                config.icon,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.padding(13.dp).size(30.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.premium_cleanup_confirm_title),
                                color = Color.White,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Black,
                            )
                            Text(
                                stringResource(config.titleRes),
                                color = accent,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    Text(
                        stringResource(
                            R.string.premium_cleanup_confirm_body,
                            selectedItems.size,
                            ByteFormatter.format(selectedBytes),
                        ),
                        color = ToolTextSecondary,
                    )

                    Surface(
                        color = ToolRed.copy(alpha = .10f),
                        shape = RoundedCornerShape(15.dp),
                        border = BorderStroke(1.dp, ToolRed.copy(alpha = .25f)),
                    ) {
                        Text(
                            stringResource(R.string.premium_cleanup_irreversible),
                            color = Color(0xFFFFA7AE),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(12.dp),
                        )
                    }

                    Text(
                        stringResource(R.string.premium_cleanup_ad_notice),
                        color = Color(0xFF9EB7D0),
                        fontSize = 10.sp,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            enabled = !cleanupInProgress,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.cancel))
                        }
                        Button(
                            onClick = onConfirm,
                            enabled = selectedItems.isNotEmpty() && !cleanupInProgress,
                            modifier = Modifier.weight(1.4f),
                        ) {
                            Text(
                                stringResource(R.string.premium_cleanup_confirm_action),
                                fontWeight = FontWeight.Black,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumToolHero(
    focus: ScanFocus,
    summary: ScanSummary,
    config: ToolVisualConfig,
) {
    val foundBytes = summary.totalSuggestedBytes
    val selectedBytes = summary.selectedBytes
    Card(
        colors = CardDefaults.cardColors(containerColor = ToolCard),
        shape = RoundedCornerShape(30.dp),
        border = BorderStroke(1.dp, config.accent.copy(alpha = .45f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF06132C), config.accent.copy(alpha = .44f), config.accent2.copy(alpha = .36f))))
                .padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = Color(0xCC051027), shape = CircleShape, border = BorderStroke(1.dp, config.accent.copy(alpha = .65f))) {
                        Icon(config.icon, contentDescription = null, tint = config.accent, modifier = Modifier.padding(18.dp).size(42.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(config.titleRes), color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(config.subtitleRes), color = Color.White.copy(alpha = .82f), style = MaterialTheme.typography.bodySmall)
                    }
                }

                Text(ByteFormatter.format(foundBytes), color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Black)
                Text(stringResource(R.string.premium_tool_found_size), color = config.accent, fontWeight = FontWeight.Black)

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    PremiumHeroStat(summary.items.size.toString(), stringResource(R.string.premium_tool_candidates), Modifier.weight(1f))
                    PremiumHeroStat(ByteFormatter.format(selectedBytes), stringResource(R.string.premium_tool_selected), Modifier.weight(1f))
                    PremiumHeroStat(summary.scannedFileCount.toString(), stringResource(R.string.premium_tool_scanned), Modifier.weight(1f))
                }

                if (focus == ScanFocus.DUPLICATES) {
                    Surface(color = Color(0x33000000), shape = RoundedCornerShape(15.dp)) {
                        Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Security, contentDescription = null, tint = ToolGreen, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.premium_duplicates_original_safe), color = Color.White.copy(alpha = .90f), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (focus == ScanFocus.APKS) {
                    Surface(color = Color(0x33000000), shape = RoundedCornerShape(15.dp)) {
                        Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Android, contentDescription = null, tint = ToolGreen, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.premium_apk_installer_note), color = Color.White.copy(alpha = .90f), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumHeroStat(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = Color(0x55020A19), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 8.dp)) {
            Text(value, color = Color.White, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, color = Color.White.copy(alpha = .68f), fontSize = 9.sp, maxLines = 1)
        }
    }
}

@Composable
private fun PremiumSortRow(sortMode: ToolSortMode, onChange: (ToolSortMode) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(ToolSortMode.entries) { mode ->
            val selected = sortMode == mode
            Surface(
                modifier = Modifier.clickable { onChange(mode) },
                color = if (selected) Color(0xFF123C68) else ToolCard,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, if (selected) ToolCyan.copy(alpha = .65f) else Color.White.copy(alpha = .08f)),
            ) {
                Text(
                    stringResource(
                        when (mode) {
                            ToolSortMode.LARGEST -> R.string.premium_sort_largest
                            ToolSortMode.NEWEST -> R.string.premium_sort_newest
                            ToolSortMode.SELECTED -> R.string.premium_sort_selected
                        },
                    ),
                    color = if (selected) Color.White else ToolTextSecondary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun PremiumToolSectionCard(
    section: ToolSection,
    focus: ScanFocus,
    onViewAll: () -> Unit,
    onPreview: (CleanableItem) -> Unit,
    onToggleItem: (String) -> Unit,
    onToggleAll: () -> Unit,
) {
    val allSelected = section.items.isNotEmpty() && section.items.all(CleanableItem::selected)
    Card(
        colors = CardDefaults.cardColors(containerColor = ToolCard),
        shape = RoundedCornerShape(25.dp),
        border = BorderStroke(1.dp, section.accent.copy(alpha = .22f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = section.accent.copy(alpha = .15f), shape = RoundedCornerShape(13.dp)) {
                    Icon(sectionIcon(focus, section), contentDescription = null, tint = section.accent, modifier = Modifier.padding(9.dp).size(23.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(section.title, color = Color.White, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(section.subtitle, color = ToolTextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = onViewAll) {
                    Text(stringResource(R.string.whatsapp_view_all), color = section.accent, fontWeight = FontWeight.Black)
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = section.accent, modifier = Modifier.size(18.dp))
                }
                Checkbox(
                    checked = allSelected,
                    onCheckedChange = { onToggleAll() },
                    colors = CheckboxDefaults.colors(checkedColor = section.accent, uncheckedColor = Color(0xFFB7C5DB)),
                )
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(section.items.take(4), key = CleanableItem::id) { item ->
                    PremiumToolItemCard(
                        item = item,
                        accent = section.accent,
                        onPreview = { onPreview(item) },
                        onToggle = { onToggleItem(item.id) },
                    )
                }
                if (section.items.size > 4) {
                    item {
                        Card(
                            onClick = onViewAll,
                            modifier = Modifier.width(102.dp).height(190.dp),
                            colors = CardDefaults.cardColors(containerColor = ToolCardAlt),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, section.accent.copy(alpha = .25f)),
                        ) {
                            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text("+${section.items.size - 4}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                                Spacer(Modifier.height(5.dp))
                                Text(stringResource(R.string.whatsapp_view_all), color = section.accent, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumToolItemCard(
    item: CleanableItem,
    accent: Color,
    onPreview: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val thumbnail by produceState<ImageBitmap?>(initialValue = null, key1 = item.uri, key2 = item.modifiedAtMillis) {
        value = loadToolThumbnail(context, item)
    }
    Card(
        onClick = onPreview,
        modifier = modifier.width(150.dp).height(190.dp),
        colors = CardDefaults.cardColors(containerColor = ToolCardAlt),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, if (item.selected) accent.copy(alpha = .70f) else Color.White.copy(alpha = .08f)),
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxWidth().height(112.dp).background(Color(0xFF05132A)),
                contentAlignment = Alignment.Center,
            ) {
                if (thumbnail != null) {
                    Image(requireNotNull(thumbnail), contentDescription = item.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(fileIcon(item), contentDescription = null, tint = accent, modifier = Modifier.size(43.dp))
                }
                if (item.mimeType.startsWith("video/")) {
                    Surface(color = Color(0xB6020713), shape = CircleShape) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.padding(7.dp).size(24.dp))
                    }
                }
                Checkbox(
                    checked = item.selected,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.align(Alignment.TopEnd).padding(2.dp),
                    colors = CheckboxDefaults.colors(checkedColor = accent, uncheckedColor = Color.White, checkmarkColor = Color.White),
                )
                Surface(modifier = Modifier.align(Alignment.BottomStart).padding(6.dp), color = Color(0xCC020713), shape = RoundedCornerShape(9.dp)) {
                    Text(ByteFormatter.format(item.sizeBytes), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.name, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp)
                Text(localDate(item.modifiedAtMillis), color = ToolTextSecondary, fontSize = 9.sp, maxLines = 1)
                if (item.protectedDuplicateName != null) {
                    Text(stringResource(R.string.premium_duplicates_kept_short), color = ToolGreen, fontSize = 9.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun PremiumToolDetailPage(
    section: ToolSection,
    focus: ScanFocus,
    sortMode: ToolSortMode,
    cleanupInProgress: Boolean,
    selectedCount: Int,
    selectedBytes: Long,
    onSortMode: (ToolSortMode) -> Unit,
    onBack: () -> Unit,
    onPreview: (CleanableItem) -> Unit,
    onToggleItem: (String) -> Unit,
    onToggleAll: () -> Unit,
    onClean: () -> Unit,
) {
    val sorted = sortItems(section.items, sortMode)
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back), tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(section.title, color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(section.subtitle, color = ToolTextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onToggleAll) {
                Text(
                    stringResource(if (section.items.all(CleanableItem::selected)) R.string.smart_detail_unselect_all else R.string.smart_detail_select_all),
                    color = section.accent,
                    fontWeight = FontWeight.Black,
                )
            }
        }
        Box(Modifier.padding(horizontal = 14.dp)) {
            PremiumSortRow(sortMode = sortMode, onChange = onSortMode)
        }
        Spacer(Modifier.height(10.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            gridItems(sorted, key = CleanableItem::id) { item ->
                PremiumToolItemCard(
                    item = item,
                    accent = section.accent,
                    onPreview = { onPreview(item) },
                    onToggle = { onToggleItem(item.id) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        PremiumToolBottomAction(
            selectedCount = selectedCount,
            selectedBytes = selectedBytes,
            accent = section.accent,
            accent2 = toolConfig(focus).accent2,
            cleanupInProgress = cleanupInProgress,
            onClean = onClean,
        )
    }
}

@Composable
private fun PremiumToolBottomAction(
    selectedCount: Int,
    selectedBytes: Long,
    accent: Color,
    accent2: Color,
    cleanupInProgress: Boolean,
    onClean: () -> Unit,
) {
    Surface(color = ToolBackground, shadowElevation = 12.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.width(122.dp).height(66.dp),
                color = ToolCard,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, accent.copy(alpha = .35f)),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.Center) {
                    Text(stringResource(R.string.premium_tool_selected), color = ToolTextSecondary, fontSize = 10.sp)
                    Text(ByteFormatter.format(selectedBytes), color = accent, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Text(stringResource(R.string.premium_tool_selected_count, selectedCount), color = ToolTextSecondary, fontSize = 9.sp)
                }
            }
            Button(
                onClick = onClean,
                enabled = selectedCount > 0 && !cleanupInProgress,
                modifier = Modifier.weight(1f).height(66.dp),
                shape = RoundedCornerShape(21.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(accent, accent2))),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (cleanupInProgress) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.CleaningServices, contentDescription = null, tint = Color.White)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.whatsapp_review_and_clean), color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp)
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumToolPreviewDialog(
    item: CleanableItem,
    accent: Color,
    onToggleSelection: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = ToolBackground) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back), tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.whatsapp_preview_title), color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(item.name, color = Color.White, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 18.sp)
                    }
                }

                Box(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFF020713)),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        item.mimeType.startsWith("image/") -> ToolImagePreview(item, accent)
                        item.mimeType.startsWith("video/") -> ToolVideoPreview(item, accent)
                        item.mimeType.startsWith("audio/") -> ToolAudioPreview(item, accent)
                        else -> ToolGenericPreview(item, accent)
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    colors = CardDefaults.cardColors(containerColor = ToolCard),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = accent.copy(alpha = .14f), shape = RoundedCornerShape(14.dp)) {
                                Icon(fileIcon(item), contentDescription = null, tint = accent, modifier = Modifier.padding(10.dp).size(25.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, color = Color.White, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(compactPath(item.relativePath), color = ToolTextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(ByteFormatter.format(item.sizeBytes), color = accent, fontWeight = FontWeight.Black)
                                Text(localDate(item.modifiedAtMillis), color = ToolTextSecondary, fontSize = 10.sp)
                            }
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleSelection),
                            color = if (item.selected) Color(0xFF0C4936) else Color(0xFF10284C),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, if (item.selected) ToolGreen.copy(alpha = .70f) else Color(0xFF5D7498)),
                        ) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = item.selected,
                                    onCheckedChange = { onToggleSelection() },
                                    colors = CheckboxDefaults.colors(checkedColor = ToolGreen, uncheckedColor = Color(0xFFB9C7DE)),
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(if (item.selected) R.string.whatsapp_included else R.string.whatsapp_not_included), color = Color.White, fontWeight = FontWeight.Black)
                                    Text(stringResource(if (item.selected) R.string.whatsapp_remove_from_cleanup else R.string.whatsapp_add_to_cleanup), color = Color(0xFFC1CEE2), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        if (item.protectedDuplicateName != null) {
                            Surface(color = ToolGreen.copy(alpha = .09f), shape = RoundedCornerShape(14.dp)) {
                                Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Security, contentDescription = null, tint = ToolGreen, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.duplicate_original_kept, item.protectedDuplicateName), color = Color.White.copy(alpha = .88f), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolImagePreview(item: CleanableItem, accent: Color) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = item.uri) {
        value = loadToolDisplayBitmap(context, item)
    }
    if (bitmap == null) CircularProgressIndicator(color = accent) else Image(requireNotNull(bitmap), contentDescription = item.name, modifier = Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
}

@Composable
private fun ToolVideoPreview(item: CleanableItem, accent: Color) {
    val context = LocalContext.current
    val player = remember(item.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(item.uri))
            prepare()
        }
    }
    var failed by remember(item.uri) { mutableStateOf(false) }
    var noVideoTrack by remember(item.uri) { mutableStateOf(false) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                if (tracks.groups.isEmpty()) return
                noVideoTrack = !tracks.groups.any { group -> group.type == C.TRACK_TYPE_VIDEO && group.isSelected }
                if (noVideoTrack) player.pause()
            }
            override fun onPlayerError(error: PlaybackException) { failed = true }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                (LayoutInflater.from(viewContext).inflate(R.layout.smart_video_player, null, false) as PlayerView).apply {
                    this.player = player
                    useController = true
                }
            },
            update = { view -> if (view.player !== player) view.player = player },
        )
        if (failed || noVideoTrack) {
            Surface(color = Color(0xDD06132F), shape = RoundedCornerShape(18.dp)) {
                Text(
                    stringResource(if (noVideoTrack) R.string.smart_video_no_picture else R.string.smart_video_preview_error),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(18.dp),
                )
            }
        }
    }
}

@Composable
private fun ToolAudioPreview(item: CleanableItem, accent: Color) {
    val context = LocalContext.current
    val player = remember(item.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(item.uri))
            prepare()
        }
    }
    var playing by remember(item.uri) { mutableStateOf(false) }
    var failed by remember(item.uri) { mutableStateOf(false) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlayerError(error: PlaybackException) { failed = true }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(color = accent.copy(alpha = .15f), shape = CircleShape) {
            Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = accent, modifier = Modifier.padding(24.dp).size(56.dp))
        }
        Text(item.name, color = Color.White, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (failed) {
            Text(stringResource(R.string.whatsapp_audio_preview_error), color = ToolAmber)
        } else {
            Button(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
                Icon(if (playing) Icons.Outlined.CheckCircle else Icons.Outlined.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(7.dp))
                Text(stringResource(if (playing) R.string.smart_video_pause else R.string.smart_video_play))
            }
        }
    }
}

@Composable
private fun ToolGenericPreview(item: CleanableItem, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(color = accent.copy(alpha = .15f), shape = RoundedCornerShape(26.dp)) {
            Icon(fileIcon(item), contentDescription = null, tint = accent, modifier = Modifier.padding(26.dp).size(64.dp))
        }
        Text(stringResource(R.string.whatsapp_file_preview), color = Color.White, fontWeight = FontWeight.Black)
        Text(item.name, color = ToolTextSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text(ByteFormatter.format(item.sizeBytes), color = accent, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun premiumSections(focus: ScanFocus, summary: ScanSummary): List<ToolSection> {
    val items = summary.items
    fun section(key: String, title: String, list: List<CleanableItem>, accent: Color): ToolSection = ToolSection(
        key = key,
        title = title,
        subtitle = "${list.size} • ${ByteFormatter.format(list.sumOf(CleanableItem::sizeBytes))}",
        accent = accent,
        items = list,
    )

    return when (focus) {
        ScanFocus.DUPLICATES -> {
            val grouped = items
                .groupBy { it.protectedDuplicateName ?: stringResource(R.string.premium_duplicate_unknown_original) }
                .entries
                .sortedByDescending { (_, list) -> list.sumOf(CleanableItem::sizeBytes) }
            val visibleGroups = grouped.take(5).mapIndexed { index, (kept, list) ->
                section(
                    key = "duplicate-$index-$kept",
                    title = stringResource(R.string.premium_duplicate_group_title, kept),
                    list = list,
                    accent = if (index % 2 == 0) ToolPurple else ToolCyan,
                )
            }
            val remaining = grouped.drop(5).flatMap { it.value }
            if (remaining.isEmpty()) visibleGroups else visibleGroups + section(
                key = "duplicate-more",
                title = stringResource(R.string.premium_duplicate_more_groups),
                list = remaining,
                accent = ToolGreen,
            )
        }
        ScanFocus.LARGE_FILES -> {
            val videos = items.filter { it.mimeType.startsWith("video/") }
            val images = items.filter { it.mimeType.startsWith("image/") }
            val docs = items.filter { isDocumentOrArchive(it) }
            val other = items - videos.toSet() - images.toSet() - docs.toSet()
            listOfNotNull(
                videos.takeIf { it.isNotEmpty() }?.let { section("large-videos", stringResource(R.string.storage_type_videos), it, ToolPurple) },
                images.takeIf { it.isNotEmpty() }?.let { section("large-images", stringResource(R.string.storage_type_images), it, ToolCyan) },
                docs.takeIf { it.isNotEmpty() }?.let { section("large-docs", stringResource(R.string.premium_documents_archives), it, ToolAmber) },
                other.takeIf { it.isNotEmpty() }?.let { section("large-other", stringResource(R.string.storage_type_other), it, ToolGreen) },
            )
        }
        ScanFocus.APKS -> {
            val now = System.currentTimeMillis()
            val old = items.filter { ageDays(it.modifiedAtMillis, now) >= 30 }
            val recent = items - old.toSet()
            listOfNotNull(
                old.takeIf { it.isNotEmpty() }?.let { section("apk-old", stringResource(R.string.premium_apk_old), it, ToolGreen) },
                recent.takeIf { it.isNotEmpty() }?.let { section("apk-recent", stringResource(R.string.premium_apk_recent), it, ToolCyan) },
            )
        }
        ScanFocus.MEDIA -> {
            val duplicateMedia = items.filter { it.assessment.category == CleanCategory.DUPLICATE }
            val remainingMedia = items.filterNot { it.assessment.category == CleanCategory.DUPLICATE }
            val images = remainingMedia.filter { it.mimeType.startsWith("image/") }
            val videos = remainingMedia.filter { it.mimeType.startsWith("video/") }
            val other = remainingMedia.filterNot { it in images || it in videos }
            listOfNotNull(
                images.takeIf { it.isNotEmpty() }?.let { section("media-images", stringResource(R.string.storage_type_images), it, ToolCyan) },
                videos.takeIf { it.isNotEmpty() }?.let { section("media-videos", stringResource(R.string.storage_type_videos), it, ToolPurple) },
                duplicateMedia.takeIf { it.isNotEmpty() }?.let { section("media-dupes", stringResource(R.string.category_duplicates), it, ToolGreen) },
                other.takeIf { it.isNotEmpty() }?.let { section("media-other", stringResource(R.string.premium_media_review), it, ToolAmber) },
            )
        }
        ScanFocus.JUNK -> listOfNotNull(
            items.takeIf { it.isNotEmpty() }?.let { section("junk-safe", stringResource(R.string.category_junk), it, ToolCyan) },
        )
        ScanFocus.DOWNLOADS -> summary.byCategory.entries.map { (category, list) ->
            section("downloads-${category.name}", stringResource(category.titleRes), list, categoryAccent(category))
        }
        ScanFocus.DEEP -> summary.byCategory.entries.map { (category, list) ->
            section("deep-${category.name}", stringResource(category.titleRes), list, categoryAccent(category))
        }
        else -> summary.byCategory.entries.map { (category, list) ->
            section("generic-${category.name}", stringResource(category.titleRes), list, categoryAccent(category))
        }
    }
}

private fun sortItems(items: List<CleanableItem>, sortMode: ToolSortMode): List<CleanableItem> = when (sortMode) {
    ToolSortMode.LARGEST -> items.sortedByDescending(CleanableItem::sizeBytes)
    ToolSortMode.NEWEST -> items.sortedByDescending(CleanableItem::modifiedAtMillis)
    ToolSortMode.SELECTED -> items.sortedWith(compareByDescending<CleanableItem> { it.selected }.thenByDescending { it.sizeBytes })
}

private fun setSectionSelection(
    items: List<CleanableItem>,
    onSetItemsSelected: (Set<String>, Boolean) -> Unit,
) {
    if (items.isEmpty()) return
    val targetSelected = !items.all(CleanableItem::selected)
    onSetItemsSelected(items.asSequence().map(CleanableItem::id).toSet(), targetSelected)
}

private fun toolConfig(focus: ScanFocus): ToolVisualConfig = when (focus) {
    ScanFocus.DUPLICATES -> ToolVisualConfig(R.string.scan_focus_duplicates, R.string.premium_duplicates_subtitle, Icons.Outlined.ContentCopy, ToolPurple, ToolCyan)
    ScanFocus.LARGE_FILES -> ToolVisualConfig(R.string.scan_focus_large, R.string.premium_large_subtitle, Icons.Outlined.VideoFile, ToolAmber, ToolCyan)
    ScanFocus.APKS -> ToolVisualConfig(R.string.scan_focus_apks, R.string.premium_apk_subtitle, Icons.Outlined.Android, ToolGreen, ToolCyan)
    ScanFocus.MEDIA -> ToolVisualConfig(R.string.scan_focus_media, R.string.premium_media_subtitle, Icons.Outlined.PhotoLibrary, ToolCyan, ToolPurple)
    ScanFocus.JUNK -> ToolVisualConfig(R.string.scan_focus_junk, R.string.premium_junk_subtitle, Icons.Outlined.DeleteSweep, ToolCyan, ToolGreen)
    ScanFocus.DOWNLOADS -> ToolVisualConfig(R.string.scan_focus_downloads, R.string.premium_downloads_subtitle, Icons.Outlined.Download, ToolAmber, ToolCyan)
    ScanFocus.DEEP -> ToolVisualConfig(R.string.scan_focus_deep, R.string.premium_deep_subtitle, Icons.Outlined.AutoAwesome, ToolGreen, ToolPurple)
    else -> ToolVisualConfig(R.string.scan_focus_smart, R.string.safe_ai_cleaning_description_v050, Icons.Outlined.CleaningServices, ToolCyan, ToolGreen)
}

private fun sectionIcon(focus: ScanFocus, section: ToolSection): ImageVector = when {
    focus == ScanFocus.DUPLICATES -> Icons.Outlined.ContentCopy
    focus == ScanFocus.APKS -> Icons.Outlined.Android
    section.items.any { it.mimeType.startsWith("video/") } -> Icons.Outlined.VideoFile
    section.items.any { it.mimeType.startsWith("image/") } -> Icons.Outlined.PhotoLibrary
    section.items.any { it.mimeType.startsWith("audio/") } -> Icons.Outlined.MusicNote
    section.items.any { isDocumentOrArchive(it) } -> Icons.Outlined.Description
    else -> Icons.Outlined.Folder
}

private fun categoryAccent(category: CleanCategory): Color = when (category) {
    CleanCategory.JUNK -> ToolCyan
    CleanCategory.DUPLICATE -> ToolPurple
    CleanCategory.SCREENSHOT -> Color(0xFFDA57FF)
    CleanCategory.LARGE_FILE -> ToolAmber
    CleanCategory.OLD_DOWNLOAD -> Color(0xFFFF8746)
    CleanCategory.APK_PACKAGE -> ToolGreen
    CleanCategory.APP_CACHE -> Color(0xFF18D0CF)
    CleanCategory.WHATSAPP_MEDIA -> Color(0xFF22C96C)
}

private fun fileIcon(item: CleanableItem): ImageVector = when {
    item.mimeType.startsWith("image/") -> Icons.Outlined.PhotoLibrary
    item.mimeType.startsWith("video/") -> Icons.Outlined.VideoFile
    item.mimeType.startsWith("audio/") -> Icons.Outlined.MusicNote
    item.name.lowercase().endsWith(".apk") -> Icons.Outlined.Android
    isDocumentOrArchive(item) -> Icons.Outlined.Description
    else -> Icons.Outlined.InsertDriveFile
}

private fun isDocumentOrArchive(item: CleanableItem): Boolean {
    val ext = item.name.substringAfterLast('.', "").lowercase()
    return item.mimeType.startsWith("text/") ||
        item.mimeType.contains("pdf") ||
        ext in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "zip", "rar", "7z", "tar", "gz")
}

private fun ageDays(modifiedAtMillis: Long, nowMillis: Long = System.currentTimeMillis()): Long {
    if (modifiedAtMillis <= 0L || modifiedAtMillis > nowMillis) return 0L
    return TimeUnit.MILLISECONDS.toDays(nowMillis - modifiedAtMillis)
}

private fun localDate(millis: Long): String = if (millis <= 0L) "—" else DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))

private fun compactPath(path: String): String {
    val parts = path.replace('\\', '/').split('/').filter(String::isNotBlank)
    return when {
        parts.isEmpty() -> "—"
        parts.size <= 3 -> parts.joinToString(" › ")
        else -> parts.takeLast(3).joinToString(" › ")
    }
}

private suspend fun loadToolThumbnail(context: Context, item: CleanableItem): ImageBitmap? = withContext(Dispatchers.IO) {
    ToolThumbnailCache.get(item.uri)?.let { return@withContext it }
    val bitmap = runCatching {
        val uri = Uri.parse(item.uri)
        val loaded = if (uri.scheme == "content") {
            context.contentResolver.loadThumbnail(uri, Size(420, 320), null)
        } else {
            val path = uri.path ?: return@runCatching null
            val file = File(path)
            if (!file.isFile) return@runCatching null
            when {
                item.mimeType.startsWith("video/") -> android.media.ThumbnailUtils.createVideoThumbnail(file, Size(420, 320), null)
                item.mimeType.startsWith("image/") -> decodeToolBitmap(file, 520)
                else -> null
            }
        }
        loaded?.asImageBitmap()
    }.getOrNull()
    if (bitmap != null) ToolThumbnailCache.put(item.uri, bitmap)
    bitmap
}

private suspend fun loadToolDisplayBitmap(context: Context, item: CleanableItem): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val uri = Uri.parse(item.uri)
        if (uri.scheme == "content") {
            context.contentResolver.loadThumbnail(uri, Size(1600, 1600), null).asImageBitmap()
        } else {
            val path = uri.path ?: return@runCatching null
            val file = File(path)
            if (!file.isFile) return@runCatching null
            decodeToolBitmap(file, 1600)?.asImageBitmap()
        }
    }.getOrNull()
}

private fun decodeToolBitmap(file: File, maxSide: Int): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    val largest = maxOf(bounds.outWidth, bounds.outHeight)
    while (largest / sample > maxSide) sample *= 2
    return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
}
