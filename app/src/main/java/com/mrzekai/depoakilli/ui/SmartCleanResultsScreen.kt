package com.mrzekai.depoakilli.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.util.Size
import android.view.LayoutInflater
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
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import com.mrzekai.depoakilli.model.IndexedFile
import com.mrzekai.depoakilli.model.ScanSummary
import com.mrzekai.depoakilli.model.StorageFileType
import com.mrzekai.depoakilli.ui.theme.ElectricBlue
import com.mrzekai.depoakilli.ui.theme.Lime400
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val SmartBackground = Color(0xFF030B20)
private val SmartCard = Color(0xFF0A1C42)
private val SmartCardAlt = Color(0xFF0D2450)
private val SmartTextSecondary = Color(0xFFB4C4DF)
private val SmartCyan = Color(0xFF16C7FF)
private val SmartGreen = Color(0xFF28E58A)

private data class SmartPreviewFile(
    val uri: String,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val modifiedAtMillis: Long,
    val relativePath: String,
)

private fun CleanableItem.toSmartPreview() = SmartPreviewFile(
    uri = uri,
    name = name,
    sizeBytes = sizeBytes,
    mimeType = mimeType,
    modifiedAtMillis = modifiedAtMillis,
    relativePath = relativePath,
)

private fun IndexedFile.toSmartPreview() = SmartPreviewFile(
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
    onClean: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        !state.hasAllFilesAccess -> SmartAccessRequired(
            onRequest = onRequestAllFilesAccess,
            modifier = modifier,
        )

        state.scanning -> SmartScanningState(state = state, modifier = modifier)
        !state.lastScanCompleted -> SmartEmptyState(onScan = onScan, modifier = modifier)
        else -> SmartResultsContent(
            summary = state.summary,
            cleanupInProgress = state.cleanupInProgress,
            onScan = onScan,
            onToggleItem = onToggleItem,
            onToggleCategory = onToggleCategory,
            onClean = onClean,
            modifier = modifier,
        )
    }
}

@Composable
private fun SmartAccessRequired(onRequest: () -> Unit, modifier: Modifier) {
    Box(
        modifier.fillMaxSize().background(SmartBackground).padding(22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = SmartCard),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(Icons.Outlined.Security, contentDescription = null, tint = SmartCyan, modifier = Modifier.size(54.dp))
                Text(stringResource(R.string.all_files_access_title), color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(stringResource(R.string.all_files_access_clean_screen), color = SmartTextSecondary)
                Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.grant_access))
                }
            }
        }
    }
}

