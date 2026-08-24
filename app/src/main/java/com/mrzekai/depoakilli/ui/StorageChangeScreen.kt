package com.mrzekai.depoakilli.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.ByteFormatter
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@Composable
internal fun StorageChangeScreen(
    report: StorageChangeReport,
    refreshing: Boolean,
    progressFiles: Int,
    progressDirectories: Int,
    onAnalyze: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val typeChanges = report.storageTypeChanges.take(MAX_VISIBLE_TYPE_CHANGES)
    val appChanges = report.appCacheChanges.take(MAX_VISIBLE_APP_CHANGES)

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
            StorageChangeHero(report)
        }

        if (refreshing) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = HomeVisualTokens.Teal,
                        trackColor = Color.White.copy(alpha = .08f),
                    )
                    Text(
                        text = stringResource(
                            R.string.storage_change_progress,
                            progressFiles,
                            progressDirectories,
                        ),
                        color = Color(0xFF93A5C8),
                        fontSize = 10.sp,
                    )
                }
            }
        }

        if (report.hasComparison) {
            item {
                StorageChangeSummaryCard(report)
            }

            item {
                SectionTitle(stringResource(R.string.storage_change_types_title))
            }

            if (typeChanges.isEmpty()) {
                item {
                    EmptyChangeCard(
                        title = stringResource(R.string.storage_change_types_empty),
                        subtitle = stringResource(R.string.storage_change_types_empty_subtitle),
                    )
                }
            } else {
                items(
                    items = typeChanges,
                    key = { it.type.name },
                    contentType = { "storage_type_change" },
                ) { change ->
                    StorageTypeChangeRow(change)
                }
            }

            if (appChanges.isNotEmpty()) {
                item {
                    SectionTitle(stringResource(R.string.storage_change_apps_title))
                }

                items(
                    items = appChanges,
                    key = AppCacheChange::packageName,
                    contentType = { "app_cache_change" },
                ) { change ->
                    AppCacheChangeRow(change)
                }
            }
        } else {
            item {
                EmptyChangeCard(
                    title = stringResource(
                        if (report.hasBaseline) {
                            R.string.storage_change_baseline_ready_title
                        } else {
                            R.string.storage_change_no_baseline_title
                        },
                    ),
                    subtitle = stringResource(
                        if (report.hasBaseline) {
                            R.string.storage_change_baseline_ready_subtitle
                        } else {
                            R.string.storage_change_no_baseline_subtitle
                        },
                    ),
                )
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Button(
                    onClick = onAnalyze,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !refreshing,
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        stringResource(
                            when {
                                refreshing -> R.string.storage_change_refreshing
                                !report.hasBaseline -> R.string.storage_change_create_baseline
                                else -> R.string.storage_change_refresh
                            },
                        ),
                    )
                }
                Text(
                    text = stringResource(R.string.storage_change_analysis_scope),
                    color = Color(0xFF93A5C8),
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                )
            }
        }

        item {
            StorageChangeTruthNote()
        }
    }
}

@Composable
private fun StorageChangeHero(report: StorageChangeReport) {
    val current = report.current
    val delta = report.usedDeltaBytes

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
        shape = RoundedCornerShape(28.dp),
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF1745C5),
                            Color(0xFF3B1F8E),
                            Color(0xFF087B73),
                        ),
                    ),
                    RoundedCornerShape(28.dp),
                )
                .padding(horizontal = 18.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.storage_change_hero_label),
                    color = Color(0xFFD5DDF7),
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text = when {
                        !report.hasBaseline -> "—"
                        report.hasComparison -> formatSignedBytes(delta)
                        else -> ByteFormatter.format(current?.usedBytes ?: 0L)
                    },
                    color = Color.White,
                    fontSize = 34.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = when {
                        !report.hasBaseline ->
                            stringResource(R.string.storage_change_hero_no_baseline)
                        !report.hasComparison ->
                            stringResource(R.string.storage_change_hero_baseline)
                        delta > 0L ->
                            stringResource(
                                R.string.storage_change_hero_more_used,
                                ByteFormatter.format(delta),
                            )
                        delta < 0L ->
                            stringResource(
                                R.string.storage_change_hero_less_used,
                                ByteFormatter.format(absSafe(delta)),
                            )
                        else ->
                            stringResource(R.string.storage_change_hero_no_change)
                    },
                    color = Color(0xFFB9C8E7),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )

                current?.let {
                    Text(
                        text = stringResource(
                            R.string.storage_change_last_analysis,
                            storageChangeRelativeLabel(it.analyzedAtMillis),
                        ),
                        color = Color(0xFF8EE8D0),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Surface(
                modifier = Modifier.size(82.dp),
                color = Color.White.copy(alpha = .10f),
                shape = CircleShape,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.Timeline,
                        contentDescription = null,
                        tint = Color(0xFF76F1D2),
                        modifier = Modifier.size(42.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StorageChangeSummaryCard(report: StorageChangeReport) {
    val previous = requireNotNull(report.previous)
    val current = requireNotNull(report.current)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF0D1934),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Text(
                text = stringResource(R.string.storage_change_device_title),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
            )

            StorageChangeValueLine(
                label = stringResource(R.string.storage_change_previous_used),
                value = ByteFormatter.format(previous.usedBytes),
            )
            StorageChangeValueLine(
                label = stringResource(R.string.storage_change_current_used),
                value = ByteFormatter.format(current.usedBytes),
            )
            StorageChangeValueLine(
                label = stringResource(R.string.storage_change_difference),
                value = formatSignedBytes(report.usedDeltaBytes),
                accent = deltaColor(report.usedDeltaBytes),
            )
        }
    }
}

@Composable
private fun StorageChangeValueLine(
    label: String,
    value: String,
    accent: Color = Color.White,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Color(0xFFAEB9D4),
            fontSize = 12.sp,
        )
        Text(
            text = value,
            color = accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 20.sp,
        fontWeight = FontWeight.Black,
    )
}

