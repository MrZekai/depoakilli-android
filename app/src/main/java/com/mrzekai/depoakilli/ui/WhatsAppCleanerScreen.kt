package com.mrzekai.depoakilli.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import android.util.Size
import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import com.mrzekai.depoakilli.model.WhatsAppLibrarySummary
import com.mrzekai.depoakilli.model.WhatsAppMediaCategory
import com.mrzekai.depoakilli.model.WhatsAppMediaItem
import com.mrzekai.depoakilli.ui.theme.ElectricBlue
import com.mrzekai.depoakilli.ui.theme.Purple500
import com.mrzekai.depoakilli.ui.theme.WhatsAppGreen
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val WaBackground = Color(0xFF030A1D)
private val WaCard = Color(0xFF081A38)
private val WaCardSecondary = Color(0xFF0B2146)
private val WaTextSecondary = Color(0xFF9FB1D0)
private val WaCyan = Color(0xFF19DCCB)
private val WaAmber = Color(0xFFFFB21A)

private enum class WhatsAppDirectionFilter {
    ALL,
    INCOMING,
    SENT,
}

private enum class WhatsAppUiGroup {
    IMAGES,
    VIDEOS,
    DOCUMENTS_OTHER,
}

private enum class WhatsAppSortMode {
    LARGEST,
    NEWEST,
}

private object WhatsAppThumbnailCache {
    private val cache = object : LruCache<String, ImageBitmap>(72) {}

    fun get(key: String): ImageBitmap? = synchronized(cache) { cache.get(key) }

    fun put(key: String, bitmap: ImageBitmap) {
        synchronized(cache) { cache.put(key, bitmap) }
    }
}

@Composable
internal fun WhatsAppCleanerDetailScreen(
    state: CleanerUiState,
    onBack: () -> Unit,
    onRequestAccess: () -> Unit,
    onScan: () -> Unit,
    onToggleItem: (String) -> Unit,
    onToggleCategory: (WhatsAppMediaCategory) -> Unit,
    onDeleteSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = WaBackground) {
        when {
            !state.hasWhatsAppAccess -> WhatsAppAccessScreen(
                onBack = onBack,
                onRequestAccess = onRequestAccess,
            )

            state.whatsAppScanning -> WhatsAppScanningScreen(
                progress = state.whatsAppScanProgress,
                onBack = onBack,
            )

            !state.whatsAppLastScanCompleted -> WhatsAppReadyScreen(
                onBack = onBack,
                onScan = onScan,
            )

            else -> WhatsAppResultsScreen(
                summary = state.whatsAppSummary,
                cleanupInProgress = state.cleanupInProgress,
                onBack = onBack,
                onScan = onScan,
                onToggleItem = onToggleItem,
                onToggleCategory = onToggleCategory,
                onDeleteSelected = onDeleteSelected,
            )
        }
    }
}

@Composable
private fun WhatsAppHeader(
    onBack: () -> Unit,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = Color(0xFF07152D),
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .08f)),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = Color.White,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.whatsapp_cleaner_title),
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    color = WhatsAppGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
        }
        Surface(
            color = WhatsAppGreen.copy(alpha = .10f),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, WhatsAppGreen.copy(alpha = .30f)),
        ) {
            Icon(
                Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = WhatsAppGreen,
                modifier = Modifier.padding(10.dp).size(22.dp),
            )
        }
    }
}

