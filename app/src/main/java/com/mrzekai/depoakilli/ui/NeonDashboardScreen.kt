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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.model.CleanCategory
import com.mrzekai.depoakilli.model.StorageSnapshot
import com.mrzekai.depoakilli.ui.theme.Amber400
import com.mrzekai.depoakilli.ui.theme.ElectricBlue
import com.mrzekai.depoakilli.ui.theme.Lime400
import com.mrzekai.depoakilli.ui.theme.Purple500
import com.mrzekai.depoakilli.ui.theme.Rose500
import com.mrzekai.depoakilli.ui.theme.Teal500
import com.mrzekai.depoakilli.ui.theme.WhatsAppGreen
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NeonDashboardScreen(
    state: CleanerUiState,
    onOpenProfile: () -> Unit,
    onSmartClean: () -> Unit,
    onOpenWhatsApp: () -> Unit,
    onDuplicates: () -> Unit,
    onLargeFiles: () -> Unit,
    onApks: () -> Unit,
    onMedia: () -> Unit,
    onDeepClean: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val categoryBytes = state.dashboardCategoryBytes
    val duplicateBytes = categoryBytes[CleanCategory.DUPLICATE] ?: 0L
    val largeBytes = categoryBytes[CleanCategory.LARGE_FILE] ?: 0L
    val apkBytes = categoryBytes[CleanCategory.APK_PACKAGE] ?: 0L
    val mediaBytes = (categoryBytes[CleanCategory.SCREENSHOT] ?: 0L) +
        state.summary.items
            .asSequence()
            .filter { item ->
                item.assessment.category == CleanCategory.LARGE_FILE &&
                    (item.mimeType.startsWith("image/") || item.mimeType.startsWith("video/"))
            }
            .sumOf { it.sizeBytes }
    val whatsAppBytes = categoryBytes[CleanCategory.WHATSAPP_MEDIA] ?: 0L
    val cleanableBytes = state.dashboardCleanableBytes
    val reviewBytes = state.dashboardReviewBytes
    val opportunityBytes = (cleanableBytes + reviewBytes).coerceAtLeast(0L)
    val hasDashboardSnapshot = state.dashboardSnapshotAtMillis > 0L

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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                DashboardHeader(onOpenProfile = onOpenProfile)
            }
            item {
                DashboardHero(
                    cleanableBytes = cleanableBytes,
                    reviewBytes = reviewBytes,
                    snapshotAtMillis = state.dashboardSnapshotAtMillis,
                    scannedFileCount = state.dashboardScannedFileCount,
                    scannedBytes = state.dashboardScannedBytes,
                    storage = state.storage,
                )
            }
            item {
                SmartCleanButton(
                    state = state,
                    opportunityBytes = opportunityBytes,
                    hasDashboardSnapshot = hasDashboardSnapshot,
                    onClick = onSmartClean,
                )
            }
            if (!state.hasAllFilesAccess) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onSmartClean),
                        color = Color(0xFF11245A).copy(alpha = .95f),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Security, contentDescription = null, tint = Amber400)
                            Spacer(Modifier.size(9.dp))
                            Text(
                                stringResource(R.string.dashboard_access_hint),
                                color = Color(0xFFD8E2FF),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DashboardToolTile(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.whatsapp_cleaner_title),
                        amount = ByteFormatter.format(whatsAppBytes),
                        icon = Icons.Outlined.Chat,
                        accent = WhatsAppGreen,
                        badge = whatsAppBytes > 0L,
                        onClick = onOpenWhatsApp,
                    )
                    DashboardToolTile(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.duplicates_tool_title),
                        amount = ByteFormatter.format(duplicateBytes),
                        icon = Icons.Outlined.ContentCopy,
                        accent = Purple500,
                        badge = duplicateBytes > 0L,
                        onClick = onDuplicates,
                    )
                    DashboardToolTile(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.large_files_tool_title),
                        amount = ByteFormatter.format(largeBytes),
                        icon = Icons.Outlined.VideoFile,
                        accent = Amber400,
                        badge = largeBytes > 0L,
                        onClick = onLargeFiles,
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DashboardToolTile(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.apk_packages),
                        amount = ByteFormatter.format(apkBytes),
                        icon = Icons.Outlined.Android,
                        accent = Lime400,
                        badge = apkBytes > 0L,
                        onClick = onApks,
                    )
                    DashboardToolTile(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.media_cleaner_title),
                        amount = ByteFormatter.format(mediaBytes),
                        icon = Icons.Outlined.PhotoLibrary,
                        accent = ElectricBlue,
                        badge = mediaBytes > 0L,
                        onClick = onMedia,
                    )
                    DashboardToolTile(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.deep_cleaner_title),
                        amount = stringResource(R.string.dashboard_deep_detail),
                        icon = Icons.Outlined.AutoAwesome,
                        accent = Purple500,
                        badge = false,
                        onClick = onDeepClean,
                    )
                }
            }
            item {
                DeepCleanPromo(onClick = onDeepClean)
            }
        }
    }
    }
}