@Composable
private fun StorageTypeChangeRow(change: StorageTypeChange) {
    val accent = deltaColor(change.deltaBytes)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF0D1934),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(43.dp),
                color = accent.copy(alpha = .14f),
                shape = RoundedCornerShape(14.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.Storage,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Spacer(Modifier.size(11.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(change.type.titleRes),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = stringResource(
                        R.string.storage_change_current_amount,
                        ByteFormatter.format(change.currentBytes),
                    ),
                    color = Color(0xFFAEB9D4),
                    fontSize = 11.sp,
                )
            }

            Text(
                text = formatSignedBytes(change.deltaBytes),
                color = accent,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun AppCacheChangeRow(change: AppCacheChange) {
    val accent = deltaColor(change.deltaBytes)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF0D1934),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncAppIcon(
                packageName = change.packageName,
                modifier = Modifier.size(44.dp),
            )

            Spacer(Modifier.size(11.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = change.label,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.storage_change_app_cache_current,
                        ByteFormatter.format(change.currentBytes),
                    ),
                    color = Color(0xFFAEB9D4),
                    fontSize = 11.sp,
                )
            }

            Text(
                text = formatSignedBytes(change.deltaBytes),
                color = accent,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun EmptyChangeCard(
    title: String,
    subtitle: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF0D1934),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = subtitle,
                color = Color(0xFFAEB9D4),
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
    }
}

@Composable
private fun StorageChangeTruthNote() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF09152D),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = Color(0xFF53C9F4),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(9.dp))
            Text(
                text = stringResource(R.string.storage_change_truth_note),
                color = Color(0xFF93A5C8),
                fontSize = 10.sp,
                lineHeight = 14.sp,
            )
        }
    }
}

@Composable
private fun storageChangeRelativeLabel(timestampMillis: Long): String {
    if (timestampMillis <= 0L) return stringResource(R.string.storage_change_time_now)

    val ageMillis = (System.currentTimeMillis() - timestampMillis).coerceAtLeast(0L)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ageMillis)

    return when {
        minutes < 1L -> stringResource(R.string.storage_change_time_now)
        minutes < 60L -> stringResource(R.string.storage_change_time_minutes, minutes)
        minutes < 24L * 60L -> stringResource(
            R.string.storage_change_time_hours,
            TimeUnit.MILLISECONDS.toHours(ageMillis),
        )
        else -> stringResource(
            R.string.storage_change_time_days,
            TimeUnit.MILLISECONDS.toDays(ageMillis),
        )
    }
}

private fun formatSignedBytes(delta: Long): String = when {
    delta > 0L -> "+${ByteFormatter.format(delta)}"
    delta < 0L -> "−${ByteFormatter.format(absSafe(delta))}"
    else -> ByteFormatter.format(0L)
}

private fun deltaColor(delta: Long): Color = when {
    delta > 0L -> Color(0xFFFFB74D)
    delta < 0L -> Color(0xFF54E2B5)
    else -> Color(0xFF9FB0D0)
}

private fun absSafe(value: Long): Long =
    if (value == Long.MIN_VALUE) Long.MAX_VALUE else abs(value)

private const val MAX_VISIBLE_TYPE_CHANGES = 7
private const val MAX_VISIBLE_APP_CHANGES = 8
