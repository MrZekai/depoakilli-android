package com.mrzekai.depoakilli.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.ads.AdPolicy
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.model.InstalledAppEntry
import com.mrzekai.depoakilli.ui.common.LinkButton
import com.mrzekai.depoakilli.ui.common.listBottomPadding
import com.mrzekai.depoakilli.ui.common.PageFrame
import com.mrzekai.depoakilli.ui.theme.AppWeights
import com.mrzekai.depoakilli.ui.theme.Black
import com.mrzekai.depoakilli.ui.theme.Ink
import com.mrzekai.depoakilli.ui.theme.Lime
import com.mrzekai.depoakilli.ui.theme.OnBlack
import com.mrzekai.depoakilli.ui.theme.OnBlackSoft

@Composable
internal fun AppManagerScreen(
    state: CleanerUiState,
    onRequestUsageAccess: () -> Unit,
    onRefresh: () -> Unit,
    onUninstallApp: (String) -> Unit,
    onOpenAppInfo: (String) -> Unit,
    modifier: Modifier = Modifier,
    listAd: @Composable () -> Unit = {},
) {
    PageFrame(modifier = modifier) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = listBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!state.hasUsageAccess) {
            item {
                PermissionToolCard(
                    title = stringResource(R.string.app_manager_usage_title),
                    subtitle = stringResource(R.string.app_manager_usage_description),
                    action = stringResource(R.string.grant_access),
                    onClick = onRequestUsageAccess,
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pluralStringResource(R.plurals.apps_found, state.installedApps.size, state.installedApps.size),
                    color = Ink,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                )
                LinkButton(stringResource(R.string.cache_refresh), onClick = onRefresh)
            }
        }
        // B7: one list native after the 4th row, in lists of 6 rows or more.
        val showAd = state.installedApps.size >= AdPolicy.NATIVE_LIST_MIN_ROWS
        state.installedApps.forEachIndexed { index, app ->
            item(key = app.packageName) { AppManagerRow(app, onUninstallApp, onOpenAppInfo) }
            if (showAd && index == AdPolicy.NATIVE_LIST_AFTER_REVIEW_ROW - 1) {
                item(key = "app-manager-native") { listAd() }
            }
        }
    }
    }
}

@Composable
internal fun AppManagerRow(
    app: InstalledAppEntry,
    onUninstallApp: (String) -> Unit,
    onOpenAppInfo: (String) -> Unit,
) {
    // B6: the real app icon, no package name, the total big in Lime and the
    // cache as a second line. Inline actions stay one word ("Uninstall"); the
    // App info page of system apps is an icon button, so the name keeps its width.
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Black, contentColor = OnBlack)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncAppIcon(packageName = app.packageName, modifier = Modifier.size(44.dp))
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, color = OnBlack, fontWeight = AppWeights.Title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (app.totalBytes > 0L) {
                    Text(
                        ByteFormatter.format(app.totalBytes),
                        color = Lime,
                        fontSize = 20.sp,
                        lineHeight = 24.sp,
                        fontWeight = AppWeights.Giant,
                        maxLines = 1,
                    )
                    Text(
                        stringResource(R.string.app_manager_cache_line, ByteFormatter.format(app.cacheBytes)),
                        color = OnBlackSoft,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // Preinstalled system apps cannot be uninstalled by the user.
            // Offer Android's App info page instead, where Android shows what
            // is actually allowed (disable, clear storage) (QA v43 #08).
            if (app.systemApp) {
                IconButton(onClick = { onOpenAppInfo(app.packageName) }) {
                    Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.app_info), tint = Lime)
                }
            } else {
                TextButton(
                    onClick = { onUninstallApp(app.packageName) },
                    colors = ButtonDefaults.textButtonColors(contentColor = Lime),
                ) {
                    Text(stringResource(R.string.uninstall), fontWeight = AppWeights.Strong, maxLines = 1)
                }
            }
        }
    }
}
