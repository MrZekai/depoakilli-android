package com.mrzekai.depoakilli.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.ads.AdPolicy
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.model.IndexedFile
import com.mrzekai.depoakilli.model.StorageFileType
import com.mrzekai.depoakilli.model.StorageTypeStat
import com.mrzekai.depoakilli.ui.theme.AdLabel
import com.mrzekai.depoakilli.ui.theme.Black
import com.mrzekai.depoakilli.ui.theme.BlackChip
import com.mrzekai.depoakilli.ui.theme.Ink
import com.mrzekai.depoakilli.ui.theme.InkSoft
import com.mrzekai.depoakilli.ui.theme.Lime
import com.mrzekai.depoakilli.ui.theme.OnBlack
import com.mrzekai.depoakilli.ui.theme.OnBlackSoft
import kotlin.math.roundToInt

// Faz 5: Lime page and black cards; the per-type chart colours below stay.
private val AnalyzerBackground = Lime
private val AnalyzerCard = Black
private val AnalyzerCardAlt = BlackChip
private val AnalyzerTextSecondary = OnBlackSoft
private val AnalyzerCyan = Lime
private val AnalyzerGreen = Lime
private val AnalyzerAmber = AdLabel

@Composable
internal fun StorageAnalyzerScreen(
    state: CleanerUiState,
    onRequestAllFilesAccess: () -> Unit,
    onScan: () -> Unit,
    onReviewType: (StorageFileType) -> Unit,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit = onScan,
    listAd: @Composable () -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = AnalyzerBackground,
        contentColor = Ink,
    ) {
        when {
            !state.hasAllFilesAccess -> AnalyzerAccessRequired(
                onRequest = onRequestAllFilesAccess,
            )

            state.scanning -> AnalyzerScanning(state)

            !state.lastScanCompleted -> AnalyzerEmpty(
                onScan = onScan,
            )

            else -> AnalyzerResults(
                state = state,
                onScan = onRefresh,
                onReviewType = onReviewType,
                listAd = listAd,
            )
        }
    }
}

@Composable
private fun AnalyzerAccessRequired(
    onRequest: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = AnalyzerCard),
            shape = RoundedCornerShape(28.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    color = AnalyzerCyan.copy(alpha = .14f),
                    shape = CircleShape,
                ) {
                    Icon(
                        Icons.Outlined.Storage,
                        contentDescription = null,
                        tint = AnalyzerCyan,
                        modifier = Modifier
                            .padding(18.dp)
                            .size(44.dp),
                    )
                }

                Text(
                    text = stringResource(R.string.all_files_access_title),
                    color = OnBlack,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                )

                Text(
                    text = stringResource(R.string.all_files_access_clean_screen),
                    color = AnalyzerTextSecondary,
                )

                Button(
                    onClick = onRequest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.grant_access))
                }
            }
        }
    }
}

@Composable
private fun AnalyzerScanning(
    state: CleanerUiState,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = AnalyzerCard),
            shape = RoundedCornerShape(28.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(84.dp),
                        color = Lime,
                        trackColor = BlackChip,
                        strokeWidth = 7.dp,
                    )
                    Icon(
                        Icons.Outlined.Storage,
                        contentDescription = null,
                        tint = AnalyzerGreen,
                        modifier = Modifier.size(32.dp),
                    )
                }

                Spacer(Modifier.width(16.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = stringResource(R.string.storage_analyzer_title),
                        color = OnBlack,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = stringResource(R.string.storage_analyzer_scanning_subtitle),
                        color = OnBlack.copy(alpha = .84f),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AnalyzerMetric(
                value = state.scanProgressFiles.toString(),
                label = stringResource(R.string.smart_scan_files_label),
                icon = Icons.Outlined.InsertDriveFile,
                modifier = Modifier.weight(1f),
            )
            AnalyzerMetric(
                value = state.scanProgressDirectories.toString(),
                label = stringResource(R.string.smart_scan_folders_label),
                icon = Icons.Outlined.Folder,
                modifier = Modifier.weight(1f),
            )
        }

        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = Ink,
            trackColor = InkSoft.copy(alpha = .25f),
        )
    }
}

@Composable
private fun AnalyzerEmpty(
    onScan: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = AnalyzerCard),
            shape = RoundedCornerShape(28.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    color = AnalyzerCyan.copy(alpha = .14f),
                    shape = CircleShape,
                ) {
                    Icon(
                        Icons.Outlined.Storage,
                        contentDescription = null,
                        tint = AnalyzerCyan,
                        modifier = Modifier
                            .padding(20.dp)
                            .size(48.dp),
                    )
                }

                Text(
                    text = stringResource(R.string.storage_analyzer_title),
                    color = OnBlack,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                )

                Text(
                    text = stringResource(R.string.storage_analyzer_empty_subtitle),
                    color = AnalyzerTextSecondary,
                )

                Button(
                    onClick = onScan,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.start_scan))
                }
            }
        }
    }
}

