package com.mrzekai.depoakilli.ui

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
    Box(modifier.fillMaxSize().padding(22.dp), contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(28.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(Icons.Outlined.Security, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(54.dp))
                Text(stringResource(R.string.all_files_access_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(stringResource(R.string.all_files_access_clean_screen), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            .background(Brush.verticalGradient(listOf(Color(0xFF06152F), Color(0xFF0A2F66), Color(0xFF07152B))))
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(92.dp),
                    strokeWidth = 8.dp,
                    color = Lime400,
                    trackColor = Color.White.copy(alpha = .14f),
                )
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
            }
            Text(
                stringResource(R.string.smart_scan_progress_title),
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
            )
            Text(
                stringResource(R.string.smart_scan_progress_subtitle),
                color = Color.White.copy(alpha = .76f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Surface(color = Color.White.copy(alpha = .10f), shape = RoundedCornerShape(18.dp)) {
                Text(
                    stringResource(R.string.scan_live_counter, state.scanProgressFiles, state.scanProgressDirectories),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(stringResource(R.string.scan_private), color = Color(0xFF83E7FF), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SmartEmptyState(onScan: () -> Unit, modifier: Modifier) {
    Box(modifier.fillMaxSize().padding(22.dp), contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(28.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(56.dp))
                Text(stringResource(R.string.smart_clean_results_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(stringResource(R.string.smart_clean_results_empty_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.start_scan)) }
            }
        }
    }
}

@Composable
private fun SmartResultsContent(
    summary: ScanSummary,
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

    Column(modifier.fillMaxSize().background(Color(0xFFF5F8FE))) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SmartCleanSummaryCard(
                    summary = summary,
                    onCleanClick = { if (summary.selectedItems.isNotEmpty()) confirmCleanup = true },
                )
            }

            item {
                SectionTitle(
                    title = stringResource(R.string.smart_clean_suggestions_title),
                    subtitle = stringResource(R.string.smart_clean_suggestions_subtitle),
                )
                Spacer(Modifier.height(10.dp))
                if (summary.byCategory.isEmpty()) {
                    Card(shape = RoundedCornerShape(20.dp)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Color(0xFF0BA56A))
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.no_safe_suggestions), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        summary.byCategory.forEach { (category, categoryItems) ->
                            item(key = category.name) {
                                SmartCategoryCard(
                                    category = category,
                                    items = categoryItems,
                                    onToggleCategory = { onToggleCategory(category) },
                                    onPreview = { previewItemId = it.id },
                                    onOpenAll = { openCategory = category },
                                    onToggleItem = onToggleItem,
                                )
                            }
                        }
                    }
                }
            }

            item {
                SectionTitle(
                    title = stringResource(R.string.storage_analyzer_title),
                    subtitle = stringResource(R.string.smart_storage_analyzer_subtitle),
                )
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(summary.storageTypes, key = { it.type.name }) { stat ->
                        StorageTypeCard(
                            type = stat.type,
                            count = stat.fileCount,
                            bytes = stat.totalBytes,
                            onClick = { openStorageType = stat.type },
                        )
                    }
                }
            }

            if (summary.scanLimitReached) {
                item {
                    Text(
                        stringResource(R.string.scan_limit_note_v050),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            item {
                OutlinedButton(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.scan_again))
                }
            }
        }

        Surface(shadowElevation = 12.dp, color = Color.White) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.smart_selected_for_cleanup), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(ByteFormatter.format(summary.selectedBytes), fontSize = 24.sp, fontWeight = FontWeight.Black)
                }
                Button(
                    onClick = { confirmCleanup = true },
                    enabled = summary.selectedItems.isNotEmpty(),
                ) {
                    Icon(Icons.Outlined.CleaningServices, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.smart_review_and_clean), fontWeight = FontWeight.Black)
                }
            }
        }
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
                onToggleSelection = { onToggleItem(item.id) },
                onDismiss = { previewItemId = null },
            )
        }
    }

    previewStorageFile?.let { file ->
        FilePreviewDialog(
            file = file.toSmartPreview(),
            selected = null,
            onToggleSelection = null,
            onDismiss = { previewStorageFile = null },
        )
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SmartCleanSummaryCard(summary: ScanSummary, onCleanClick: () -> Unit) {
    Card(
        onClick = onCleanClick,
        enabled = summary.selectedItems.isNotEmpty(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(Color(0xFF071E55), Color(0xFF0873DF), Color(0xFF13B87A))))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Color.White.copy(alpha = .14f), shape = CircleShape) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.padding(10.dp).size(26.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.smart_clean_results_title), color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Text(stringResource(R.string.smart_clean_results_tap_hint), color = Color.White.copy(alpha = .76f), style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = Color.White)
            }
            Text(ByteFormatter.format(summary.selectedBytes), color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Black)
            Text(stringResource(R.string.cleanable_space), color = Color(0xFFA2FFD0), fontWeight = FontWeight.Bold)
            Text(
                stringResource(
                    R.string.smart_clean_summary_detail,
                    ByteFormatter.format(summary.totalSuggestedBytes),
                    summary.selectedItems.size,
                    summary.scannedFileCount,
                ),
                color = Color.White.copy(alpha = .80f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SmartCategoryCard(
    category: CleanCategory,
    items: List<CleanableItem>,
    onToggleCategory: () -> Unit,
    onPreview: (CleanableItem) -> Unit,
    onOpenAll: () -> Unit,
    onToggleItem: (String) -> Unit,
) {
    val allSelected = items.isNotEmpty() && items.all(CleanableItem::selected)
    Card(
        modifier = Modifier.width(300.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = categoryAccent(category).copy(alpha = .13f), shape = RoundedCornerShape(14.dp)) {
                    Icon(categoryVisual(category), contentDescription = null, tint = categoryAccent(category), modifier = Modifier.padding(9.dp).size(24.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(category.titleRes), fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(R.string.category_summary, items.size, ByteFormatter.format(items.sumOf(CleanableItem::sizeBytes))),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Checkbox(checked = allSelected, onCheckedChange = { onToggleCategory() })
            }

            items.take(4).forEach { item ->
                CompactPreviewRow(
                    item = item,
                    onPreview = { onPreview(item) },
                    onToggle = { onToggleItem(item.id) },
                )
            }

            TextButton(onClick = onOpenAll, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.smart_show_all_count, items.size))
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun CompactPreviewRow(item: CleanableItem, onPreview: () -> Unit, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFF4F7FC))
            .clickable(onClick = onPreview)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(fileVisual(item.mimeType, item.name), contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(ByteFormatter.format(item.sizeBytes), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        Checkbox(checked = item.selected, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun StorageTypeCard(type: StorageFileType, count: Int, bytes: Long, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(165.dp).height(126.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = storageAccent(type).copy(alpha = .11f)),
    ) {
        Column(Modifier.fillMaxSize().padding(13.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = storageAccent(type), shape = RoundedCornerShape(12.dp)) {
                    Icon(storageTypeVisual(type), contentDescription = null, tint = Color.White, modifier = Modifier.padding(8.dp).size(22.dp))
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = storageAccent(type), modifier = Modifier.size(18.dp))
            }
            Column {
                Text(stringResource(type.titleRes), fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(ByteFormatter.format(bytes), color = storageAccent(type), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.smart_storage_file_count, count), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
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
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            shape = RoundedCornerShape(26.dp),
            color = Color(0xFFF7F9FD),
        ) {
            Column(Modifier.fillMaxSize()) {
                DialogHeader(title = stringResource(category.titleRes), onBack = onDismiss)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.category_summary, items.size, ByteFormatter.format(items.sumOf(CleanableItem::sizeBytes))), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.smart_preview_tap_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Checkbox(
                        checked = items.isNotEmpty() && items.all(CleanableItem::selected),
                        onCheckedChange = { onToggleCategory() },
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items, key = CleanableItem::id) { item ->
                        DetailedCleanableRow(item = item, onPreview = { onPreview(item) }, onToggle = { onToggleItem(item.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailedCleanableRow(item: CleanableItem, onPreview: () -> Unit, onToggle: () -> Unit) {
    Card(
        onClick = onPreview,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Color(0xFFEAF1FF), shape = RoundedCornerShape(12.dp)) {
                Icon(fileVisual(item.mimeType, item.name), contentDescription = null, tint = ElectricBlue, modifier = Modifier.padding(9.dp).size(25.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.relativePath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(ByteFormatter.format(item.sizeBytes), style = MaterialTheme.typography.labelMedium, color = ElectricBlue)
            }
            Checkbox(checked = item.selected, onCheckedChange = { onToggle() })
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
            color = Color(0xFFF7F9FD),
        ) {
            Column(Modifier.fillMaxSize()) {
                DialogHeader(title = stringResource(type.titleRes), onBack = onDismiss)
                Text(
                    stringResource(R.string.smart_storage_preview_note, files.size),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (files.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.smart_storage_preview_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                            ) {
                                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(fileVisual(file.mimeType, file.name), contentDescription = null, tint = storageAccent(type), modifier = Modifier.size(28.dp))
                                    Spacer(Modifier.width(11.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(file.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(file.relativePath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text(ByteFormatter.format(file.sizeBytes), fontWeight = FontWeight.Bold)
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
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun FilePreviewDialog(
    file: SmartPreviewFile,
    selected: Boolean?,
    onToggleSelection: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFFF7F9FD),
        ) {
            Column(Modifier.fillMaxSize()) {
                DialogHeader(title = file.name, onBack = onDismiss)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 14.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF07152B)),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        file.mimeType.startsWith("image/") -> ImageFilePreview(file)
                        file.mimeType.startsWith("video/") -> VideoFilePreview(file)
                        file.mimeType.startsWith("audio/") -> AudioFilePreview(file)
                        else -> GenericFilePreview(file)
                    }
                }
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(file.name, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(file.relativePath, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(ByteFormatter.format(file.sizeBytes), color = ElectricBlue, fontWeight = FontWeight.Bold)
                    if (selected != null && onToggleSelection != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (selected) Color(0xFFE4F8ED) else Color(0xFFF0F2F7))
                                .clickable(onClick = onToggleSelection)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = selected, onCheckedChange = { onToggleSelection() })
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(if (selected) R.string.smart_included_in_cleanup else R.string.smart_not_included_in_cleanup),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageFilePreview(file: SmartPreviewFile) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = file.uri) {
        value = loadSampledBitmap(file.uri)
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
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            VideoView(context).apply {
                val controller = MediaController(context)
                controller.setAnchorView(this)
                setMediaController(controller)
                setVideoURI(Uri.parse(file.uri))
                setOnPreparedListener { player -> player.isLooping = false }
            }
        },
        update = { view ->
            if (!view.isPlaying) view.seekTo(1)
        },
    )
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
    CleanCategory.WHATSAPP_MEDIA -> Icons.Outlined.Folder
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
