package com.mrzekai.depoakilli.ui

import android.net.Uri
import android.os.Build
import android.util.Size
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.model.WhatsAppLibrarySummary
import com.mrzekai.depoakilli.model.WhatsAppMediaCategory
import com.mrzekai.depoakilli.model.WhatsAppMediaItem
import com.mrzekai.depoakilli.ui.theme.ElectricBlue
import com.mrzekai.depoakilli.ui.theme.WhatsAppGreen
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun WhatsAppCleanerDetailScreen(
    state: CleanerUiState,
    onRequestAccess: () -> Unit,
    onScan: () -> Unit,
    onToggleItem: (String) -> Unit,
    onToggleCategory: (WhatsAppMediaCategory) -> Unit,
    onDeleteSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        !state.hasWhatsAppAccess -> WhatsAppAccessScreen(
            onRequestAccess = onRequestAccess,
            modifier = modifier,
        )

        state.whatsAppScanning -> WhatsAppScanningScreen(
            progress = state.whatsAppScanProgress,
            modifier = modifier,
        )

        !state.whatsAppLastScanCompleted -> WhatsAppReadyScreen(
            onScan = onScan,
            modifier = modifier,
        )

        else -> WhatsAppResultsScreen(
            summary = state.whatsAppSummary,
            onScan = onScan,
            onToggleItem = onToggleItem,
            onToggleCategory = onToggleCategory,
            onDeleteSelected = onDeleteSelected,
            modifier = modifier,
        )
    }
}

@Composable
private fun WhatsAppAccessScreen(
    onRequestAccess: () -> Unit,
    modifier: Modifier,
) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEAFBF1)),
            shape = RoundedCornerShape(28.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(color = WhatsAppGreen, shape = CircleShape) {
                    Icon(
                        Icons.Outlined.Chat,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(18.dp).size(44.dp),
                    )
                }
                Text(
                    stringResource(R.string.whatsapp_access_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    stringResource(R.string.whatsapp_access_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onRequestAccess, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.whatsapp_choose_folder))
                }
                Text(
                    stringResource(R.string.whatsapp_access_once),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WhatsAppReadyScreen(
    onScan: () -> Unit,
    modifier: Modifier,
) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(color = WhatsAppGreen.copy(alpha = .14f), shape = CircleShape) {
                Icon(
                    Icons.Outlined.Chat,
                    contentDescription = null,
                    tint = WhatsAppGreen,
                    modifier = Modifier.padding(24.dp).size(52.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.whatsapp_ready_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.whatsapp_ready_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(22.dp))
            Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.whatsapp_scan_action))
            }
        }
    }
}

@Composable
private fun WhatsAppScanningScreen(
    progress: Int,
    modifier: Modifier,
) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEAFBF1)),
            shape = RoundedCornerShape(28.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.size(112.dp),
                        color = WhatsAppGreen,
                        trackColor = WhatsAppGreen.copy(alpha = .15f),
                        strokeWidth = 9.dp,
                    )
                    Text(
                        "$progress%",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = WhatsAppGreen,
                    )
                }
                Text(
                    stringResource(R.string.whatsapp_scanning_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    stringResource(R.string.whatsapp_scanning_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color = WhatsAppGreen,
                    trackColor = WhatsAppGreen.copy(alpha = .15f),
                )
            }
        }
    }
}

