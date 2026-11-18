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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.model.InstalledAppEntry
import com.mrzekai.depoakilli.ui.theme.ElectricBlue

@Composable
internal fun AppManagerScreen(
    state: CleanerUiState,
    onRequestUsageAccess: () -> Unit,
    onRefresh: () -> Unit,
    onUninstallApp: (String) -> Unit,
    onOpenAppInfo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(stringResource(R.string.app_manager_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
                Text(pluralStringResource(R.plurals.apps_found, state.installedApps.size, state.installedApps.size), fontWeight = FontWeight.Black)
                TextButton(onClick = onRefresh) { Text(stringResource(R.string.cache_refresh)) }
            }
        }
        items(state.installedApps, key = InstalledAppEntry::packageName) { app ->
            AppManagerRow(app, onUninstallApp, onOpenAppInfo)
        }
    }
}

@Composable
internal fun AppManagerRow(
    app: InstalledAppEntry,
    onUninstallApp: (String) -> Unit,
    onOpenAppInfo: (String) -> Unit,
) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = ElectricBlue.copy(alpha = .12f), shape = CircleShape) {
                Icon(Icons.Outlined.Android, contentDescription = null, tint = ElectricBlue, modifier = Modifier.padding(10.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                if (app.totalBytes > 0L) {
                    Text(
                        stringResource(
                            R.string.app_manager_size_detail,
                            ByteFormatter.format(app.totalBytes),
                            ByteFormatter.format(app.cacheBytes),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Preinstalled system apps cannot be uninstalled by the user.
            // Offer Android's App info page instead, where Android shows what
            // is actually allowed (disable, clear storage) (QA v43 #08).
            if (app.systemApp) {
                TextButton(onClick = { onOpenAppInfo(app.packageName) }) { Text(stringResource(R.string.app_info)) }
            } else {
                TextButton(onClick = { onUninstallApp(app.packageName) }) { Text(stringResource(R.string.uninstall)) }
            }
        }
    }
}
