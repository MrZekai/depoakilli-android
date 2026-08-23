package com.mrzekai.depoakilli.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.AppCacheEntry
import com.mrzekai.depoakilli.model.ByteFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private enum class CacheSortMode {
    LARGEST,
    A_TO_Z,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppCacheManagerScreen(
    state: CleanerUiState,
    onRequestUsageAccess: () -> Unit,
    onRefresh: () -> Unit,
    onClearAllAppCaches: () -> Unit,
    onClearOwnCache: () -> Unit,
    onOpenAppDetails: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var sortMode by rememberSaveable { mutableStateOf(CacheSortMode.LARGEST) }
    var selectedApp by remember { mutableStateOf<AppCacheEntry?>(null) }

    val entries = state.appCache.entries
    val maxCacheBytes = entries.maxOfOrNull(AppCacheEntry::cacheBytes)?.coerceAtLeast(1L) ?: 1L
    val visibleEntries = remember(entries, query, sortMode) {
        val normalized = query.trim().lowercase(Locale.getDefault())
        val filtered = if (normalized.isBlank()) {
            entries
        } else {
            entries.filter { app ->
                app.label.lowercase(Locale.getDefault()).contains(normalized) ||
                    app.packageName.lowercase(Locale.ROOT).contains(normalized)
            }
        }
        when (sortMode) {
            CacheSortMode.LARGEST -> filtered.sortedByDescending(AppCacheEntry::cacheBytes)
            CacheSortMode.A_TO_Z -> filtered.sortedBy { it.label.lowercase(Locale.getDefault()) }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF071A50),
                        Color(0xFF06122F),
                        Color(0xFF030816),
                    ),
                ),
            ),
        contentPadding = PaddingValues(
            start = 18.dp,
            end = 18.dp,
            top = 16.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        item {
            CacheManagerHero(
                totalCacheBytes = state.appCache.totalCacheBytes,
                scannedAppCount = state.appCache.scannedAppCount,
            )
        }

        if (!state.hasUsageAccess) {
            item {
                CachePermissionCard(onClick = onRequestUsageAccess)
            }
        }

        item {
            CacheSystemActionCard(
                enabled = !state.cleanupInProgress,
                onClick = onClearAllAppCaches,
            )
        }

        if (state.scanningAppCaches) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF31E2BB),
                    trackColor = Color.White.copy(alpha = .08f),
                )
            }
        }

        if (state.hasUsageAccess) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.cache_modern_list_title),
                            modifier = Modifier.weight(1f),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                        )
                        TextButton(
                            onClick = onRefresh,
                            enabled = !state.scanningAppCaches,
                        ) {
                            Icon(
                                Icons.Outlined.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.cache_refresh))
                        }
                    }

                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Outlined.Search, contentDescription = null)
                        },
                        placeholder = {
                            Text(stringResource(R.string.cache_modern_search_hint))
                        },
                        shape = RoundedCornerShape(18.dp),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = sortMode == CacheSortMode.LARGEST,
                            onClick = { sortMode = CacheSortMode.LARGEST },
                            label = {
                                Text(
                                    stringResource(R.string.cache_modern_sort_largest),
                                    maxLines = 1,
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                        FilterChip(
                            selected = sortMode == CacheSortMode.A_TO_Z,
                            onClick = { sortMode = CacheSortMode.A_TO_Z },
                            label = {
                                Text(
                                    stringResource(R.string.cache_modern_sort_az),
                                    maxLines = 1,
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            if (visibleEntries.isEmpty()) {
                item {
                    Surface(
                        color = Color(0xFF0D1934),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.cache_modern_no_result),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = stringResource(R.string.cache_modern_no_result_subtitle),
                                color = Color(0xFFAEB9D4),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            } else {
                items(
                    items = visibleEntries,
                    key = AppCacheEntry::packageName,
                    contentType = { "cache_app_row" },
                ) { app ->
                    CacheAppRow(
                        app = app,
                        maxCacheBytes = maxCacheBytes,
                        onClick = { selectedApp = app },
                    )
                }
            }
        }

        item {
            OwnCacheCard(
                ownCacheBytes = state.ownCacheBytes,
                onClearOwnCache = onClearOwnCache,
            )
        }

        item { CacheSafetyNote() }
    }

    selectedApp?.let { app ->
        AppCacheActionSheet(
            app = app,
            onDismiss = { selectedApp = null },
            onOpenAndroidSettings = {
                selectedApp = null
                onOpenAppDetails(app.packageName)
            },
        )
    }
}

@Composable
private fun CacheManagerHero(
    totalCacheBytes: Long,
    scannedAppCount: Int,
) {
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF113FB7),
                        Color(0xFF251A6A),
                        Color(0xFF0A445A),
                    ),
                ),
                shape,
            )
            .padding(horizontal = 19.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.cache_modern_total_label),
                color = Color(0xFFD2DAF5),
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = ByteFormatter.format(totalCacheBytes),
                color = Color.White,
                fontSize = 38.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
            Text(
                text = stringResource(
                    R.string.cache_modern_measured_apps,
                    scannedAppCount,
                ),
                color = Color(0xFFB8C6E6),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(R.string.cache_modern_hero_helper),
                color = Color(0xFF8EE8D0),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        CacheHeroGraphic()
    }
}