@Composable
private fun WhatsAppResultsScreen(
    summary: WhatsAppLibrarySummary,
    onScan: () -> Unit,
    onToggleItem: (String) -> Unit,
    onToggleCategory: (WhatsAppMediaCategory) -> Unit,
    onDeleteSelected: () -> Unit,
    modifier: Modifier,
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(R.string.whatsapp_delete_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.whatsapp_delete_confirm_message,
                        summary.selectedItems.size,
                        ByteFormatter.format(summary.selectedBytes),
                    ),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation = false
                        onDeleteSelected()
                    },
                ) {
                    Text(stringResource(R.string.delete_selected))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    Column(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                WhatsAppSummaryCard(summary)
            }
            if (summary.items.isEmpty()) {
                item {
                    Card(shape = RoundedCornerShape(22.dp)) {
                        Column(
                            Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(Icons.Outlined.Info, contentDescription = null, tint = WhatsAppGreen)
                            Spacer(Modifier.height(10.dp))
                            Text(
                                stringResource(R.string.whatsapp_no_files),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
            WhatsAppMediaCategory.entries.forEach { category ->
                val categoryItems = summary.byCategory[category].orEmpty()
                if (categoryItems.isNotEmpty()) {
                    item(key = "header-${category.name}") {
                        WhatsAppCategoryHeader(
                            category = category,
                            items = categoryItems,
                            onToggle = { onToggleCategory(category) },
                        )
                    }
                    items(categoryItems, key = WhatsAppMediaItem::id) { item ->
                        WhatsAppMediaRow(item = item, onToggle = { onToggleItem(item.id) })
                    }
                }
            }
            item {
                TextButton(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.scan_again))
                }
            }
        }
        if (summary.selectedItems.isNotEmpty()) {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp) {
                Button(
                    onClick = { showDeleteConfirmation = true },
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(58.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(
                            R.string.whatsapp_delete_action,
                            ByteFormatter.format(summary.selectedBytes),
                        ),
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
    }
}

@Composable
private fun WhatsAppSummaryCard(summary: WhatsAppLibrarySummary) {
    Card(shape = RoundedCornerShape(26.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF075E54), WhatsAppGreen, Color(0xFF4DDB93)),
                    ),
                )
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                stringResource(R.string.whatsapp_scan_complete),
                color = Color.White.copy(alpha = .82f),
                fontWeight = FontWeight.Bold,
            )
            Text(
                ByteFormatter.format(summary.totalBytes),
                color = Color.White,
                fontSize = 38.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                stringResource(R.string.whatsapp_scan_stats, summary.scannedFileCount),
                color = Color.White.copy(alpha = .9f),
            )
            Text(
                stringResource(R.string.whatsapp_selection_safe),
                color = Color.White.copy(alpha = .78f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun WhatsAppCategoryHeader(
    category: WhatsAppMediaCategory,
    items: List<WhatsAppMediaItem>,
    onToggle: () -> Unit,
) {
    val selectedCount = items.count(WhatsAppMediaItem::selected)
    Card(
        modifier = Modifier.clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEAFBF1)),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(color = WhatsAppGreen, shape = CircleShape) {
                Icon(
                    whatsAppCategoryIcon(category),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.padding(10.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(category.titleRes), fontWeight = FontWeight.Black)
                Text(
                    stringResource(
                        R.string.whatsapp_category_summary,
                        items.size,
                        ByteFormatter.format(items.sumOf(WhatsAppMediaItem::sizeBytes)),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (selectedCount > 0) {
                    Text(
                        stringResource(R.string.whatsapp_selected_count, selectedCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = WhatsAppGreen,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Checkbox(
                checked = items.all(WhatsAppMediaItem::selected),
                onCheckedChange = { onToggle() },
            )
        }
    }
}

@Composable
private fun WhatsAppMediaRow(
    item: WhatsAppMediaItem,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier.clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WhatsAppThumbnail(item)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    ByteFormatter.format(item.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = ElectricBlue,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(item.modifiedAtMillis)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Checkbox(checked = item.selected, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun WhatsAppThumbnail(item: WhatsAppMediaItem) {
    val context = LocalContext.current
    val canShowThumbnail = item.category in setOf(
        WhatsAppMediaCategory.IMAGES,
        WhatsAppMediaCategory.VIDEOS,
        WhatsAppMediaCategory.STICKERS_GIFS,
    )
    val bitmap by produceState<ImageBitmap?>(initialValue = null, item.uri, canShowThumbnail) {
        value = if (canShowThumbnail && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.loadThumbnail(
                        Uri.parse(item.uri),
                        Size(160, 160),
                        null,
                    ).asImageBitmap()
                }.getOrNull()
            }
        } else {
            null
        }
    }
    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(WhatsAppGreen.copy(alpha = .12f)),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = requireNotNull(bitmap),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                whatsAppCategoryIcon(item.category),
                contentDescription = null,
                tint = WhatsAppGreen,
            )
        }
    }
}

private fun whatsAppCategoryIcon(category: WhatsAppMediaCategory): ImageVector = when (category) {
    WhatsAppMediaCategory.IMAGES -> Icons.Outlined.PhotoLibrary
    WhatsAppMediaCategory.VIDEOS -> Icons.Outlined.Movie
    WhatsAppMediaCategory.DOCUMENTS -> Icons.Outlined.Folder
    WhatsAppMediaCategory.AUDIO -> Icons.Outlined.Chat
    WhatsAppMediaCategory.VOICE_NOTES -> Icons.Outlined.Chat
    WhatsAppMediaCategory.STICKERS_GIFS -> Icons.Outlined.PhotoLibrary
    WhatsAppMediaCategory.OTHER -> Icons.Outlined.Folder
}