@Composable
private fun DashboardHeader(onOpenProfile: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onOpenProfile) {
            Icon(
                Icons.Outlined.Menu,
                contentDescription = stringResource(R.string.tab_profile),
                tint = Color.White,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.app_name).substringBeforeLast(" "),
                color = Color.White,
                fontSize = 25.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = " " + stringResource(R.string.app_name).substringAfterLast(" "),
                color = Color(0xFF40E3CF),
                fontSize = 25.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = " ✦",
                color = Color(0xFF45EBC9),
                fontSize = 21.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Spacer(Modifier.weight(1f))
        Surface(
            color = Color.White.copy(alpha = .08f),
            shape = CircleShape,
        ) {
            Icon(
                Icons.Outlined.Security,
                contentDescription = null,
                tint = Color(0xFF60F0BD),
                modifier = Modifier.padding(10.dp).size(22.dp),
            )
        }
    }
}

@Composable
private fun DashboardHero(
    cleanableBytes: Long,
    reviewBytes: Long,
    snapshotAtMillis: Long,
    scannedFileCount: Int,
    scannedBytes: Long,
    storage: StorageSnapshot,
) {
    val hasSnapshot = snapshotAtMillis > 0L
    val opportunityBytes = (cleanableBytes + reviewBytes).coerceAtLeast(0L)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Card(
            modifier = Modifier.weight(1.45f).height(184.dp),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF1649D8),
                                Color(0xFF242072),
                                Color(0xFF371963),
                            ),
                        ),
                    )
                    .padding(18.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(R.string.dashboard_cleanup_opportunity),
                        color = Color(0xFFC7D5FF),
                        fontSize = 15.sp,
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
                        lineHeight = 42.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                    )
                    Surface(
                        color = Color(0xFF6C49C9).copy(alpha = .45f),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                        ) {
                            if (hasSnapshot) {
                                Text(
                                    if (cleanableBytes > 0L) {
                                        stringResource(
                                            R.string.dashboard_safe_amount,
                                            ByteFormatter.format(cleanableBytes),
                                        )
                                    } else {
                                        stringResource(R.string.dashboard_no_risky_auto_selection)
                                    },
                                    color = Color(0xFFDDF4EA),
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    stringResource(
                                        R.string.dashboard_review_amount,
                                        ByteFormatter.format(reviewBytes),
                                    ),
                                    color = Color(0xFF9FE6FF),
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    stringResource(
                                        R.string.dashboard_scanned_summary,
                                        scannedFileCount,
                                        ByteFormatter.format(scannedBytes),
                                    ),
                                    color = Color(0xFFC9D0F3),
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    dashboardLastAnalysisLabel(snapshotAtMillis),
                                    color = Color(0xFFC9D0F3),
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            } else {
                                Text(
                                    stringResource(R.string.dashboard_not_analyzed),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                )
                                Text(
                                    stringResource(R.string.dashboard_not_analyzed_hint),
                                    color = Color(0xFFC9D0F3),
                                    fontSize = 9.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }

        StorageStatusRing(
            storage = storage,
            modifier = Modifier.weight(.9f),
        )
    }
}

@Composable
private fun StorageStatusRing(
    storage: StorageSnapshot,
    modifier: Modifier = Modifier,
) {
    val hasStorage = storage.totalBytes > 0L
    val freeFraction = if (hasStorage) {
        (storage.availableBytes.toDouble() / storage.totalBytes.toDouble())
            .coerceIn(0.0, 1.0)
            .toFloat()
    } else {
        0f
    }
    val usedPercent = if (hasStorage) {
        ((1f - freeFraction) * 100f).roundToInt().coerceIn(0, 100)
    } else {
        0
    }
    val accent = when {
        !hasStorage -> ElectricBlue
        freeFraction < 0.10f -> Rose500
        freeFraction < 0.20f -> Amber400
        else -> Lime400
    }

    Box(
        modifier = modifier.height(184.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(150.dp)) {
            val stroke = 13.dp.toPx()
            val ringSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            drawArc(
                color = Color.White.copy(alpha = .10f),
                startAngle = -130f,
                sweepAngle = 260f,
                useCenter = false,
                topLeft = topLeft,
                size = ringSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            if (hasStorage) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(
                            ElectricBlue,
                            accent,
                            Color(0xFF16D9E3),
                            ElectricBlue,
                        ),
                    ),
                    startAngle = -130f,
                    sweepAngle = 260f * freeFraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = ringSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                if (hasStorage) ByteFormatter.format(storage.availableBytes) else "—",
                color = Color.White,
                fontSize = 23.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
            Text(
                stringResource(R.string.dashboard_free_space),
                color = accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                if (hasStorage) {
                    stringResource(R.string.dashboard_storage_used_percent, usedPercent)
                } else {
                    stringResource(R.string.dashboard_storage_unknown)
                },
                color = Color(0xFFB8C6E2),
                fontSize = 9.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun dashboardLastAnalysisLabel(snapshotAtMillis: Long): String {
    val ageMillis = (System.currentTimeMillis() - snapshotAtMillis).coerceAtLeast(0L)
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

@Composable
private fun SmartCleanButton(
    state: CleanerUiState,
    opportunityBytes: Long,
    hasDashboardSnapshot: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .background(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color(0xFF0B66FF),
                        Color(0xFF12A8F0),
                        Color(0xFF25DD59),
                    ),
                ),
                shape = RoundedCornerShape(46.dp),
            )
            .clickable(enabled = !state.scanning, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = Color(0xFF0A5CFF).copy(alpha = .7f),
                shape = CircleShape,
            ) {
                Icon(
                    Icons.Outlined.CleaningServices,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.padding(15.dp).size(34.dp),
                )
            }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (state.scanning) stringResource(R.string.dashboard_scanning) else stringResource(R.string.smart_clean_primary),
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                )
                Text(
                    when {
                        hasDashboardSnapshot && opportunityBytes > 0L -> {
                            stringResource(
                                R.string.dashboard_opportunity_ready,
                                ByteFormatter.format(opportunityBytes),
                            )
                        }
                        hasDashboardSnapshot -> {
                            stringResource(R.string.dashboard_no_current_opportunity)
                        }
                        else -> {
                            stringResource(R.string.smart_clean_primary_subtitle)
                        }
                    },
                    color = Color.White.copy(alpha = .78f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
private fun DashboardToolTile(
    title: String,
    amount: String,
    icon: ImageVector,
    accent: Color,
    badge: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.height(156.dp),
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            accent.copy(alpha = .18f),
                            Color(0xFF11193B),
                            Color(0xFF10152F),
                        ),
                    ),
                )
                .padding(12.dp),
        ) {
            if (badge) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd),
                    color = Color(0xFFE63E44),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        amount,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Surface(
                    color = accent.copy(alpha = .18f),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.padding(10.dp).size(34.dp),
                    )
                }
                Column {
                    Text(
                        title,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        minLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            amount,
                            modifier = Modifier.weight(1f),
                            color = accent,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Surface(
                            color = accent.copy(alpha = .35f),
                            shape = CircleShape,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowForward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.padding(5.dp).size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardWideTool(
    title: String,
    amount: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.height(116.dp),
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1935)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(accent.copy(alpha = .14f), Color.Transparent),
                    ),
                )
                .padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = accent.copy(alpha = .17f),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.padding(11.dp).size(34.dp),
                )
            }
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    amount,
                    color = Color(0xFFB9C7E8),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = accent,
            )
        }
    }
}

@Composable
private fun DeepCleanPromo(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF171B6D),
                            Color(0xFF251771),
                            Color(0xFF452080),
                        ),
                    ),
                )
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(4.dp, 62.dp)
                    .background(Color(0xFF496EFF), RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.size(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.dashboard_more_space_title),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    stringResource(R.string.dashboard_more_space_subtitle),
                    color = Color(0xFFC0C9E9),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Surface(
                color = Rose500.copy(alpha = .18f),
                shape = CircleShape,
            ) {
                Icon(
                    Icons.Outlined.Bolt,
                    contentDescription = null,
                    tint = Color(0xFFFFD04D),
                    modifier = Modifier.padding(14.dp).size(34.dp),
                )
            }
        }
    }
}
