package com.mrzekai.depoakilli.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.model.CleanCategory

private data class HomeSuggestion(
    val title: String,
    val bytes: Long,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val accent: Color,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NeonDashboardScreen(
    state: CleanerUiState,
    onSmartClean: () -> Unit,
    onOpenWhatsApp: () -> Unit,
    onLargeFiles: () -> Unit,
    onApks: () -> Unit,
    onOpenAppCache: () -> Unit,
    onOpenPrivacyAccess: () -> Unit,
    onDownloads: () -> Unit,
    onOpenTools: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val opportunityBytes =
        (state.dashboardCleanableBytes + state.dashboardReviewBytes).coerceAtLeast(0L)
    val hasSnapshot = state.dashboardSnapshotAtMillis > 0L
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val gaugeSize = if (screenWidthDp < 380) 116.dp else 132.dp

    val suggestions = listOf(
        HomeSuggestion(
            title = stringResource(R.string.category_large_files),
            bytes = state.dashboardCategoryBytes[CleanCategory.LARGE_FILE] ?: 0L,
            icon = Icons.Outlined.VideoFile,
            accent = HomeVisualTokens.Amber,
            onClick = onLargeFiles,
        ),
        HomeSuggestion(
            title = stringResource(R.string.category_whatsapp),
            bytes = state.dashboardCategoryBytes[CleanCategory.WHATSAPP_MEDIA] ?: 0L,
            icon = Icons.Outlined.Chat,
            accent = HomeVisualTokens.Teal,
            onClick = onOpenWhatsApp,
        ),
        HomeSuggestion(
            title = stringResource(R.string.category_apk_packages),
            bytes = state.dashboardCategoryBytes[CleanCategory.APK_PACKAGE] ?: 0L,
            icon = Icons.Outlined.Android,
            accent = HomeVisualTokens.Purple,
            onClick = onApks,
        ),
    ).filter { it.bytes > 0L }

    val appCacheBytes = state.appCache.totalCacheBytes.coerceAtLeast(0L)
    val downloadsBytes =
        (state.dashboardCategoryBytes[CleanCategory.OLD_DOWNLOAD] ?: 0L).coerceAtLeast(0L)

    PullToRefreshBox(
        isRefreshing = state.dashboardRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(HomeVisualTokens.PageGradient)
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                start = HomeVisualTokens.PageHorizontalPadding,
                end = HomeVisualTokens.PageHorizontalPadding,
                top = 14.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(HomeVisualTokens.SectionSpacing),
        ) {
            item {
                HomeBrandHeader(
                    onPrivacyAccess = onOpenPrivacyAccess,
                )
            }

            item {
                HomeOpportunityCard(
                    storage = state.storage,
                    opportunityBytes = opportunityBytes,
                    safeBytes = state.dashboardCleanableBytes,
                    reviewBytes = state.dashboardReviewBytes,
                    hasSnapshot = hasSnapshot,
                    gaugeSize = gaugeSize,
                )
            }

            item {
                HomeSmartCleanCard(
                    scanning = state.scanning || state.dashboardRefreshing,
                    onClick = onSmartClean,
                )
            }

            item {
                HomeCleanupProofCard(
                    history = state.cleanupHistory,
                )
            }

            item {
                Text(
                    text = stringResource(R.string.home_smart_suggestions_title),
                    color = HomeVisualTokens.TextPrimary,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Black,
                )
            }

            if (suggestions.isEmpty()) {
                item {
                    HomeEmptySuggestionCard()
                }
            } else {
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        suggestions.forEach { suggestion ->
                            HomeSuggestionCard(
                                title = suggestion.title,
                                amount = ByteFormatter.format(suggestion.bytes),
                                icon = suggestion.icon,
                                accent = suggestion.accent,
                                onClick = suggestion.onClick,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.home_different_tools_title),
                    color = HomeVisualTokens.TextPrimary,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Black,
                )
            }

            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HomeToolShortcut(
                        title = stringResource(R.string.home_cache_title),
                        subtitle = stringResource(R.string.home_cache_subtitle),
                        icon = Icons.Outlined.Storage,
                        accent = HomeVisualTokens.Blue,
                        trailingValue = if (appCacheBytes > 0L) {
                            stringResource(
                                R.string.home_cache_measured,
                                ByteFormatter.format(appCacheBytes),
                            )
                        } else {
                            null
                        },
                        onClick = onOpenAppCache,
                    )

                    HomeToolShortcut(
                        title = stringResource(R.string.home_privacy_title),
                        subtitle = stringResource(
                            if (state.hasAllFilesAccess && state.hasUsageAccess) {
                                R.string.home_privacy_ready
                            } else {
                                R.string.home_privacy_review
                            },
                        ),
                        icon = Icons.Outlined.Security,
                        accent = HomeVisualTokens.Teal,
                        onClick = onOpenPrivacyAccess,
                    )

                    HomeToolShortcut(
                        title = stringResource(R.string.home_downloads_title),
                        subtitle = stringResource(R.string.home_downloads_subtitle),
                        icon = Icons.Outlined.Download,
                        accent = HomeVisualTokens.Cyan,
                        trailingValue = if (downloadsBytes > 0L) {
                            ByteFormatter.format(downloadsBytes)
                        } else {
                            null
                        },
                        onClick = onDownloads,
                    )
                }
            }

            item {
                HomeExploreCard(onClick = onOpenTools)
            }
        }
    }
}