@Composable
private fun WhatsAppAccessScreen(
    onBack: () -> Unit,
    onRequestAccess: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        WhatsAppHeader(onBack = onBack)
        Box(Modifier.weight(1f).fillMaxWidth().padding(22.dp), contentAlignment = Alignment.Center) {
            Card(
                colors = CardDefaults.cardColors(containerColor = WaCard),
                shape = RoundedCornerShape(28.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, WhatsAppGreen.copy(alpha = .25f)),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(color = WhatsAppGreen.copy(alpha = .14f), shape = CircleShape) {
                        Icon(
                            Icons.Outlined.Folder,
                            contentDescription = null,
                            tint = WhatsAppGreen,
                            modifier = Modifier.padding(20.dp).size(46.dp),
                        )
                    }
                    Text(
                        stringResource(R.string.whatsapp_access_title),
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        stringResource(R.string.whatsapp_access_description),
                        color = WaTextSecondary,
                    )
                    Button(onClick = onRequestAccess, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.whatsapp_choose_folder))
                    }
                    Text(
                        stringResource(R.string.whatsapp_access_once),
                        style = MaterialTheme.typography.bodySmall,
                        color = WaTextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun WhatsAppReadyScreen(
    onBack: () -> Unit,
    onScan: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        WhatsAppHeader(onBack = onBack)
        Box(Modifier.weight(1f).fillMaxWidth().padding(22.dp), contentAlignment = Alignment.Center) {
            Card(
                colors = CardDefaults.cardColors(containerColor = WaCard),
                shape = RoundedCornerShape(28.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(color = WhatsAppGreen.copy(alpha = .14f), shape = CircleShape) {
                        Icon(
                            Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = WhatsAppGreen,
                            modifier = Modifier.padding(24.dp).size(50.dp),
                        )
                    }
                    Text(
                        stringResource(R.string.whatsapp_ready_title),
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        stringResource(R.string.whatsapp_ready_description),
                        color = WaTextSecondary,
                    )
                    Button(onClick = onScan, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.whatsapp_scan_action), fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun WhatsAppScanningScreen(
    progress: Int,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        WhatsAppHeader(onBack = onBack, subtitle = stringResource(R.string.whatsapp_scanning_short))
        Box(Modifier.weight(1f).fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
            Card(
                colors = CardDefaults.cardColors(containerColor = WaCard),
                shape = RoundedCornerShape(30.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, WaCyan.copy(alpha = .25f)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF092453), Color(0xFF093E43), Color(0xFF07152D)),
                            ),
                        )
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.size(132.dp),
                            color = WhatsAppGreen,
                            trackColor = Color.White.copy(alpha = .10f),
                            strokeWidth = 10.dp,
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = WaCyan)
                            Text(
                                "$progress%",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.whatsapp_scanning_title),
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        stringResource(R.string.whatsapp_scanning_description),
                        color = WaTextSecondary,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ScanPill(Icons.Outlined.PhotoLibrary, R.string.whatsapp_group_images, Modifier.weight(1f))
                        ScanPill(Icons.Outlined.VideoFile, R.string.whatsapp_group_videos, Modifier.weight(1f))
                        ScanPill(Icons.Outlined.Description, R.string.whatsapp_group_documents_more, Modifier.weight(1f))
                    }
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                        color = WhatsAppGreen,
                        trackColor = Color.White.copy(alpha = .10f),
                    )
                    Text(
                        stringResource(R.string.whatsapp_local_processing_note),
                        color = WaCyan,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanPill(icon: ImageVector, textRes: Int, modifier: Modifier) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = .06f),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(icon, contentDescription = null, tint = WaCyan, modifier = Modifier.size(20.dp))
            Text(
                stringResource(textRes),
                color = Color.White,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun WhatsAppResultsScreen(
    summary: WhatsAppLibrarySummary,
    cleanupInProgress: Boolean,
    onBack: () -> Unit,
    onScan: () -> Unit,
    onToggleItem: (String) -> Unit,
    onToggleCategory: (WhatsAppMediaCategory) -> Unit,
    onDeleteSelected: () -> Unit,
) {
    var directionName by rememberSaveable { mutableStateOf(WhatsAppDirectionFilter.ALL.name) }
    var detailGroupName by rememberSaveable { mutableStateOf<String?>(null) }
    var previewItemId by rememberSaveable { mutableStateOf<String?>(null) }
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }

    val direction = runCatching { WhatsAppDirectionFilter.valueOf(directionName) }
        .getOrDefault(WhatsAppDirectionFilter.ALL)
    val detailGroup = detailGroupName?.let { name ->
        runCatching { WhatsAppUiGroup.valueOf(name) }.getOrNull()
    }
    val previewItem = previewItemId?.let { id -> summary.items.firstOrNull { it.id == id } }

    BackHandler(enabled = detailGroup != null) { detailGroupName = null }

    if (showDeleteConfirmation) {
        WhatsAppCleanupConfirmationDialog(
            summary = summary,
            onDismiss = { showDeleteConfirmation = false },
            onConfirm = {
                showDeleteConfirmation = false
                onDeleteSelected()
            },
        )
    }

    if (previewItem != null) {
        WhatsAppPreviewDialog(
            item = previewItem,
            selected = previewItem.selected,
            onToggleSelection = { onToggleItem(previewItem.id) },
            onDismiss = { previewItemId = null },
        )
    }

    if (detailGroup != null) {
        WhatsAppGroupDetailPage(
            summary = summary,
            group = detailGroup,
            direction = direction,
            cleanupInProgress = cleanupInProgress,
            onBack = { detailGroupName = null },
            onDirectionChange = { directionName = it.name },
            onToggleItem = onToggleItem,
            onToggleGroup = { toggleUiGroup(summary, detailGroup, direction, onToggleCategory, onToggleItem) },
            onPreview = { previewItemId = it.id },
            onReviewAndClean = { showDeleteConfirmation = true },
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        WhatsAppHeader(onBack = onBack)

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { WhatsAppHeroCard(summary) }
            item {
                WhatsAppDirectionTabs(
                    selected = direction,
                    onSelected = { directionName = it.name },
                )
            }

            if (summary.items.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = WaCard),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(Icons.Outlined.Info, contentDescription = null, tint = WhatsAppGreen)
                            Spacer(Modifier.height(10.dp))
                            Text(stringResource(R.string.whatsapp_no_files), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                WhatsAppUiGroup.entries.forEach { group ->
                    val groupItems = filterWhatsAppItems(summary.items, group, direction)
                    if (groupItems.isNotEmpty()) {
                        item(key = "wa-group-${group.name}") {
                            WhatsAppGroupSection(
                                group = group,
                                items = groupItems,
                                onToggleItem = onToggleItem,
                                onToggleGroup = {
                                    toggleUiGroup(summary, group, direction, onToggleCategory, onToggleItem)
                                },
                                onViewAll = { detailGroupName = group.name },
                                onPreview = { previewItemId = it.id },
                            )
                        }
                    }
                }
            }

            item {
                TextButton(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.scan_again))
                }
            }
        }

        WhatsAppCleanupFooter(
            selectedCount = summary.selectedItems.size,
            selectedBytes = summary.selectedBytes,
            enabled = summary.selectedItems.isNotEmpty() && !cleanupInProgress,
            inProgress = cleanupInProgress,
            onClick = { showDeleteConfirmation = true },
        )
    }
}

@Composable
private fun WhatsAppHeroCard(summary: WhatsAppLibrarySummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = androidx.compose.foundation.BorderStroke(1.dp, WaCyan.copy(alpha = .35f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF063B3A), Color(0xFF0B9E72), Color(0xFF0860A5)),
                    ),
                )
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = Color(0xAA06162F),
                shape = CircleShape,
                border = androidx.compose.foundation.BorderStroke(2.dp, WaCyan.copy(alpha = .65f)),
            ) {
                Box(Modifier.padding(18.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = WhatsAppGreen,
                        modifier = Modifier.size(42.dp),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    stringResource(R.string.whatsapp_total_media),
                    color = Color.White.copy(alpha = .84f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                )
                Text(
                    ByteFormatter.format(summary.totalBytes),
                    color = Color.White,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    stringResource(R.string.whatsapp_scan_stats, summary.scannedFileCount),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.whatsapp_selection_safe),
                    color = Color.White.copy(alpha = .78f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun WhatsAppDirectionTabs(
    selected: WhatsAppDirectionFilter,
    onSelected: (WhatsAppDirectionFilter) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DirectionTab(
            title = stringResource(R.string.whatsapp_filter_incoming),
            icon = Icons.Outlined.Download,
            selected = selected == WhatsAppDirectionFilter.INCOMING,
            modifier = Modifier.weight(1f),
            onClick = { onSelected(WhatsAppDirectionFilter.INCOMING) },
        )
        DirectionTab(
            title = stringResource(R.string.whatsapp_filter_sent),
            icon = Icons.Outlined.Upload,
            selected = selected == WhatsAppDirectionFilter.SENT,
            modifier = Modifier.weight(1f),
            onClick = { onSelected(WhatsAppDirectionFilter.SENT) },
        )
        DirectionTab(
            title = stringResource(R.string.whatsapp_filter_all),
            icon = Icons.Outlined.GridView,
            selected = selected == WhatsAppDirectionFilter.ALL,
            modifier = Modifier.weight(1f),
            onClick = { onSelected(WhatsAppDirectionFilter.ALL) },
        )
    }
}

@Composable
private fun DirectionTab(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val background = if (selected) WhatsAppGreen.copy(alpha = .18f) else Color(0xFF09162F)
    val borderColor = if (selected) WhatsAppGreen.copy(alpha = .72f) else Color.White.copy(alpha = .10f)
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = background,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) WhatsAppGreen else WaTextSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                title,
                color = if (selected) Color.White else WaTextSecondary,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun WhatsAppGroupSection(
    group: WhatsAppUiGroup,
    items: List<WhatsAppMediaItem>,
    onToggleItem: (String) -> Unit,
    onToggleGroup: () -> Unit,
    onViewAll: () -> Unit,
    onPreview: (WhatsAppMediaItem) -> Unit,
) {
    val accent = groupAccent(group)
    val allSelected = items.isNotEmpty() && items.all(WhatsAppMediaItem::selected)
    Card(
        colors = CardDefaults.cardColors(containerColor = WaCard),
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .07f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(color = accent.copy(alpha = .16f), shape = RoundedCornerShape(13.dp)) {
                    Icon(
                        groupIcon(group),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.padding(9.dp).size(22.dp),
                    )
                }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(groupTitleRes(group)), color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Text(
                        stringResource(
                            R.string.whatsapp_category_summary,
                            items.size,
                            ByteFormatter.format(items.sumOf(WhatsAppMediaItem::sizeBytes)),
                        ),
                        color = WaTextSecondary,
                        fontSize = 11.sp,
                    )
                }
                TextButton(onClick = onViewAll) {
                    Text(stringResource(R.string.whatsapp_view_all), color = WhatsAppGreen, fontWeight = FontWeight.Bold)
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(18.dp))
                }
                Checkbox(
                    checked = allSelected,
                    onCheckedChange = { onToggleGroup() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = accent,
                        uncheckedColor = Color(0xFFB9C7DE),
                        checkmarkColor = Color.White,
                    ),
                )
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                val previewItems = items.sortedByDescending(WhatsAppMediaItem::modifiedAtMillis).take(4)
                items(previewItems, key = WhatsAppMediaItem::id) { item ->
                    WhatsAppMediaCard(
                        item = item,
                        group = group,
                        compact = true,
                        onPreview = { onPreview(item) },
                        onToggle = { onToggleItem(item.id) },
                    )
                }
                if (items.size > previewItems.size) {
                    item(key = "more-${group.name}") {
                        MoreItemsCard(
                            remaining = items.size - previewItems.size,
                            accent = accent,
                            onClick = onViewAll,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreItemsCard(
    remaining: Int,
    accent: Color,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(100.dp).height(174.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF091A38)),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = .25f)),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("+$remaining", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
            Spacer(Modifier.height(5.dp))
            Text(stringResource(R.string.whatsapp_view_all), color = accent, fontSize = 10.sp)
        }
    }
}

@Composable
private fun WhatsAppMediaCard(
    item: WhatsAppMediaItem,
    group: WhatsAppUiGroup,
    compact: Boolean,
    onPreview: () -> Unit,
    onToggle: () -> Unit,
) {
    val accent = groupAccent(group)
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = item.uri, key2 = item.modifiedAtMillis) {
        value = loadWhatsAppThumbnail(context, item)
    }
    val width = if (compact) 148.dp else 160.dp
    val height = if (compact) 174.dp else 200.dp
    val previewHeight = if (compact) 100.dp else 120.dp

    Card(
        onClick = onPreview,
        modifier = Modifier.width(width).height(height),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = WaCardSecondary),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (item.selected) accent.copy(alpha = .72f) else Color.White.copy(alpha = .08f),
        ),
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(previewHeight).background(Color(0xFF06132A)),
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = requireNotNull(bitmap),
                        contentDescription = item.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        fileIcon(item),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(42.dp),
                    )
                }
                if (group == WhatsAppUiGroup.VIDEOS) {
                    Surface(color = Color(0xBB020713), shape = CircleShape) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.padding(7.dp).size(24.dp))
                    }
                }
                Checkbox(
                    checked = item.selected,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.align(Alignment.TopEnd).padding(2.dp),
                    colors = CheckboxDefaults.colors(
                        checkedColor = accent,
                        uncheckedColor = Color.White,
                        checkmarkColor = Color.White,
                    ),
                )
                Surface(
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                    color = Color(0xCC020713),
                    shape = RoundedCornerShape(9.dp),
                ) {
                    Text(
                        ByteFormatter.format(item.sizeBytes),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    item.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 11.sp,
                )
                Text(
                    localDate(item.modifiedAtMillis),
                    color = WaTextSecondary,
                    fontSize = 9.sp,
                    maxLines = 1,
                )
                if (!compact) {
                    Text(
                        stringResource(if (item.selected) R.string.whatsapp_included else R.string.whatsapp_not_included),
                        color = if (item.selected) WhatsAppGreen else WaAmber,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun WhatsAppGroupDetailPage(
    summary: WhatsAppLibrarySummary,
    group: WhatsAppUiGroup,
    direction: WhatsAppDirectionFilter,
    cleanupInProgress: Boolean,
    onBack: () -> Unit,
    onDirectionChange: (WhatsAppDirectionFilter) -> Unit,
    onToggleItem: (String) -> Unit,
    onToggleGroup: () -> Unit,
    onPreview: (WhatsAppMediaItem) -> Unit,
    onReviewAndClean: () -> Unit,
) {
    var sortName by rememberSaveable(group.name) { mutableStateOf(WhatsAppSortMode.LARGEST.name) }
    val sortMode = runCatching { WhatsAppSortMode.valueOf(sortName) }.getOrDefault(WhatsAppSortMode.LARGEST)
    val filtered = filterWhatsAppItems(summary.items, group, direction)
    val displayItems = when (sortMode) {
        WhatsAppSortMode.LARGEST -> filtered.sortedByDescending(WhatsAppMediaItem::sizeBytes)
        WhatsAppSortMode.NEWEST -> filtered.sortedByDescending(WhatsAppMediaItem::modifiedAtMillis)
    }
    val allSelected = filtered.isNotEmpty() && filtered.all(WhatsAppMediaItem::selected)

    Column(Modifier.fillMaxSize()) {
        WhatsAppHeader(onBack = onBack, subtitle = stringResource(groupTitleRes(group)))

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = WaCard),
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = groupAccent(group).copy(alpha = .16f), shape = CircleShape) {
                        Icon(groupIcon(group), contentDescription = null, tint = groupAccent(group), modifier = Modifier.padding(11.dp).size(26.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(groupTitleRes(group)), color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        Text(
                            stringResource(
                                R.string.whatsapp_category_summary,
                                filtered.size,
                                ByteFormatter.format(filtered.sumOf(WhatsAppMediaItem::sizeBytes)),
                            ),
                            color = WaTextSecondary,
                        )
                    }
                    Checkbox(
                        checked = allSelected,
                        onCheckedChange = { onToggleGroup() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = groupAccent(group),
                            uncheckedColor = Color(0xFFB9C7DE),
                            checkmarkColor = Color.White,
                        ),
                    )
                }
                Text(
                    stringResource(R.string.whatsapp_card_hint),
                    color = WaCyan,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        WhatsAppDirectionTabs(
            selected = direction,
            onSelected = onDirectionChange,
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SortTab(
                title = stringResource(R.string.whatsapp_sort_largest),
                icon = Icons.Outlined.GridView,
                selected = sortMode == WhatsAppSortMode.LARGEST,
                modifier = Modifier.weight(1f),
                onClick = { sortName = WhatsAppSortMode.LARGEST.name },
            )
            SortTab(
                title = stringResource(R.string.whatsapp_sort_newest),
                icon = Icons.Outlined.AccessTime,
                selected = sortMode == WhatsAppSortMode.NEWEST,
                modifier = Modifier.weight(1f),
                onClick = { sortName = WhatsAppSortMode.NEWEST.name },
            )
        }

        if (displayItems.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.whatsapp_no_files_for_filter), color = WaTextSecondary)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                gridItems(displayItems, key = WhatsAppMediaItem::id) { item ->
                    WhatsAppMediaCard(
                        item = item,
                        group = group,
                        compact = false,
                        onPreview = { onPreview(item) },
                        onToggle = { onToggleItem(item.id) },
                    )
                }
            }
        }

        WhatsAppCleanupFooter(
            selectedCount = summary.selectedItems.size,
            selectedBytes = summary.selectedBytes,
            enabled = summary.selectedItems.isNotEmpty() && !cleanupInProgress,
            inProgress = cleanupInProgress,
            onClick = onReviewAndClean,
        )
    }
}