@Composable
private fun SmartScanningState(state: CleanerUiState, modifier: Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF03091B),
                        Color(0xFF071A4B),
                        Color(0xFF05142E),
                    ),
                ),
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card(
                shape = RoundedCornerShape(30.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF071B45)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF0A2D87),
                                    Color(0xFF0B59C9),
                                    Color(0xFF0B8B86),
                                ),
                            ),
                        )
                        .padding(20.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(100.dp),
                                strokeWidth = 8.dp,
                                color = SmartGreen,
                                trackColor = Color.White.copy(alpha = .14f),
                            )
                            Surface(
                                color = Color(0xFF071B45).copy(alpha = .84f),
                                shape = CircleShape,
                            ) {
                                Icon(
                                    Icons.Outlined.CleaningServices,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.padding(18.dp).size(38.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(18.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Text(
                                stringResource(R.string.smart_scan_progress_title),
                                color = Color.White,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black,
                            )
                            Text(
                                stringResource(R.string.smart_scan_progress_subtitle),
                                color = Color.White.copy(alpha = .86f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            Text(
                stringResource(R.string.smart_scan_live_title),
                color = Color.White,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleMedium,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ScanMetricCard(
                    value = state.scanProgressFiles.toString(),
                    label = stringResource(R.string.smart_scan_files_label),
                    icon = Icons.Outlined.InsertDriveFile,
                    modifier = Modifier.weight(1f),
                )
                ScanMetricCard(
                    value = state.scanProgressDirectories.toString(),
                    label = stringResource(R.string.smart_scan_folders_label),
                    icon = Icons.Outlined.Folder,
                    modifier = Modifier.weight(1f),
                )
                ScanMetricCard(
                    value = stringResource(R.string.smart_scan_on_device_value),
                    label = stringResource(R.string.smart_scan_on_device_label),
                    icon = Icons.Outlined.Security,
                    modifier = Modifier.weight(1f),
                )
            }

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SmartCard),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(R.string.smart_scan_checking_title),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ScanStagePill(
                            title = stringResource(CleanCategory.JUNK.titleRes),
                            accent = Color(0xFF35BFFF),
                            modifier = Modifier.weight(1f),
                        )
                        ScanStagePill(
                            title = stringResource(CleanCategory.DUPLICATE.titleRes),
                            accent = Color(0xFF9A63FF),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ScanStagePill(
                            title = stringResource(CleanCategory.LARGE_FILE.titleRes),
                            accent = Color(0xFFFFB02E),
                            modifier = Modifier.weight(1f),
                        )
                        ScanStagePill(
                            title = stringResource(CleanCategory.APK_PACKAGE.titleRes),
                            accent = Color(0xFF6ADE48),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    ScanStagePill(
                        title = stringResource(R.string.smart_scan_media_stage),
                        accent = Color(0xFF31E0D0),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF09224E),
                shape = RoundedCornerShape(18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Security, contentDescription = null, tint = SmartGreen, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(9.dp))
                    Text(
                        stringResource(R.string.scan_private),
                        color = Color.White.copy(alpha = .90f),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanMetricCard(
    value: String,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.height(86.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SmartCard),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(icon, contentDescription = null, tint = SmartCyan, modifier = Modifier.size(22.dp))
            Column {
                Text(value, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp, maxLines = 1)
                Text(label, color = SmartTextSecondary, fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ScanStagePill(
    title: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = accent.copy(alpha = .13f),
        shape = RoundedCornerShape(15.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = .45f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                title,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SmartEmptyState(onScan: () -> Unit, modifier: Modifier) {
    Box(
        modifier.fillMaxSize().background(SmartBackground).padding(22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = SmartCard),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = SmartCyan, modifier = Modifier.size(56.dp))
                Text(stringResource(R.string.smart_clean_results_title), color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(stringResource(R.string.smart_clean_results_empty_intro), color = SmartTextSecondary)
                Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.start_scan)) }
            }
        }
    }
}

@Composable
private fun SmartResultsContent(
    summary: ScanSummary,
    cleanupInProgress: Boolean,
    onScan: () -> Unit,
    onToggleItem: (String) -> Unit,
    onToggleCategory: (CleanCategory) -> Unit,
    onClean: () -> Unit,
    modifier: Modifier,
) {
    var confirmCleanup by remember { mutableStateOf(false) }
    var openCategory by remember { mutableStateOf<CleanCategory?>(null) }
    var openStorageType by remember { mutableStateOf<StorageFileType?>(null) }
    var previewItemId by remember { mutableStateOf<String?>(null) }
    var previewStorageFile by remember { mutableStateOf<IndexedFile?>(null) }
    var expandedCategories by remember {
        mutableStateOf(setOf(CleanCategory.WHATSAPP_MEDIA, CleanCategory.DUPLICATE))
    }

    Column(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF02081A), Color(0xFF05142E), Color(0xFF030A1E)),
                ),
            ),
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SmartCleanHero(summary = summary)
            }

            if (summary.byCategory.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = SmartCard),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = SmartGreen)
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.no_safe_suggestions), color = Color.White)
                        }
                    }
                }
            } else {
                orderedSmartCategories(summary).forEach { category ->
                    val categoryItems = summary.byCategory[category].orEmpty()
                    if (categoryItems.isNotEmpty()) {
                        item(key = "smart-category-${category.name}") {
                            SmartCategoryStripCard(
                                category = category,
                                items = categoryItems,
                                expanded = category in expandedCategories,
                                onExpandToggle = {
                                    expandedCategories = if (category in expandedCategories) {
                                        expandedCategories - category
                                    } else {
                                        expandedCategories + category
                                    }
                                },
                                onToggleCategory = { onToggleCategory(category) },
                                onPreview = { previewItemId = it.id },
                                onOpenAll = { openCategory = category },
                                onToggleItem = onToggleItem,
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    stringResource(R.string.storage_analyzer_title),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                )
            }

            val storageRows = summary.storageTypes.chunked(3)
            storageRows.forEachIndexed { rowIndex, rowStats ->
                item(key = "storage-row-$rowIndex") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowStats.forEach { stat ->
                            StorageGridTile(
                                type = stat.type,
                                count = stat.fileCount,
                                bytes = stat.totalBytes,
                                onClick = { openStorageType = stat.type },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(3 - rowStats.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            if (summary.scanLimitReached) {
                item {
                    Text(
                        stringResource(R.string.scan_limit_note_v050),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFF8A9A),
                    )
                }
            }

            item {
                OutlinedButton(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.scan_again))
                }
            }
        }

        SmartCleanBottomAction(
            selectedBytes = summary.selectedBytes,
            enabled = summary.selectedItems.isNotEmpty() && !cleanupInProgress,
            inProgress = cleanupInProgress,
            onClick = { if (!cleanupInProgress) confirmCleanup = true },
        )
    }

    if (confirmCleanup) {
        CleanupConfirmationDialog(
            summary = summary,
            onDismiss = { confirmCleanup = false },
            onConfirm = {
                confirmCleanup = false
                onClean()
            },
        )
    }

    openCategory?.let { category ->
        CategoryDetailDialog(
            category = category,
            items = summary.byCategory[category].orEmpty(),
            onDismiss = { openCategory = null },
            onToggleCategory = { onToggleCategory(category) },
            onToggleItem = onToggleItem,
            onPreview = { previewItemId = it.id },
        )
    }

    openStorageType?.let { type ->
        StorageDetailDialog(
            type = type,
            files = summary.storagePreviews[type].orEmpty(),
            onDismiss = { openStorageType = null },
            onPreview = { previewStorageFile = it },
        )
    }

    previewItemId?.let { itemId ->
        summary.items.firstOrNull { it.id == itemId }?.let { item ->
            FilePreviewDialog(
                file = item.toSmartPreview(),
                selected = item.selected,
                reason = stringResource(item.assessment.reasonRes, *item.assessment.reasonArgs.toTypedArray()),
                onToggleSelection = { onToggleItem(item.id) },
                onDismiss = { previewItemId = null },
            )
        }
    }

    previewStorageFile?.let { file ->
        FilePreviewDialog(
            file = file.toSmartPreview(),
            selected = null,
            reason = null,
            onToggleSelection = null,
            onDismiss = { previewStorageFile = null },
        )
    }
}

private fun orderedSmartCategories(summary: ScanSummary): List<CleanCategory> {
    val priority = listOf(
        CleanCategory.WHATSAPP_MEDIA,
        CleanCategory.DUPLICATE,
        CleanCategory.LARGE_FILE,
        CleanCategory.APK_PACKAGE,
        CleanCategory.SCREENSHOT,
        CleanCategory.JUNK,
        CleanCategory.OLD_DOWNLOAD,
        CleanCategory.APP_CACHE,
    )
    return priority.filter { summary.byCategory[it].orEmpty().isNotEmpty() }
}

@Composable
private fun SmartCleanHero(summary: ScanSummary) {
    Card(
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(listOf(Color(0xFF147DFF), SmartCyan, SmartGreen)),
                shape = RoundedCornerShape(30.dp),
            ),
    ) {
        Column(
            Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF061D58), Color(0xFF071C44), Color(0xFF063D35)),
                    ),
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(98.dp)
                        .border(
                            4.dp,
                            Brush.sweepGradient(listOf(Color(0xFF285CFF), SmartCyan, SmartGreen, Color(0xFF7C3CFF), Color(0xFF285CFF))),
                            CircleShape,
                        )
                        .background(Color(0xFF081D51), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.CleaningServices,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(45.dp),
                    )
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        ByteFormatter.format(summary.selectedBytes),
                        color = Color.White,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                    )
                    Text(
                        stringResource(R.string.cleanable_space),
                        color = SmartGreen,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.smart_clean_hero_subtitle),
                        color = Color.White.copy(alpha = .88f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Surface(
                color = Color(0xFF061735).copy(alpha = .82f),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    HeroStat(
                        icon = Icons.Outlined.CheckCircle,
                        value = summary.selectedItems.size.toString(),
                        label = stringResource(R.string.smart_items_selected),
                    )
                    HeroStat(
                        icon = Icons.Outlined.AutoAwesome,
                        value = ByteFormatter.format(summary.reviewBytes),
                        label = stringResource(R.string.smart_review_candidates),
                    )
                    HeroStat(
                        icon = Icons.Outlined.Storage,
                        value = summary.scannedFileCount.toString(),
                        label = stringResource(R.string.smart_files_scanned),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroStat(icon: ImageVector, value: String, label: String) {
    Column(
        modifier = Modifier.width(92.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF9A8CFF), modifier = Modifier.size(21.dp))
        Text(value, color = Color.White, fontWeight = FontWeight.Black, maxLines = 1, fontSize = 14.sp)
        Text(label, color = SmartTextSecondary, fontSize = 10.sp, maxLines = 2)
    }
}

@Composable
private fun SmartCategoryStripCard(
    category: CleanCategory,
    items: List<CleanableItem>,
    expanded: Boolean,
    onExpandToggle: () -> Unit,
    onToggleCategory: () -> Unit,
    onPreview: (CleanableItem) -> Unit,
    onOpenAll: () -> Unit,
    onToggleItem: (String) -> Unit,
) {
    val accent = categoryAccent(category)
    val allSelected = items.isNotEmpty() && items.all(CleanableItem::selected)
    val selectedBytes = items.filter(CleanableItem::selected).sumOf(CleanableItem::sizeBytes)
    val totalBytes = items.sumOf(CleanableItem::sizeBytes)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SmartCard),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onExpandToggle)
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(color = accent.copy(alpha = .17f), shape = RoundedCornerShape(13.dp)) {
                    Icon(
                        categoryVisual(category),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.padding(9.dp).size(25.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(category.titleRes),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(R.string.category_summary, items.size, ByteFormatter.format(totalBytes)),
                        color = SmartTextSecondary,
                        fontSize = 12.sp,
                    )
                    if (category == CleanCategory.JUNK) {
                        Text(
                            stringResource(category.shortDescriptionRes),
                            color = Color(0xFF8FDFFF),
                            fontSize = 10.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Surface(color = accent.copy(alpha = .18f), shape = RoundedCornerShape(20.dp)) {
                    Text(
                        ByteFormatter.format(if (selectedBytes > 0L) selectedBytes else totalBytes),
                        color = accent,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        fontSize = 12.sp,
                    )
                }
                Checkbox(
                    checked = allSelected,
                    onCheckedChange = { onToggleCategory() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = ElectricBlue,
                        uncheckedColor = Color(0xFF7187AB),
                        checkmarkColor = Color.White,
                    ),
                )
                Text(if (expanded) "⌃" else "⌄", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
            }

            if (expanded) {
                LazyRow(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items.take(4), key = { it.id }) { item ->
                        SmartHorizontalPreviewTile(
                            item = item,
                            accent = accent,
                            onPreview = { onPreview(item) },
                            onToggle = { onToggleItem(item.id) },
                        )
                    }
                    if (items.size > 4) {
                        item(key = "more-${category.name}") {
                            MoreItemsTile(
                                count = items.size - 4,
                                accent = accent,
                                onClick = onOpenAll,
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onOpenAll) {
                        Text(stringResource(R.string.smart_show_all_count, items.size), color = accent, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = accent, modifier = Modifier.size(17.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartHorizontalPreviewTile(
    item: CleanableItem,
    accent: Color,
    onPreview: () -> Unit,
    onToggle: () -> Unit,
) {
    val context = LocalContext.current
    val preview = item.toSmartPreview()
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = item.uri, key2 = item.mimeType) {
        value = loadPreviewThumbnail(context, preview)
    }

    Card(
        modifier = Modifier.width(142.dp).height(116.dp).clickable(onClick = onPreview),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SmartCardAlt),
    ) {
        Box(Modifier.fillMaxSize()) {
            if (bitmap != null) {
                Image(
                    bitmap = requireNotNull(bitmap),
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000718))))
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(10.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(fileVisual(item.mimeType, item.name), contentDescription = null, tint = accent, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(item.name, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 11.sp)
                }
            }

            Checkbox(
                checked = item.selected,
                onCheckedChange = { onToggle() },
                modifier = Modifier.align(Alignment.TopEnd),
                colors = CheckboxDefaults.colors(
                    checkedColor = ElectricBlue,
                    uncheckedColor = Color.White,
                    checkmarkColor = Color.White,
                ),
            )

            Column(
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
            ) {
                if (bitmap != null) {
                    Text(item.name, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(ByteFormatter.format(item.sizeBytes), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun MoreItemsTile(count: Int, accent: Color, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(88.dp).height(116.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SmartCardAlt),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("+$count", color = accent, fontWeight = FontWeight.Black, fontSize = 19.sp)
        }
    }
}

@Composable
private fun StorageGridTile(
    type: StorageFileType,
    count: Int,
    bytes: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = storageAccent(type)
    Card(
        onClick = onClick,
        modifier = modifier.height(96.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SmartCard),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = accent.copy(alpha = .18f), shape = RoundedCornerShape(10.dp)) {
                    Icon(storageTypeVisual(type), contentDescription = null, tint = accent, modifier = Modifier.padding(7.dp).size(20.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(type.titleRes),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(ByteFormatter.format(bytes), color = SmartTextSecondary, fontSize = 11.sp)
                    Text(stringResource(R.string.smart_storage_file_count, count), color = Color(0xFF7187AB), fontSize = 9.sp, maxLines = 1)
                }
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = Color(0xFFA9B9D5), modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun SmartCleanBottomAction(
    selectedBytes: Long,
    enabled: Boolean,
    inProgress: Boolean,
    onClick: () -> Unit,
) {
    Surface(shadowElevation = 18.dp, color = Color(0xFF030B20)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Card(
                modifier = Modifier.weight(.38f).height(66.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0A1B41)),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(stringResource(R.string.smart_selected_for_cleanup), color = SmartTextSecondary, fontSize = 10.sp, maxLines = 1)
                    Text(ByteFormatter.format(selectedBytes), color = SmartGreen, fontSize = 21.sp, fontWeight = FontWeight.Black, maxLines = 1)
                }
            }

            Card(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.weight(.62f).height(66.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent, disabledContainerColor = Color(0xFF16315A)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (enabled) Brush.horizontalGradient(listOf(Color(0xFF0C46FF), SmartCyan, Color(0xFF13CD43)))
                            else Brush.horizontalGradient(listOf(Color(0xFF16315A), Color(0xFF1B365B))),
                        )
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.CleaningServices, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(if (inProgress) R.string.smart_cleanup_in_progress else R.string.smart_review_and_clean),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = Color.White)
                }
            }
        }
    }
}

private suspend fun loadPreviewThumbnail(context: Context, file: SmartPreviewFile): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val uri = Uri.parse(file.uri)
        if (uri.scheme == "content") {
            return@runCatching context.contentResolver.loadThumbnail(uri, Size(360, 260), null).asImageBitmap()
        }
        val path = uri.path ?: file.uri
        val source = File(path)
        if (!source.isFile) return@runCatching null
        when {
            file.mimeType.startsWith("video/") -> {
                android.media.ThumbnailUtils.createVideoThumbnail(source, Size(360, 260), null)?.asImageBitmap()
            }
            file.mimeType.startsWith("image/") -> loadDisplayBitmap(context, file, maxSide = 480)
            else -> null
        }
    }.getOrNull()
}

@Composable
private fun CleanupConfirmationDialog(summary: ScanSummary, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.DeleteSweep, contentDescription = null, tint = ElectricBlue) },
        title = { Text(stringResource(R.string.smart_cleanup_confirm_title), fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(stringResource(R.string.smart_cleanup_confirm_body, ByteFormatter.format(summary.selectedBytes), summary.selectedItems.size))
                summary.selectedItems
                    .groupBy { it.assessment.category }
                    .entries
                    .sortedByDescending { (_, items) -> items.sumOf(CleanableItem::sizeBytes) }
                    .forEach { (category, items) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(category.titleRes), style = MaterialTheme.typography.bodySmall)
                            Text("${items.size} • ${ByteFormatter.format(items.sumOf(CleanableItem::sizeBytes))}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                    }
                Text(stringResource(R.string.smart_cleanup_selected_examples), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                summary.selectedItems.take(5).forEach { item ->
                    Text("• ${item.name}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (summary.selectedItems.size > 5) {
                    Text(
                        stringResource(R.string.smart_cleanup_more_items, summary.selectedItems.size - 5),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    stringResource(R.string.smart_cleanup_ad_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF2867D8),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(stringResource(R.string.smart_cleanup_confirm_warning), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text(stringResource(R.string.smart_cleanup_confirm_action)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun CategoryDetailDialog(
    category: CleanCategory,
    items: List<CleanableItem>,
    onDismiss: () -> Unit,
    onToggleCategory: () -> Unit,
    onToggleItem: (String) -> Unit,
    onPreview: (CleanableItem) -> Unit,
) {
    val displayItems = items.sortedByDescending(CleanableItem::sizeBytes)
    val selectedCount = items.count(CleanableItem::selected)
    val selectedBytes = items.asSequence().filter(CleanableItem::selected).sumOf(CleanableItem::sizeBytes)
    val allSelected = items.isNotEmpty() && selectedCount == items.size
    val accent = categoryAccent(category)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SmartBackground,
        ) {
            Column(Modifier.fillMaxSize()) {
                DialogHeader(title = stringResource(category.titleRes), onBack = onDismiss)

                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = SmartCard),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(
                                        R.string.smart_detail_selected_summary,
                                        selectedCount,
                                        items.size,
                                        ByteFormatter.format(selectedBytes),
                                    ),
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                )
                                Text(
                                    stringResource(R.string.smart_detail_grid_hint),
                                    color = SmartTextSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Surface(
                                color = accent.copy(alpha = .16f),
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Text(
                                    ByteFormatter.format(items.sumOf(CleanableItem::sizeBytes)),
                                    color = accent,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF102850))
                                .clickable { onToggleCategory() }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = allSelected,
                                onCheckedChange = { onToggleCategory() },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = ElectricBlue,
                                    uncheckedColor = Color(0xFFA7B8D6),
                                    checkmarkColor = Color.White,
                                ),
                            )
                            Spacer(Modifier.width(5.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(if (allSelected) R.string.smart_detail_unselect_all else R.string.smart_detail_select_all),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    stringResource(R.string.smart_preview_tap_hint),
                                    color = Color(0xFFB8C8E2),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }

                if (displayItems.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.smart_detail_empty), color = SmartTextSecondary)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        gridItems(displayItems, key = CleanableItem::id) { item ->
                            CategoryGridItem(
                                item = item,
                                accent = accent,
                                onPreview = { onPreview(item) },
                                onToggle = { onToggleItem(item.id) },
                            )
                        }
                    }
                }

                Surface(
                    color = Color(0xFF06132F),
                    shadowElevation = 12.dp,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.smart_detail_selected_label),
                                color = SmartTextSecondary,
                                fontSize = 11.sp,
                            )
                            Text(
                                "$selectedCount • ${ByteFormatter.format(selectedBytes)}",
                                color = SmartGreen,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                            )
                        }
                        Button(onClick = onDismiss) {
                            Text(stringResource(R.string.done))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryGridItem(
    item: CleanableItem,
    accent: Color,
    onPreview: () -> Unit,
    onToggle: () -> Unit,
) {
    val context = LocalContext.current
    val preview = item.toSmartPreview()
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = item.uri, key2 = item.mimeType) {
        value = loadPreviewThumbnail(context, preview)
    }

    Card(
        modifier = Modifier.fillMaxWidth().height(214.dp),
        onClick = onPreview,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SmartCard),
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp)
                    .background(Color(0xFF091A3B)),
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = requireNotNull(bitmap),
                        contentDescription = item.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color(0x5503091B)),
                                ),
                            ),
                    )
                } else {
                    Surface(
                        color = accent.copy(alpha = .16f),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Icon(
                            fileVisual(item.mimeType, item.name),
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.padding(18.dp).size(38.dp),
                        )
                    }
                }

                if (item.mimeType.startsWith("video/")) {
                    Surface(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color(0xCC06132F),
                        shape = CircleShape,
                    ) {
                        Text(
                            "▶",
                            color = Color.White,
                            fontSize = 20.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }

                Checkbox(
                    checked = item.selected,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    colors = CheckboxDefaults.colors(
                        checkedColor = ElectricBlue,
                        uncheckedColor = Color.White,
                        checkmarkColor = Color.White,
                    ),
                )

                Surface(
                    modifier = Modifier.align(Alignment.BottomStart).padding(7.dp),
                    color = Color(0xD9030B20),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        ByteFormatter.format(item.sizeBytes),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    item.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                )
                Text(
                    compactRelativePath(item.relativePath),
                    color = Color(0xFF9FB4D7),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 10.sp,
                )
                Text(
                    stringResource(item.assessment.reasonRes, *item.assessment.reasonArgs.toTypedArray()),
                    color = Color(0xFFBFD0EA),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 9.sp,
                )
                Text(
                    stringResource(if (item.selected) R.string.smart_included_in_cleanup else R.string.smart_not_included_in_cleanup),
                    color = if (item.selected) SmartGreen else Color(0xFFFFB45B),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun StorageDetailDialog(
    type: StorageFileType,
    files: List<IndexedFile>,
    onDismiss: () -> Unit,
    onPreview: (IndexedFile) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            shape = RoundedCornerShape(26.dp),
            color = Color(0xFF07152B),
        ) {
            Column(Modifier.fillMaxSize()) {
                DialogHeader(title = stringResource(type.titleRes), onBack = onDismiss)
                Text(
                    stringResource(R.string.smart_storage_preview_note, files.size),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFA9B9D5),
                )
                if (files.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.smart_storage_preview_empty), color = Color(0xFFA9B9D5))
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(files, key = IndexedFile::uri) { file ->
                            Card(
                                onClick = { onPreview(file) },
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D2147)),
                            ) {
                                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(fileVisual(file.mimeType, file.name), contentDescription = null, tint = storageAccent(type), modifier = Modifier.size(28.dp))
                                    Spacer(Modifier.width(11.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(file.name, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(file.relativePath, style = MaterialTheme.typography.bodySmall, color = Color(0xFFA9B9D5), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text(ByteFormatter.format(file.sizeBytes), color = Color.White, fontWeight = FontWeight.Bold)
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
private fun DialogHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
        }
        Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun FilePreviewDialog(
    file: SmartPreviewFile,
    selected: Boolean?,
    reason: String?,
    onToggleSelection: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SmartBackground,
        ) {
            Column(Modifier.fillMaxSize()) {
                DialogHeader(title = file.name, onBack = onDismiss)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 14.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFF06132F)),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        file.mimeType.startsWith("image/") -> ImageFilePreview(file)
                        file.mimeType.startsWith("video/") -> VideoFilePreview(file)
                        file.mimeType.startsWith("audio/") -> AudioFilePreview(file)
                        else -> GenericFilePreview(file)
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = SmartCard),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            file.name,
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 17.sp,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PreviewInfoPill(
                                text = ByteFormatter.format(file.sizeBytes),
                                accent = SmartCyan,
                                modifier = Modifier.weight(1f),
                            )
                            PreviewInfoPill(
                                text = previewKindLabel(file),
                                accent = Color(0xFF9A63FF),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Text(
                            file.relativePath.ifBlank { stringResource(R.string.smart_path_unknown) },
                            color = Color(0xFFA9B9D5),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )

                        if (!reason.isNullOrBlank()) {
                            Surface(
                                color = Color(0xFF102850),
                                shape = RoundedCornerShape(14.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, SmartCyan.copy(alpha = .26f)),
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Text(
                                        stringResource(R.string.smart_reason_title),
                                        color = SmartCyan,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp,
                                    )
                                    Text(
                                        reason,
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }

                        if (selected != null && onToggleSelection != null) {
                            val selectionColor = if (selected) Color(0xFF0F4938) else Color(0xFF152C52)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(selectionColor)
                                    .border(
                                        width = 1.dp,
                                        color = if (selected) SmartGreen.copy(alpha = .72f) else Color(0xFF6B85AD),
                                        shape = RoundedCornerShape(18.dp),
                                    )
                                    .clickable(onClick = onToggleSelection)
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = selected,
                                    onCheckedChange = { onToggleSelection() },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = SmartGreen,
                                        uncheckedColor = Color(0xFFB7C6DE),
                                        checkmarkColor = Color(0xFF03120D),
                                    ),
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        stringResource(if (selected) R.string.smart_included_in_cleanup else R.string.smart_not_included_in_cleanup),
                                        color = Color.White,
                                        fontWeight = FontWeight.Black,
                                    )
                                    Text(
                                        stringResource(if (selected) R.string.smart_selection_remove_hint else R.string.smart_selection_add_hint),
                                        color = Color(0xFFC0CEE4),
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
}

@Composable
private fun PreviewInfoPill(
    text: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = accent.copy(alpha = .14f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = .36f)),
    ) {
        Text(
            text,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ImageFilePreview(file: SmartPreviewFile) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = file.uri) {
        value = loadDisplayBitmap(context, file)
    }
    if (bitmap == null) {
        CircularProgressIndicator(color = Color.White)
    } else {
        Image(
            bitmap = requireNotNull(bitmap),
            contentDescription = file.name,
            modifier = Modifier.fillMaxSize().padding(8.dp),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun VideoFilePreview(file: SmartPreviewFile) {
    val context = LocalContext.current
    val player = remember(file.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(file.uri))
            prepare()
        }
    }
    var prepared by remember(file.uri) { mutableStateOf(false) }
    var playing by remember(file.uri) { mutableStateOf(false) }
    var failed by remember(file.uri) { mutableStateOf(false) }
    var noVideoTrack by remember(file.uri) { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    prepared = true
                    failed = false
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onTracksChanged(tracks: Tracks) {
                if (tracks.groups.isEmpty()) return
                val selectedVideo = tracks.groups.any { group ->
                    group.type == C.TRACK_TYPE_VIDEO && group.isSelected
                }
                noVideoTrack = !selectedVideo
                if (noVideoTrack) {
                    playing = false
                    player.pause()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                failed = true
                prepared = false
                playing = false
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                (LayoutInflater.from(viewContext)
                    .inflate(R.layout.smart_video_player, null, false) as PlayerView).apply {
                    this.player = player
                    useController = false
                }
            },
            update = { view ->
                if (view.player !== player) view.player = player
            },
        )

        if (!prepared && !failed) {
            CircularProgressIndicator(color = SmartCyan)
        }

        if (failed || noVideoTrack) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Outlined.VideoFile,
                    contentDescription = null,
                    tint = Color(0xFFFFB45B),
                    modifier = Modifier.size(56.dp),
                )
                Text(
                    stringResource(if (noVideoTrack) R.string.smart_video_no_picture else R.string.smart_video_preview_error),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
        } else {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(18.dp),
                color = Color(0xE606132F),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .24f)),
            ) {
                Row(
                    modifier = Modifier
                        .clickable(enabled = prepared && !noVideoTrack) {
                            if (player.isPlaying) {
                                player.pause()
                            } else {
                                player.play()
                            }
                        }
                        .padding(horizontal = 18.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Text(if (playing) "Ⅱ" else "▶", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Text(
                        stringResource(if (playing) R.string.smart_video_pause else R.string.smart_video_play),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioFilePreview(file: SmartPreviewFile) {
    val context = LocalContext.current
    var player by remember(file.uri) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(file.uri) { mutableStateOf(false) }

    DisposableEffect(file.uri) {
        onDispose {
            runCatching { player?.stop() }
            runCatching { player?.release() }
            player = null
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = Color(0xFF65E8FF), modifier = Modifier.size(82.dp))
        Text(file.name, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Button(onClick = {
            var active = player
            if (active == null) {
                active = runCatching { MediaPlayer.create(context, Uri.parse(file.uri)) }.getOrNull()
                player = active
                active?.setOnCompletionListener { playing = false }
            }
            active?.let { media ->
                if (media.isPlaying) {
                    media.pause()
                    playing = false
                } else {
                    media.start()
                    playing = true
                }
            }
        }) {
            Icon(if (playing) Icons.Outlined.MusicNote else Icons.Outlined.PlayCircle, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (playing) R.string.smart_audio_pause else R.string.smart_audio_play))
        }
    }
}

@Composable
private fun GenericFilePreview(file: SmartPreviewFile) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(fileVisual(file.mimeType, file.name), contentDescription = null, tint = Color(0xFF65E8FF), modifier = Modifier.size(86.dp))
        Text(stringResource(R.string.smart_generic_preview_title), color = Color.White, fontWeight = FontWeight.Black)
        Text(file.name, color = Color.White.copy(alpha = .82f), maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text(ByteFormatter.format(file.sizeBytes), color = Color(0xFFA2FFD0), fontWeight = FontWeight.Bold)
    }
}

private suspend fun loadDisplayBitmap(
    context: Context,
    file: SmartPreviewFile,
    maxSide: Int = 1600,
): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val uri = Uri.parse(file.uri)
        if (uri.scheme == "content") {
            return@runCatching context.contentResolver.loadThumbnail(uri, Size(maxSide, maxSide), null).asImageBitmap()
        }
        loadSampledBitmap(file.uri)
    }.getOrNull()
}

private suspend fun loadSampledBitmap(uriString: String): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val uri = Uri.parse(uriString)
        val path = uri.path ?: return@runCatching null
        val file = File(path)
        if (!file.isFile) return@runCatching null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        val largest = max(bounds.outWidth, bounds.outHeight)
        while (largest / sample > 1600) sample *= 2

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(file.absolutePath, options)?.asImageBitmap()
    }.getOrNull()
}

private fun compactRelativePath(path: String): String {
    val clean = path.trim().trim('/')
    if (clean.isBlank()) return "—"
    val parts = clean.split('/').filter(String::isNotBlank)
    return when {
        parts.size <= 2 -> parts.joinToString("/")
        else -> parts.takeLast(2).joinToString("/")
    }
}

@Composable
private fun previewKindLabel(file: SmartPreviewFile): String {
    val extension = file.name.substringAfterLast('.', "").lowercase()
    return when {
        file.mimeType.startsWith("image/") -> stringResource(R.string.smart_file_type_image)
        file.mimeType.startsWith("video/") -> stringResource(R.string.smart_file_type_video)
        file.mimeType.startsWith("audio/") -> stringResource(R.string.smart_file_type_audio)
        extension == "apk" -> stringResource(R.string.smart_file_type_apk)
        extension in setOf("zip", "rar", "7z", "tar", "gz") -> stringResource(R.string.smart_file_type_archive)
        file.mimeType.startsWith("text/") || extension in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt") ->
            stringResource(R.string.smart_file_type_document)
        else -> stringResource(R.string.smart_file_type_other)
    }
}

private fun categoryAccent(category: CleanCategory): Color = when (category) {
    CleanCategory.JUNK -> Color(0xFF0B84FF)
    CleanCategory.DUPLICATE -> Color(0xFF8A48F5)
    CleanCategory.SCREENSHOT -> Color(0xFFEB4CC4)
    CleanCategory.LARGE_FILE -> Color(0xFFFFA31A)
    CleanCategory.OLD_DOWNLOAD -> Color(0xFF16A8D8)
    CleanCategory.APK_PACKAGE -> Color(0xFF5ECA45)
    CleanCategory.APP_CACHE -> Color(0xFF15B9C8)
    CleanCategory.WHATSAPP_MEDIA -> Color(0xFF19B968)
}

private fun categoryVisual(category: CleanCategory): ImageVector = when (category) {
    CleanCategory.JUNK -> Icons.Outlined.DeleteSweep
    CleanCategory.DUPLICATE -> Icons.Outlined.ContentCopy
    CleanCategory.SCREENSHOT -> Icons.Outlined.PhotoLibrary
    CleanCategory.LARGE_FILE -> Icons.Outlined.VideoFile
    CleanCategory.OLD_DOWNLOAD -> Icons.Outlined.Folder
    CleanCategory.APK_PACKAGE -> Icons.Outlined.Android
    CleanCategory.APP_CACHE -> Icons.Outlined.CleaningServices
    CleanCategory.WHATSAPP_MEDIA -> Icons.Outlined.Chat
}

private fun storageAccent(type: StorageFileType): Color = when (type) {
    StorageFileType.IMAGES -> Color(0xFFDA4DD7)
    StorageFileType.VIDEOS -> Color(0xFF2078F5)
    StorageFileType.AUDIO -> Color(0xFF16B7B7)
    StorageFileType.DOCUMENTS -> Color(0xFF7B55E8)
    StorageFileType.ARCHIVES -> Color(0xFFE78B19)
    StorageFileType.APK -> Color(0xFF5BBE35)
    StorageFileType.OTHER -> Color(0xFF72809B)
}

private fun storageTypeVisual(type: StorageFileType): ImageVector = when (type) {
    StorageFileType.IMAGES -> Icons.Outlined.PhotoLibrary
    StorageFileType.VIDEOS -> Icons.Outlined.VideoFile
    StorageFileType.AUDIO -> Icons.Outlined.MusicNote
    StorageFileType.DOCUMENTS -> Icons.Outlined.Description
    StorageFileType.ARCHIVES -> Icons.Outlined.Folder
    StorageFileType.APK -> Icons.Outlined.Android
    StorageFileType.OTHER -> Icons.Outlined.InsertDriveFile
}

private fun fileVisual(mimeType: String, name: String): ImageVector {
    val extension = name.substringAfterLast('.', "").lowercase()
    return when {
        mimeType.startsWith("image/") -> Icons.Outlined.PhotoLibrary
        mimeType.startsWith("video/") -> Icons.Outlined.VideoFile
        mimeType.startsWith("audio/") -> Icons.Outlined.MusicNote
        extension == "apk" -> Icons.Outlined.Android
        extension in setOf("zip", "rar", "7z", "tar", "gz") -> Icons.Outlined.Folder
        mimeType.startsWith("text/") || extension in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt") -> Icons.Outlined.Description
        else -> Icons.Outlined.InsertDriveFile
    }
}
