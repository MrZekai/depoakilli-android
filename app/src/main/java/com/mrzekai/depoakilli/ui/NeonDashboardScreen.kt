package com.mrzekai.depoakilli.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.model.CleanCategory
import com.mrzekai.depoakilli.model.ScanFocus
import com.mrzekai.depoakilli.model.StorageSnapshot
import com.mrzekai.depoakilli.ui.theme.Amber400
import com.mrzekai.depoakilli.ui.theme.ElectricBlue
import com.mrzekai.depoakilli.ui.theme.Lime400
import com.mrzekai.depoakilli.ui.theme.Purple500
import com.mrzekai.depoakilli.ui.theme.Rose500
import com.mrzekai.depoakilli.ui.theme.WhatsAppGreen
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

private data class DashboardFinding(
    val title: String,
    val bytes: Long,
    val count: Int,
    val icon: ImageVector,
    val accent: Color,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NeonDashboardScreen(
    state: CleanerUiState,
    onSmartClean: () -> Unit,
    onOpenWhatsApp: () -> Unit,
    onDuplicates: () -> Unit,
    onLargeFiles: () -> Unit,
    onApks: () -> Unit,
    onMedia: () -> Unit,
    onDownloads: () -> Unit,
    onJunk: () -> Unit,
    onDeepClean: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val opportunityBytes = (state.dashboardCleanableBytes + state.dashboardReviewBytes).coerceAtLeast(0L)
    val hasSnapshot = state.dashboardSnapshotAtMillis > 0L
    val liveComprehensiveCounts =
        if (
            state.lastScanCompleted &&
            state.scanFocus in setOf(ScanFocus.SMART, ScanFocus.DEEP)
        ) {
            state.summary.byCategory.mapValues { (_, items) -> items.size }
        } else {
            emptyMap()
        }

    val findings = listOf(
        DashboardFinding(
            title = stringResource(R.string.category_large_files),
            bytes = state.dashboardCategoryBytes[CleanCategory.LARGE_FILE] ?: 0L,
            count = liveComprehensiveCounts[CleanCategory.LARGE_FILE] ?: 0,
            icon = Icons.Outlined.VideoFile,
            accent = Amber400,
            onClick = onLargeFiles,
        ),
        DashboardFinding(
            title = stringResource(R.string.category_apk_packages),
            bytes = state.dashboardCategoryBytes[CleanCategory.APK_PACKAGE] ?: 0L,
            count = liveComprehensiveCounts[CleanCategory.APK_PACKAGE] ?: 0,
            icon = Icons.Outlined.Android,
            accent = Lime400,
            onClick = onApks,
        ),
        DashboardFinding(
            title = stringResource(R.string.category_whatsapp),
            bytes = state.dashboardCategoryBytes[CleanCategory.WHATSAPP_MEDIA] ?: 0L,
            count = liveComprehensiveCounts[CleanCategory.WHATSAPP_MEDIA] ?: 0,
            icon = Icons.Outlined.Chat,
            accent = WhatsAppGreen,
            onClick = onOpenWhatsApp,
        ),
        DashboardFinding(
            title = stringResource(R.string.category_duplicates),
            bytes = state.dashboardCategoryBytes[CleanCategory.DUPLICATE] ?: 0L,
            count = liveComprehensiveCounts[CleanCategory.DUPLICATE] ?: 0,
            icon = Icons.Outlined.ContentCopy,
            accent = Purple500,
            onClick = onDuplicates,
        ),
        DashboardFinding(
            title = stringResource(R.string.category_old_downloads),
            bytes = state.dashboardCategoryBytes[CleanCategory.OLD_DOWNLOAD] ?: 0L,
            count = liveComprehensiveCounts[CleanCategory.OLD_DOWNLOAD] ?: 0,
            icon = Icons.Outlined.Download,
            accent = Color(0xFFD59635),
            onClick = onDownloads,
        ),
        DashboardFinding(
            title = stringResource(R.string.category_screenshots),
            bytes = state.dashboardCategoryBytes[CleanCategory.SCREENSHOT] ?: 0L,
            count = liveComprehensiveCounts[CleanCategory.SCREENSHOT] ?: 0,
            icon = Icons.Outlined.PhotoLibrary,
            accent = ElectricBlue,
            onClick = onMedia,
        ),
        DashboardFinding(
            title = stringResource(R.string.category_junk),
            bytes = state.dashboardCategoryBytes[CleanCategory.JUNK] ?: 0L,
            count = liveComprehensiveCounts[CleanCategory.JUNK] ?: 0,
            icon = Icons.Outlined.DeleteSweep,
            accent = Rose500,
            onClick = onJunk,
        ),
    )
        .filter { it.bytes > 0L }
        .sortedByDescending(DashboardFinding::bytes)

    PullToRefreshBox(
        isRefreshing = state.dashboardRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF071A5C),
                            Color(0xFF06143A),
                            Color(0xFF030A1B),
                        ),
                    ),
                ),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                contentPadding = PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = 14.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { DashboardBrandHeader() }
                item {
                    DashboardStatusCard(
                        storage = state.storage,
                        opportunityBytes = opportunityBytes,
                        safeBytes = state.dashboardCleanableBytes,
                        reviewBytes = state.dashboardReviewBytes,
                        snapshotAtMillis = state.dashboardSnapshotAtMillis,
                        hasSnapshot = hasSnapshot,
                    )
                }
                if (!state.hasAllFilesAccess) {
                    item { DashboardAccessStrip(onClick = onSmartClean) }
                }
                item {
                    DashboardPrimaryAction(
                        hasSnapshot = hasSnapshot,
                        scanning = state.scanning || state.dashboardRefreshing,
                        onClick = onSmartClean,
                    )
                }
                item {
                    Text(
                        text = stringResource(R.string.smart_clean_suggestions_title),
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                    )
                }
                if (findings.isEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color.White.copy(alpha = .06f),
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(17.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Text(
                                    if (hasSnapshot) {
                                        stringResource(R.string.dashboard_no_current_opportunity)
                                    } else {
                                        stringResource(R.string.dashboard_not_analyzed)
                                    },
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    if (hasSnapshot) {
                                        stringResource(R.string.dashboard_no_risky_auto_selection)
                                    } else {
                                        stringResource(R.string.dashboard_not_analyzed_hint)
                                    },
                                    color = Color(0xFFB8C3DD),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                } else {
                    findings.forEach { finding ->
                        item(key = finding.title) {
                            DashboardFindingRow(finding)
                        }
                    }
                }
                item { DashboardDeepCleanRow(onClick = onDeepClean) }
            }
        }
    }
}

