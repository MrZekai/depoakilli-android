package com.mrzekai.depoakilli.ui.smart

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.data.CleaningThresholds
import com.mrzekai.depoakilli.data.StoragePathRules
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.model.InstalledAppEntry
import com.mrzekai.depoakilli.ui.*
import java.util.concurrent.TimeUnit

internal fun isWhatsAppRelativePath(relativePath: String): Boolean =
    StoragePathRules.isWhatsAppPath(relativePath)

internal fun unusedAppsForSmartReview(
    apps: List<InstalledAppEntry>,
    hasUsageAccess: Boolean,
): List<InstalledAppEntry> {
    if (!hasUsageAccess) return emptyList()
    val now = System.currentTimeMillis()
    val threshold = TimeUnit.DAYS.toMillis(CleaningThresholds.UNUSED_APP_DAYS)
    return apps
        .asSequence()
        // Preinstalled system apps cannot be uninstalled by the user, so they
        // are not offered as removable unused apps (QA v43 #08).
        .filterNot { it.systemApp }
        .filter { it.lastUsedMillis > 0L }
        .filter { now - it.lastUsedMillis >= threshold }
        .filter { it.totalBytes > 0L }
        .sortedWith(
            compareByDescending<InstalledAppEntry> { it.totalBytes }
                .thenBy { it.label.lowercase() },
        )
        .toList()
}

@Composable
internal fun UnusedAppsSectionCard(
    apps: List<InstalledAppEntry>,
    installedAppsLoaded: Boolean,
    loadingApps: Boolean,
    hasUsageAccess: Boolean,
    onRequestUsageAccess: () -> Unit,
    onAnalyzeApps: () -> Unit,
    onOpenAll: () -> Unit,
) {
    val accent = Color(0xFF70E58E)
    val totalBytes = apps.sumOf { it.totalBytes }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SmartCard),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = accent.copy(alpha = .17f), shape = RoundedCornerShape(13.dp)) {
                    Icon(Icons.Outlined.Android, contentDescription = null, tint = accent, modifier = Modifier.padding(9.dp).size(25.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.smart_unused_apps_title),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                    )
                    Text(
                        when {
                            !hasUsageAccess -> stringResource(R.string.smart_unused_apps_permission)
                            loadingApps -> stringResource(R.string.smart_unused_apps_loading)
                            apps.isNotEmpty() -> pluralStringResource(R.plurals.smart_unused_apps_summary, apps.size, apps.size, ByteFormatter.format(totalBytes))
                            installedAppsLoaded -> stringResource(R.string.smart_unused_apps_none)
                            else -> stringResource(R.string.smart_unused_apps_ready)
                        },
                        color = SmartTextSecondary,
                        fontSize = 12.sp,
                    )
                }
            }

            when {
                !hasUsageAccess -> {
                    TextButton(onClick = onRequestUsageAccess) {
                        Text(stringResource(R.string.smart_unused_apps_permission_action), color = accent, fontWeight = FontWeight.Bold)
                    }
                }
                loadingApps -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = accent)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.smart_unused_apps_loading), color = SmartTextSecondary, fontSize = 12.sp)
                    }
                }
                apps.isNotEmpty() -> {
                    apps.take(3).forEach { app ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                app.label,
                                modifier = Modifier.weight(1f),
                                color = Color.White,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                ByteFormatter.format(app.totalBytes),
                                color = accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    TextButton(onClick = onOpenAll) {
                        Text(stringResource(R.string.smart_unused_apps_view_all), color = accent, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = accent, modifier = Modifier.size(17.dp))
                    }
                }
                !installedAppsLoaded -> {
                    TextButton(onClick = onAnalyzeApps) {
                        Text(stringResource(R.string.smart_unused_apps_analyze), color = accent, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text(
                stringResource(R.string.smart_unused_apps_separate_note),
                color = Color(0xFF8FA9BA),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
internal fun UnusedAppsReviewDialog(
    apps: List<InstalledAppEntry>,
    onUninstallApp: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xE603091B)).padding(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = SmartCard),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(R.string.smart_unused_apps_dialog_title),
                        color = Color.White,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        stringResource(R.string.smart_unused_apps_dialog_note),
                        color = SmartTextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    LazyColumn(
                        modifier = Modifier.height(420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(apps, key = { it.packageName }) { app ->
                            Surface(
                                color = SmartCardAlt,
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Surface(
                                        color = Color(0xFF70E58E).copy(alpha = .14f),
                                        shape = RoundedCornerShape(12.dp),
                                    ) {
                                        Icon(
                                            Icons.Outlined.Android,
                                            contentDescription = null,
                                            tint = Color(0xFF70E58E),
                                            modifier = Modifier.padding(8.dp).size(22.dp),
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(app.label, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            pluralStringResource(
                                                R.plurals.smart_unused_apps_last_used,
                                                daysSinceLastUse(app.lastUsedMillis).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                                                daysSinceLastUse(app.lastUsedMillis),
                                            ),
                                            color = SmartTextSecondary,
                                            fontSize = 12.sp,
                                        )
                                        Text(ByteFormatter.format(app.totalBytes), color = Color(0xFF70E58E), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    TextButton(onClick = { onUninstallApp(app.packageName) }) {
                                        Text(stringResource(R.string.uninstall), color = Color(0xFFFF8794), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.done))
                    }
                }
            }
        }
    }
}

internal fun daysSinceLastUse(lastUsedMillis: Long): Long {
    if (lastUsedMillis <= 0L) return 0L
    return TimeUnit.MILLISECONDS.toDays(
        (System.currentTimeMillis() - lastUsedMillis).coerceAtLeast(0L),
    )
}
