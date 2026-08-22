package com.mrzekai.depoakilli.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.model.StorageSnapshot
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@Composable
internal fun HomeBrandHeader(
    onPrivacyAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                color = HomeVisualTokens.TextPrimary,
                fontSize = 30.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.app_tagline),
                color = Color(0xFF8EE8D0),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Surface(
            modifier = Modifier
                .size(52.dp)
                .clickable(onClick = onPrivacyAccess),
            color = Color(0xFF0B685B).copy(alpha = .38f),
            shape = CircleShape,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.Security,
                    contentDescription = stringResource(R.string.home_privacy_title),
                    tint = Color(0xFF64F0C5),
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Composable
internal fun HomeOpportunityCard(
    storage: StorageSnapshot,
    opportunityBytes: Long,
    safeBytes: Long,
    reviewBytes: Long,
    hasSnapshot: Boolean,
    gaugeSize: Dp,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(HomeVisualTokens.CardRadius)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                brush = HomeVisualTokens.HeroBorder,
                shape = shape,
            )
            .background(
                brush = HomeVisualTokens.HeroGradient,
                shape = shape,
            )
            .padding(horizontal = 18.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                text = stringResource(R.string.dashboard_cleanup_opportunity),
                color = Color(0xFFD7DEFF),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = when {
                    !hasSnapshot -> "—"
                    opportunityBytes > 0L -> ByteFormatter.format(opportunityBytes)
                    else -> stringResource(R.string.dashboard_no_cleanup_found)
                },
                color = HomeVisualTokens.TextPrimary,
                fontSize = 42.sp,
                lineHeight = 44.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (hasSnapshot) {
                HomeMetricLine(
                    icon = Icons.Outlined.Security,
                    text = stringResource(
                        R.string.dashboard_safe_amount,
                        ByteFormatter.format(safeBytes),
                    ),
                    tint = Color(0xFF9DF1C4),
                )
                HomeMetricLine(
                    icon = Icons.Outlined.AutoAwesome,
                    text = stringResource(
                        R.string.dashboard_review_amount,
                        ByteFormatter.format(reviewBytes),
                    ),
                    tint = Color(0xFFA9D7FF),
                )
            } else {
                Text(
                    text = stringResource(R.string.dashboard_not_analyzed_hint),
                    color = HomeVisualTokens.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        HomeStorageGauge(
            storage = storage,
            size = gaugeSize,
        )
    }
}

@Composable
private fun HomeMetricLine(
    icon: ImageVector,
    text: String,
    tint: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = text,
            color = HomeVisualTokens.TextSecondary,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HomeStorageGauge(
    storage: StorageSnapshot,
    size: Dp,
) {
    val hasStorage = storage.totalBytes > 0L
    val usedFraction = if (hasStorage) storage.usedFraction.coerceIn(0f, 1f) else 0f
    val usedPercent = (usedFraction * 100f).roundToInt().coerceIn(0, 100)
    val freeFraction = if (hasStorage) 1f - usedFraction else 0f
    val accent = when {
        !hasStorage -> HomeVisualTokens.Cyan
        freeFraction < .10f -> Color(0xFFFF5B75)
        freeFraction < .20f -> HomeVisualTokens.Amber
        else -> HomeVisualTokens.Teal
    }

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 11.dp.toPx()
            val ring = Size(
                width = size.toPx() - stroke,
                height = size.toPx() - stroke,
            )
            val topLeft = Offset(stroke / 2f, stroke / 2f)

            drawArc(
                color = Color.White.copy(alpha = .12f),
                startAngle = -130f,
                sweepAngle = 260f,
                useCenter = false,
                topLeft = topLeft,
                size = ring,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = accent,
                startAngle = -130f,
                sweepAngle = 260f * usedFraction,
                useCenter = false,
                topLeft = topLeft,
                size = ring,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (hasStorage) ByteFormatter.format(storage.availableBytes) else "—",
                color = HomeVisualTokens.TextPrimary,
                fontSize = 18.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
            Text(
                text = stringResource(R.string.dashboard_free_space),
                color = accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
            )
            if (hasStorage) {
                Text(
                    text = stringResource(
                        R.string.dashboard_storage_used_percent,
                        usedPercent,
                    ),
                    color = HomeVisualTokens.TextSecondary,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
internal fun HomeSmartCleanCard(
    scanning: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(26.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(HomeVisualTokens.PrimaryGradient, shape)
            .clickable(enabled = !scanning, onClick = onClick)
            .padding(horizontal = 17.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(58.dp),
            color = Color.White.copy(alpha = .13f),
            shape = CircleShape,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(31.dp),
                )
            }
        }

        Spacer(Modifier.size(13.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = if (scanning) {
                    stringResource(R.string.dashboard_scanning)
                } else {
                    stringResource(R.string.smart_clean_primary)
                },
                color = Color.White,
                fontSize = 24.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = stringResource(R.string.home_smart_clean_subtitle),
                color = Color.White.copy(alpha = .83f),
                fontSize = 13.sp,
                lineHeight = 17.sp,
                maxLines = 2,
            )
        }

        Surface(
            modifier = Modifier.size(46.dp),
            color = Color(0xFF0C855F).copy(alpha = .58f),
            shape = CircleShape,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(27.dp),
                )
            }
        }
    }
}

@Composable
internal fun HomeCleanupProofCard(
    history: CleanupHistorySnapshot,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = HomeVisualTokens.Surface.copy(alpha = .95f),
        shape = RoundedCornerShape(HomeVisualTokens.CompactRadius),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                color = HomeVisualTokens.Teal.copy(alpha = .14f),
                shape = CircleShape,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.CleaningServices,
                        contentDescription = null,
                        tint = HomeVisualTokens.Teal,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Spacer(Modifier.size(11.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_last_cleanup_title),
                    color = HomeVisualTokens.TextPrimary,
                    fontWeight = FontWeight.Black,
                )

                if (history.hasHistory) {
                    Text(
                        text = stringResource(
                            R.string.home_last_cleanup_summary,
                            homeRelativeCleanupLabel(history.lastCleanupAtMillis),
                            ByteFormatter.format(history.lastDeletedBytes),
                            history.lastDeletedCount,
                        ),
                        color = HomeVisualTokens.TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (history.cleanupCount > 1) {
                        Text(
                            text = stringResource(
                                R.string.home_last_cleanup_total,
                                ByteFormatter.format(history.totalDeletedBytes),
                            ),
                            color = HomeVisualTokens.Teal,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.home_last_cleanup_empty),
                        color = HomeVisualTokens.TextSecondary,
                        fontSize = 12.sp,
                    )
                    Text(
                        text = stringResource(R.string.home_last_cleanup_empty_subtitle),
                        color = HomeVisualTokens.TextMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

@Composable
internal fun HomeSuggestionCard(
    title: String,
    amount: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(132.dp)
            .clickable(onClick = onClick),
        color = HomeVisualTokens.Surface,
        shape = RoundedCornerShape(HomeVisualTokens.CompactRadius),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                color = accent.copy(alpha = .16f),
                shape = RoundedCornerShape(14.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Text(
                text = title,
                color = HomeVisualTokens.TextPrimary,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = amount,
                    modifier = Modifier.weight(1f),
                    color = accent,
                    fontSize = 18.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
internal fun HomeEmptySuggestionCard(
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = HomeVisualTokens.Surface,
        shape = RoundedCornerShape(HomeVisualTokens.CompactRadius),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.home_suggestion_empty),
                color = HomeVisualTokens.TextPrimary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.home_suggestion_empty_subtitle),
                color = HomeVisualTokens.TextSecondary,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
internal fun HomeToolShortcut(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    trailingValue: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(118.dp)
            .clickable(onClick = onClick),
        color = HomeVisualTokens.SurfaceMuted,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    color = accent.copy(alpha = .15f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(23.dp),
                        )
                    }
                }

                Spacer(Modifier.size(8.dp))

                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    color = HomeVisualTokens.TextPrimary,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Text(
                text = trailingValue ?: subtitle,
                color = if (trailingValue != null) accent else HomeVisualTokens.TextMuted,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = if (trailingValue != null) FontWeight.Bold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Icon(
                Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = HomeVisualTokens.TextMuted,
                modifier = Modifier
                    .size(18.dp)
                    .align(Alignment.End),
            )
        }
    }
}

@Composable
internal fun HomeExploreCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(HomeVisualTokens.ExploreGradient, shape)
            .border(1.dp, Color(0xFF5549E8).copy(alpha = .8f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            color = Color(0xFF675CFF).copy(alpha = .23f),
            shape = RoundedCornerShape(16.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFFBDB8FF),
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        Spacer(Modifier.size(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.home_discover_title),
                color = HomeVisualTokens.TextPrimary,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = stringResource(R.string.home_discover_subtitle),
                color = Color(0xFFCACAF2),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 2,
            )
        }

        Surface(
            color = Color(0xFF5A51EE),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text(
                text = stringResource(R.string.home_discover_action),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun homeRelativeCleanupLabel(timestampMillis: Long): String {
    if (timestampMillis <= 0L) return stringResource(R.string.home_last_cleanup_now)

    val ageMillis = (System.currentTimeMillis() - timestampMillis).coerceAtLeast(0L)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ageMillis)

    return when {
        minutes < 1L -> stringResource(R.string.home_last_cleanup_now)
        minutes < 60L -> stringResource(R.string.home_last_cleanup_minutes, minutes)
        minutes < 24L * 60L -> stringResource(
            R.string.home_last_cleanup_hours,
            TimeUnit.MILLISECONDS.toHours(ageMillis),
        )
        else -> stringResource(
            R.string.home_last_cleanup_days,
            TimeUnit.MILLISECONDS.toDays(ageMillis),
        )
    }
}