@Composable
private fun CacheHeroGraphic() {
    Box(
        modifier = Modifier.size(112.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 10.dp.toPx()
            val ring = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            drawArc(
                color = Color.White.copy(alpha = .10f),
                startAngle = -120f,
                sweepAngle = 300f,
                useCenter = false,
                topLeft = topLeft,
                size = ring,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(
                        Color(0xFF1FA6FF),
                        Color(0xFF2DE5B4),
                        Color(0xFF1FA6FF),
                    ),
                ),
                startAngle = -120f,
                sweepAngle = 225f,
                useCenter = false,
                topLeft = topLeft,
                size = ring,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }

        Surface(
            modifier = Modifier.size(62.dp),
            color = Color(0xFF102C67),
            shape = CircleShape,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.Storage,
                    contentDescription = null,
                    tint = Color(0xFF47B7FF),
                    modifier = Modifier.size(31.dp),
                )
            }
        }
    }
}

@Composable
private fun CachePermissionCard(
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2412)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = Color(0xFFFFBE45),
            )
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.cache_access_required),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = stringResource(R.string.cache_access_explanation_v050),
                    color = Color(0xFFD5C9A8),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onClick) {
                Text(stringResource(R.string.cache_grant_action))
            }
        }
    }
}

@Composable
private fun CacheSystemActionCard(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF0A805F),
                        Color(0xFF10A875),
                        Color(0xFF14B98A),
                    ),
                ),
                shape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            color = Color.White.copy(alpha = .13f),
            shape = CircleShape,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.CleaningServices,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(27.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.cache_modern_system_action),
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 17.sp,
            )
            Text(
                text = stringResource(R.string.cache_modern_system_action_subtitle),
                color = Color.White.copy(alpha = .82f),
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        }
        Icon(
            Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = null,
            tint = Color.White,
        )
    }
}

@Composable
private fun CacheAppRow(
    app: AppCacheEntry,
    maxCacheBytes: Long,
    onClick: () -> Unit,
) {
    val fraction =
        (app.cacheBytes.toFloat() / maxCacheBytes.toFloat()).coerceIn(0.04f, 1f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color(0xFF0D1934),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncAppIcon(
                packageName = app.packageName,
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = app.label,
                        modifier = Modifier.weight(1f),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = ByteFormatter.format(app.cacheBytes),
                        color = Color(0xFF80B9FF),
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(7.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .background(
                            Color.White.copy(alpha = .07f),
                            RoundedCornerShape(999.dp),
                        ),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(5.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF297CFF),
                                        Color(0xFF31E2BB),
                                    ),
                                ),
                                RoundedCornerShape(999.dp),
                            ),
                    )
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    text = app.packageName,
                    color = Color(0xFF7E8BA9),
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                modifier = Modifier.size(34.dp),
                color = Color(0xFF17325F),
                shape = CircleShape,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = stringResource(R.string.cache_modern_open_actions),
                        tint = Color(0xFF6FB5FF),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun AsyncAppIcon(
    packageName: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val iconBitmap by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = packageName,
    ) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager
                    .getApplicationIcon(packageName)
                    .toBitmap(width = 96, height = 96)
                    .asImageBitmap()
            }.getOrNull()
        }
    }

    Surface(
        modifier = modifier,
        color = Color(0xFF16264A),
        shape = RoundedCornerShape(13.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (iconBitmap != null) {
                Image(
                    painter = BitmapPainter(requireNotNull(iconBitmap)),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(5.dp)
                        .clip(RoundedCornerShape(9.dp)),
                )
            } else {
                Icon(
                    Icons.Outlined.Android,
                    contentDescription = null,
                    tint = Color(0xFF6FB5FF),
                    modifier = Modifier.size(25.dp),
                )
            }
        }
    }
}

@Composable
private fun OwnCacheCard(
    ownCacheBytes: Long,
    onClearOwnCache: () -> Unit,
) {
    Surface(
        color = Color(0xFF101E3D),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                color = Color(0xFF8B5CF6).copy(alpha = .15f),
                shape = RoundedCornerShape(14.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.CleaningServices,
                        contentDescription = null,
                        tint = Color(0xFFB995FF),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.cache_own_app),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = stringResource(
                        R.string.cache_own_app_subtitle,
                        ByteFormatter.format(ownCacheBytes),
                    ),
                    color = Color(0xFFAEB9D4),
                    fontSize = 11.sp,
                )
            }
            Button(
                onClick = onClearOwnCache,
                enabled = ownCacheBytes > 0L,
            ) {
                Text(stringResource(R.string.cache_modern_clean_now))
            }
        }
    }
}

@Composable
private fun CacheSafetyNote() {
    Surface(
        color = Color(0xFF09152D),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = Color(0xFF53C9F4),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(9.dp))
            Text(
                text = stringResource(R.string.cache_modern_safety_note),
                color = Color(0xFF93A5C8),
                fontSize = 10.sp,
                lineHeight = 14.sp,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppCacheActionSheet(
    app: AppCacheEntry,
    onDismiss: () -> Unit,
    onOpenAndroidSettings: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0B1731),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncAppIcon(
                    packageName = app.packageName,
                    modifier = Modifier.size(52.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = app.label,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(
                            R.string.cache_modern_measured_for_app,
                            ByteFormatter.format(app.cacheBytes),
                        ),
                        color = Color(0xFF8EE8D0),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = app.packageName,
                        color = Color(0xFF7E8BA9),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Surface(
                color = Color(0xFF111F3E),
                shape = RoundedCornerShape(18.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = stringResource(R.string.cache_modern_individual_title),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = stringResource(R.string.cache_modern_individual_steps),
                        color = Color(0xFFB7C2DB),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                    Text(
                        text = stringResource(R.string.cache_modern_individual_warning),
                        color = Color(0xFFFFC76D),
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                    )
                }
            }

            Button(
                onClick = onOpenAndroidSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Settings, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.cache_modern_open_android))
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}