@Composable
private fun DashboardBrandHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                color = Color.White,
                fontSize = 27.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = stringResource(R.string.app_tagline),
                color = Color(0xFF8EE8D0),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Surface(
            color = Color(0xFF0A765E).copy(alpha = .28f),
            shape = CircleShape,
        ) {
            Icon(
                Icons.Outlined.Security,
                contentDescription = null,
                tint = Color(0xFF66F0C1),
                modifier = Modifier.padding(11.dp).size(25.dp),
            )
        }
    }
}

@Composable
private fun DashboardStatusCard(
    storage: StorageSnapshot,
    opportunityBytes: Long,
    safeBytes: Long,
    reviewBytes: Long,
    snapshotAtMillis: Long,
    hasSnapshot: Boolean,
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF153FBE),
                            Color(0xFF252071),
                            Color(0xFF144C51),
                        ),
                    ),
                )
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    stringResource(R.string.dashboard_cleanup_opportunity),
                    color = Color(0xFFD2DCFF),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (!hasSnapshot) {
                        "—"
                    } else if (opportunityBytes > 0L) {
                        ByteFormatter.format(opportunityBytes)
                    } else {
                        stringResource(R.string.dashboard_no_cleanup_found)
                    },
                    color = Color.White,
                    fontSize = 39.sp,
                    lineHeight = 41.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
                Text(
                    if (hasSnapshot) {
                        stringResource(
                            R.string.dashboard_safe_amount,
                            ByteFormatter.format(safeBytes),
                        )
                    } else {
                        stringResource(R.string.dashboard_not_analyzed_hint)
                    },
                    color = Color(0xFF9DF1C4),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (hasSnapshot) {
                    Text(
                        stringResource(
                            R.string.dashboard_review_amount,
                            ByteFormatter.format(reviewBytes),
                        ),
                        color = Color(0xFFA5D9FF),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        dashboardLastAnalysisLabel(snapshotAtMillis),
                        color = Color(0xFFB8C3DD),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            DashboardStorageGauge(storage)
        }
    }
}