@Composable
private fun SortTab(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = if (selected) Color(0xFF0B3150) else Color(0xFF07152D),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) WaCyan.copy(alpha = .55f) else Color.White.copy(alpha = .08f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) WaCyan else WaTextSecondary, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text(title, color = if (selected) Color.White else WaTextSecondary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    }
}

@Composable
private fun WhatsAppCleanupFooter(
    selectedCount: Int,
    selectedBytes: Long,
    enabled: Boolean,
    inProgress: Boolean,
    onClick: () -> Unit,
) {
    Surface(color = Color(0xFF020817), shadowElevation = 18.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Card(
                modifier = Modifier.weight(.38f).height(64.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF091B39)),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, WhatsAppGreen.copy(alpha = .20f)),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(25.dp))
                    Spacer(Modifier.width(7.dp))
                    Column {
                        Text(stringResource(R.string.whatsapp_selected_size), color = WaTextSecondary, fontSize = 9.sp)
                        Text(ByteFormatter.format(selectedBytes), color = WhatsAppGreen, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text(stringResource(R.string.whatsapp_selected_items_short, selectedCount), color = WaTextSecondary, fontSize = 9.sp)
                    }
                }
            }

            Card(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.weight(.62f).height(64.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent, disabledContainerColor = Color(0xFF19304F)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (enabled) {
                                Brush.horizontalGradient(listOf(Color(0xFF0DCB72), WaCyan, Color(0xFF16D86A)))
                            } else {
                                Brush.horizontalGradient(listOf(Color(0xFF19304F), Color(0xFF203957)))
                            },
                        )
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(if (inProgress) R.string.whatsapp_cleanup_in_progress else R.string.whatsapp_review_and_clean),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun WhatsAppCleanupConfirmationDialog(
    summary: WhatsAppLibrarySummary,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = WhatsAppGreen) },
        title = { Text(stringResource(R.string.whatsapp_delete_confirm_title), fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(
                        R.string.whatsapp_delete_confirm_message,
                        summary.selectedItems.size,
                        ByteFormatter.format(summary.selectedBytes),
                    ),
                )
                summary.selectedItems
                    .groupBy(::uiGroupOf)
                    .forEach { (group, items) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(groupTitleRes(group)), style = MaterialTheme.typography.bodySmall)
                            Text(
                                "${items.size} • ${ByteFormatter.format(items.sumOf(WhatsAppMediaItem::sizeBytes))}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                Text(
                    stringResource(R.string.whatsapp_cleanup_ad_notice),
                    color = ElectricBlue,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.whatsapp_cleanup_irreversible),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.whatsapp_confirm_and_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun WhatsAppPreviewDialog(
    item: WhatsAppMediaItem,
    selected: Boolean,
    onToggleSelection: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = WaBackground) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back), tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.whatsapp_preview_title), color = WhatsAppGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(item.name, color = Color.White, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 18.sp)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFF020713)),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        item.mimeType.startsWith("image/") -> WhatsAppImagePreview(item)
                        item.mimeType.startsWith("video/") -> WhatsAppVideoPreview(item)
                        item.mimeType.startsWith("audio/") || item.category == WhatsAppMediaCategory.VOICE_NOTES -> WhatsAppAudioPreview(item)
                        else -> WhatsAppGenericPreview(item)
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    colors = CardDefaults.cardColors(containerColor = WaCard),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = groupAccent(uiGroupOf(item)).copy(alpha = .15f), shape = RoundedCornerShape(14.dp)) {
                                Icon(fileIcon(item), contentDescription = null, tint = groupAccent(uiGroupOf(item)), modifier = Modifier.padding(10.dp).size(25.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, color = Color.White, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(compactPath(item.relativePath), color = WaTextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(ByteFormatter.format(item.sizeBytes), color = groupAccent(uiGroupOf(item)), fontWeight = FontWeight.Black)
                                Text(localDate(item.modifiedAtMillis), color = WaTextSecondary, fontSize = 10.sp)
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (selected) Color(0xFF0C4936) else Color(0xFF10284C))
                                .border(
                                    1.dp,
                                    if (selected) WhatsAppGreen.copy(alpha = .70f) else Color(0xFF5D7498),
                                    RoundedCornerShape(16.dp),
                                )
                                .clickable(onClick = onToggleSelection)
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = selected,
                                onCheckedChange = { onToggleSelection() },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = WhatsAppGreen,
                                    uncheckedColor = Color(0xFFB9C7DE),
                                    checkmarkColor = Color(0xFF03130D),
                                ),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(if (selected) R.string.whatsapp_included else R.string.whatsapp_not_included),
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                )
                                Text(
                                    stringResource(if (selected) R.string.whatsapp_remove_from_cleanup else R.string.whatsapp_add_to_cleanup),
                                    color = Color(0xFFC1CEE2),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WhatsAppImagePreview(item: WhatsAppMediaItem) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = item.uri) {
        value = loadWhatsAppDisplayBitmap(context, item)
    }
    if (bitmap == null) {
        CircularProgressIndicator(color = WhatsAppGreen)
    } else {
        Image(
            bitmap = requireNotNull(bitmap),
            contentDescription = item.name,
            modifier = Modifier.fillMaxSize().padding(8.dp),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun WhatsAppVideoPreview(item: WhatsAppMediaItem) {
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

            override fun onPlayerError(error: PlaybackException) {
                failed = true
            }
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
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Outlined.VideoFile, contentDescription = null, tint = WaAmber, modifier = Modifier.size(42.dp))
                    Text(
                        stringResource(if (noVideoTrack) R.string.smart_video_no_picture else R.string.smart_video_preview_error),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun WhatsAppAudioPreview(item: WhatsAppMediaItem) {
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
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlayerError(error: PlaybackException) {
                failed = true
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(color = Purple500.copy(alpha = .16f), shape = CircleShape) {
            Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = Purple500, modifier = Modifier.padding(24.dp).size(56.dp))
        }
        Text(item.name, color = Color.White, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (failed) {
            Text(stringResource(R.string.whatsapp_audio_preview_error), color = WaAmber)
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
private fun WhatsAppGenericPreview(item: WhatsAppMediaItem) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(color = WaAmber.copy(alpha = .15f), shape = RoundedCornerShape(26.dp)) {
            Icon(fileIcon(item), contentDescription = null, tint = WaAmber, modifier = Modifier.padding(26.dp).size(64.dp))
        }
        Text(stringResource(R.string.whatsapp_file_preview), color = Color.White, fontWeight = FontWeight.Black)
        Text(item.name, color = WaTextSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text(ByteFormatter.format(item.sizeBytes), color = WhatsAppGreen, fontWeight = FontWeight.Black)
    }
}

private fun filterWhatsAppItems(
    items: List<WhatsAppMediaItem>,
    group: WhatsAppUiGroup,
    direction: WhatsAppDirectionFilter,
): List<WhatsAppMediaItem> = items.filter { item ->
    uiGroupOf(item) == group && when (direction) {
        WhatsAppDirectionFilter.ALL -> true
        WhatsAppDirectionFilter.INCOMING -> !isSentWhatsAppItem(item)
        WhatsAppDirectionFilter.SENT -> isSentWhatsAppItem(item)
    }
}

private fun toggleUiGroup(
    summary: WhatsAppLibrarySummary,
    group: WhatsAppUiGroup,
    direction: WhatsAppDirectionFilter,
    onToggleCategory: (WhatsAppMediaCategory) -> Unit,
    onToggleItem: (String) -> Unit,
) {
    val visible = filterWhatsAppItems(summary.items, group, direction)
    if (visible.isEmpty()) return
    val shouldSelect = visible.any { !it.selected }

    // If the visible group exactly covers whole underlying categories, use the ViewModel's category toggle.
    val categories = visible.map(WhatsAppMediaItem::category).toSet()
    val canUseCategoryToggle = categories.all { category ->
        val allCategoryItems = summary.items.filter { it.category == category }
        allCategoryItems.isNotEmpty() && allCategoryItems.all { it in visible }
    }
    if (canUseCategoryToggle) {
        categories.forEach(onToggleCategory)
        return
    }

    visible.filter { it.selected != shouldSelect }.forEach { onToggleItem(it.id) }
}

private fun uiGroupOf(item: WhatsAppMediaItem): WhatsAppUiGroup = when {
    item.mimeType.startsWith("image/") ||
        item.category == WhatsAppMediaCategory.IMAGES ||
        item.category == WhatsAppMediaCategory.STICKERS_GIFS -> WhatsAppUiGroup.IMAGES

    item.mimeType.startsWith("video/") || item.category == WhatsAppMediaCategory.VIDEOS -> WhatsAppUiGroup.VIDEOS
    else -> WhatsAppUiGroup.DOCUMENTS_OTHER
}

private fun isSentWhatsAppItem(item: WhatsAppMediaItem): Boolean {
    val segments = item.relativePath.replace('\\', '/').split('/').filter(String::isNotBlank)
    return segments.any { it.equals("Sent", ignoreCase = true) }
}

private fun groupTitleRes(group: WhatsAppUiGroup): Int = when (group) {
    WhatsAppUiGroup.IMAGES -> R.string.whatsapp_group_images
    WhatsAppUiGroup.VIDEOS -> R.string.whatsapp_group_videos
    WhatsAppUiGroup.DOCUMENTS_OTHER -> R.string.whatsapp_group_documents_more
}

private fun groupIcon(group: WhatsAppUiGroup): ImageVector = when (group) {
    WhatsAppUiGroup.IMAGES -> Icons.Outlined.PhotoLibrary
    WhatsAppUiGroup.VIDEOS -> Icons.Outlined.VideoFile
    WhatsAppUiGroup.DOCUMENTS_OTHER -> Icons.Outlined.Description
}

private fun groupAccent(group: WhatsAppUiGroup): Color = when (group) {
    WhatsAppUiGroup.IMAGES -> WhatsAppGreen
    WhatsAppUiGroup.VIDEOS -> Purple500
    WhatsAppUiGroup.DOCUMENTS_OTHER -> WaAmber
}

private fun fileIcon(item: WhatsAppMediaItem): ImageVector = when {
    item.mimeType.startsWith("image/") -> Icons.Outlined.PhotoLibrary
    item.mimeType.startsWith("video/") -> Icons.Outlined.VideoFile
    item.mimeType.startsWith("audio/") || item.category == WhatsAppMediaCategory.VOICE_NOTES -> Icons.Outlined.MusicNote
    item.category == WhatsAppMediaCategory.DOCUMENTS -> Icons.Outlined.Description
    else -> Icons.Outlined.Folder
}

@Composable
private fun localDate(millis: Long): String = remember(millis) {
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))
}

private fun compactPath(path: String): String {
    val parts = path.replace('\\', '/').trim('/').split('/').filter(String::isNotBlank)
    return when {
        parts.isEmpty() -> "—"
        parts.size <= 3 -> parts.joinToString(" › ")
        else -> parts.takeLast(3).joinToString(" › ")
    }
}

private suspend fun loadWhatsAppThumbnail(context: Context, item: WhatsAppMediaItem): ImageBitmap? = withContext(Dispatchers.IO) {
    WhatsAppThumbnailCache.get(item.uri)?.let { return@withContext it }
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
                item.mimeType.startsWith("image/") -> decodeSampledBitmap(file, 520)
                else -> null
            }
        }
        loaded?.asImageBitmap()
    }.getOrNull()
    if (bitmap != null) WhatsAppThumbnailCache.put(item.uri, bitmap)
    bitmap
}

private suspend fun loadWhatsAppDisplayBitmap(context: Context, item: WhatsAppMediaItem): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val uri = Uri.parse(item.uri)
        if (uri.scheme == "content") {
            context.contentResolver.loadThumbnail(uri, Size(1600, 1600), null).asImageBitmap()
        } else {
            val path = uri.path ?: return@runCatching null
            val file = File(path)
            if (!file.isFile) return@runCatching null
            decodeSampledBitmap(file, 1600)?.asImageBitmap()
        }
    }.getOrNull()
}

private fun decodeSampledBitmap(file: File, maxSide: Int): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    val largest = maxOf(bounds.outWidth, bounds.outHeight)
    while (largest / sample > maxSide) sample *= 2
    return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
}