@Composable
private fun AnalyzerResults(
    state: CleanerUiState,
    onScan: () -> Unit,
    onReviewType: (StorageFileType) -> Unit,
    listAd: @Composable () -> Unit = {},
) {
    val summary = state.summary
    val totalIndexedBytes = summary.scannedBytes.coerceAtLeast(0L)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 18.dp,
            end = 18.dp,
            top = 16.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        item {
            AnalyzerHero(
                scannedFileCount = summary.scannedFileCount,
                scannedBytes = totalIndexedBytes,
                freeBytes = state.storage.availableBytes,
            )
        }

        item {
            Text(
                text = stringResource(R.string.storage_analyzer_types_title),
                color = Ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
            )
        }

        if (summary.storageTypes.isEmpty()) {
            item {
                Surface(
                    color = AnalyzerCard,
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Text(
                        text = stringResource(R.string.storage_analyzer_no_files),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        color = AnalyzerTextSecondary,
                    )
                }
            }
        } else {
            // B7: one list native after the 2nd type card, in lists of 6 cards or more.
            val showAd = summary.storageTypes.size >= AdPolicy.NATIVE_LIST_MIN_ROWS
            summary.storageTypes.forEachIndexed { index, stat ->
                item(key = stat.type.name, contentType = "storage_analyzer_type") {
                    AnalyzerTypeCard(
                        stat = stat,
                        totalIndexedBytes = totalIndexedBytes,
                        previews = summary.storagePreviews[stat.type].orEmpty().take(3),
                        onReview = { onReviewType(stat.type) },
                    )
                }
                if (showAd && index == AdPolicy.NATIVE_GROUP_LIST_AFTER_CARD - 1) {
                    item(key = "analyzer-list-native") { listAd() }
                }
            }
        }

        if (summary.scanLimitReached) {
            item {
                Text(
                    text = stringResource(R.string.scan_limit_note_v050),
                    color = Ink,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        item {
            Surface(
                color = Black,
                shape = RoundedCornerShape(18.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Icons.Outlined.Security,
                        contentDescription = null,
                        tint = AnalyzerGreen,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        text = stringResource(R.string.storage_analyzer_truth_note),
                        color = OnBlackSoft,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
        }

        item {
            TextButton(
                onClick = onScan,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = Ink),
            ) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = null,
                )
                Spacer(Modifier.width(7.dp))
                Text(stringResource(R.string.cache_refresh))
            }
        }
    }
}

@Composable
private fun AnalyzerHero(
    scannedFileCount: Int,
    scannedBytes: Long,
    freeBytes: Long,
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = AnalyzerCard),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.storage_analyzer_indexed_label),
                color = OnBlack.copy(alpha = .78f),
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = ByteFormatter.format(scannedBytes),
                color = Lime,
                fontSize = 36.sp,
                lineHeight = 39.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = stringResource(
                    R.string.storage_analyzer_indexed_summary,
                    scannedFileCount,
                    ByteFormatter.format(freeBytes),
                ),
                color = OnBlack.copy(alpha = .86f),
            )
        }
    }
}

@Composable
private fun AnalyzerMetric(
    value: String,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.height(88.dp),
        colors = CardDefaults.cardColors(containerColor = AnalyzerCard),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = AnalyzerCyan.copy(alpha = .13f),
                shape = RoundedCornerShape(13.dp),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = AnalyzerCyan,
                    modifier = Modifier
                        .padding(9.dp)
                        .size(22.dp),
                )
            }

            Spacer(Modifier.width(9.dp))

            Column {
                Text(
                    text = value,
                    color = OnBlack,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = label,
                    color = AnalyzerTextSecondary,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun AnalyzerTypeCard(
    stat: StorageTypeStat,
    totalIndexedBytes: Long,
    previews: List<IndexedFile>,
    onReview: () -> Unit,
) {
    val accent = analyzerAccent(stat.type)
    val percent = if (totalIndexedBytes > 0L) {
        ((stat.totalBytes.toDouble() / totalIndexedBytes.toDouble()) * 100.0)
            .coerceIn(0.0, 100.0)
            .roundToInt()
    } else {
        0
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = AnalyzerCard),
        shape = RoundedCornerShape(21.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = accent.copy(alpha = .14f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(
                        analyzerIcon(stat.type),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier
                            .padding(10.dp)
                            .size(25.dp),
                    )
                }

                Spacer(Modifier.width(11.dp))

                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(stat.type.titleRes),
                        color = OnBlack,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = pluralStringResource(
                            R.plurals.storage_analyzer_type_summary,
                            stat.fileCount,
                            stat.fileCount,
                            ByteFormatter.format(stat.totalBytes),
                            percent,
                        ),
                        color = AnalyzerTextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Text(
                    text = ByteFormatter.format(stat.totalBytes),
                    color = accent,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
            }

            if (previews.isNotEmpty()) {
                Surface(
                    color = AnalyzerCardAlt,
                    shape = RoundedCornerShape(15.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(11.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.storage_analyzer_largest_examples),
                            color = accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )

                        previews.forEach { file ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = file.name,
                                    modifier = Modifier.weight(1f),
                                    color = OnBlack,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = ByteFormatter.format(file.sizeBytes),
                                    color = AnalyzerTextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }

            TextButton(
                onClick = onReview,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.storage_analyzer_review_action),
                    color = accent,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

private fun analyzerIcon(type: StorageFileType): ImageVector = when (type) {
    StorageFileType.IMAGES -> Icons.Outlined.PhotoLibrary
    StorageFileType.VIDEOS -> Icons.Outlined.VideoFile
    StorageFileType.AUDIO -> Icons.Outlined.MusicNote
    StorageFileType.DOCUMENTS -> Icons.Outlined.Description
    StorageFileType.ARCHIVES -> Icons.Outlined.Folder
    StorageFileType.APK -> Icons.Outlined.Android
    StorageFileType.OTHER -> Icons.Outlined.InsertDriveFile
}

private fun analyzerAccent(type: StorageFileType): Color = when (type) {
    StorageFileType.IMAGES -> Color(0xFF20C8FF)
    StorageFileType.VIDEOS -> Color(0xFF9A63FF)
    StorageFileType.AUDIO -> Color(0xFF22D0A0)
    StorageFileType.DOCUMENTS -> Color(0xFFFFB548)
    StorageFileType.ARCHIVES -> Color(0xFFFF875B)
    StorageFileType.APK -> Color(0xFF65D95A)
    StorageFileType.OTHER -> Color(0xFF8CA1C6)
}