@Composable
private fun DashboardStorageGauge(storage: StorageSnapshot) {
    val hasStorage = storage.totalBytes > 0L
    val freeFraction =
        if (hasStorage) {
            (storage.availableBytes.toDouble() / storage.totalBytes.toDouble())
                .coerceIn(0.0, 1.0)
                .toFloat()
        } else {
            0f
        }
    val usedFraction = if (hasStorage) 1f - freeFraction else 0f
    val usedPercent = (usedFraction * 100f).roundToInt().coerceIn(0, 100)
    val accent = when {
        !hasStorage -> ElectricBlue
        freeFraction < 0.10f -> Rose500
        freeFraction < 0.20f -> Amber400
        else -> Color(0xFF2FE6C3)
    }

    Box(
        modifier = Modifier.size(126.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 12.dp.toPx()
            val ringSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            drawArc(
                color = Color.White.copy(alpha = .12f),
                startAngle = -130f,
                sweepAngle = 260f,
                useCenter = false,
                topLeft = topLeft,
                size = ringSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = accent,
                startAngle = -130f,
                sweepAngle = 260f * usedFraction.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = ringSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (hasStorage) ByteFormatter.format(storage.availableBytes) else "—",
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
            Text(
                stringResource(R.string.dashboard_free_space),
                color = accent,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
            )
            if (hasStorage) {
                Text(
                    stringResource(R.string.dashboard_storage_used_percent, usedPercent),
                    color = Color(0xFFC4CBE0),
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun DashboardAccessStrip(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color(0xFF493515).copy(alpha = .88f),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Security, contentDescription = null, tint = Amber400)
            Spacer(Modifier.size(10.dp))
            Text(
                stringResource(R.string.dashboard_access_hint),
                modifier = Modifier.weight(1f),
                color = Color(0xFFFFD993),
                style = MaterialTheme.typography.bodySmall,
            )
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = Amber400)
        }
    }
}

@Composable
private fun DashboardPrimaryAction(
    hasSnapshot: Boolean,
    scanning: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        enabled = !scanning,
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF1268F4),
                            Color(0xFF10BFD2),
                            Color(0xFF27D772),
                        ),
                    ),
                )
                .padding(horizontal = 18.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(color = Color.White.copy(alpha = .13f), shape = CircleShape) {
                Icon(
                    Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.padding(11.dp).size(28.dp),
                )
            }
            Spacer(Modifier.size(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (scanning) stringResource(R.string.dashboard_scanning)
                    else stringResource(R.string.smart_clean_primary),
                    color = Color.White,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    if (hasSnapshot) {
                        stringResource(R.string.smart_clean_hero_subtitle_v0514)
                    } else {
                        stringResource(R.string.smart_clean_primary_subtitle)
                    },
                    color = Color.White.copy(alpha = .82f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                )
            }
            Icon(
                Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

@Composable
private fun DashboardFindingRow(finding: DashboardFinding) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = finding.onClick),
        color = Color(0xFF0C1834),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = finding.accent.copy(alpha = .14f),
                shape = RoundedCornerShape(15.dp),
            ) {
                Icon(
                    finding.icon,
                    contentDescription = null,
                    tint = finding.accent,
                    modifier = Modifier.padding(10.dp).size(27.dp),
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(finding.title, color = Color.White, fontWeight = FontWeight.Black)
                if (finding.count > 0) {
                    Text(
                        "${finding.count} ${stringResource(R.string.premium_tool_candidates)}",
                        color = Color(0xFFB8C3DD),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Text(
                ByteFormatter.format(finding.bytes),
                color = finding.accent,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.size(8.dp))
            Icon(
                Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = finding.accent,
            )
        }
    }
}

@Composable
private fun DashboardDeepCleanRow(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color(0xFF211646),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier.padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = Purple500.copy(alpha = .18f),
                shape = RoundedCornerShape(15.dp),
            ) {
                Icon(
                    Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFFA98AF8),
                    modifier = Modifier.padding(10.dp).size(28.dp),
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.deep_cleaner_title),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    stringResource(R.string.premium_deep_subtitle),
                    color = Color(0xFFC6B8E8),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = Color(0xFFA98AF8),
            )
        }
    }
}

@Composable
private fun dashboardLastAnalysisLabel(timestampMillis: Long): String {
    if (timestampMillis <= 0L) return stringResource(R.string.dashboard_not_analyzed)
    val ageMillis = (System.currentTimeMillis() - timestampMillis).coerceAtLeast(0L)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ageMillis)
    return when {
        minutes < 1L -> stringResource(R.string.dashboard_last_analysis_now)
        minutes < 60L -> stringResource(R.string.dashboard_last_analysis_minutes, minutes)
        minutes < 24L * 60L -> stringResource(
            R.string.dashboard_last_analysis_hours,
            TimeUnit.MILLISECONDS.toHours(ageMillis),
        )
        else -> stringResource(
            R.string.dashboard_last_analysis_days,
            TimeUnit.MILLISECONDS.toDays(ageMillis),
        )
    }
}
