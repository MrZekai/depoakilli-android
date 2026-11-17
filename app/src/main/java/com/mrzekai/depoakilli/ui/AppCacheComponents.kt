package com.mrzekai.depoakilli.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
import com.mrzekai.depoakilli.ui.theme.AdLabel
import com.mrzekai.depoakilli.ui.theme.Black
import com.mrzekai.depoakilli.ui.theme.BlackChip
import com.mrzekai.depoakilli.ui.theme.Lime
import com.mrzekai.depoakilli.ui.theme.OnBlack
import com.mrzekai.depoakilli.ui.theme.OnBlackSoft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun CacheAppRow(
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
        color = Black,
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
                        color = OnBlack,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = ByteFormatter.format(app.cacheBytes),
                        color = Lime,
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
                            BlackChip,
                            RoundedCornerShape(999.dp),
                        ),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(5.dp)
                            .background(
                                Lime,
                                RoundedCornerShape(999.dp),
                            ),
                    )
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    text = app.packageName,
                    color = OnBlackSoft,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                modifier = Modifier.size(34.dp),
                color = BlackChip,
                shape = CircleShape,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = stringResource(R.string.cache_modern_open_actions),
                        tint = Lime,
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
        color = BlackChip,
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
                    tint = Lime,
                    modifier = Modifier.size(25.dp),
                )
            }
        }
    }
}

@Composable
internal fun OwnCacheCard(
    ownCacheBytes: Long,
    onClearOwnCache: () -> Unit,
) {
    Surface(
        color = Black,
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
                color = BlackChip,
                shape = RoundedCornerShape(14.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.CleaningServices,
                        contentDescription = null,
                        tint = Lime,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.cache_own_app),
                    color = OnBlack,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = stringResource(
                        R.string.cache_own_app_subtitle,
                        ByteFormatter.format(ownCacheBytes),
                    ),
                    color = OnBlackSoft,
                    fontSize = 12.sp,
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
internal fun CacheSafetyNote() {
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
                Icons.Outlined.Info,
                contentDescription = null,
                tint = Lime,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(9.dp))
            Text(
                text = stringResource(R.string.cache_modern_safety_note),
                color = OnBlackSoft,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppCacheActionSheet(
    app: AppCacheEntry,
    onDismiss: () -> Unit,
    onOpenAndroidSettings: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = com.mrzekai.depoakilli.ui.common.MakeRoomShapes.Sheet,
        containerColor = Black,
        contentColor = OnBlack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
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
                        color = OnBlack,
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
                        color = Lime,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = app.packageName,
                        color = OnBlackSoft,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Surface(
                color = BlackChip,
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
                        color = OnBlack,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = stringResource(R.string.cache_modern_individual_steps),
                        color = OnBlackSoft,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                    Text(
                        text = stringResource(R.string.cache_modern_individual_warning),
                        color = AdLabel,
                        fontSize = 12.sp,
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
