package com.mrzekai.depoakilli.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.model.DeviceInfoSnapshot
import com.mrzekai.depoakilli.model.InstalledAppEntry
import com.mrzekai.depoakilli.model.ScanFocus
import com.mrzekai.depoakilli.ui.theme.ElectricBlue

internal enum class LegalPage(@StringRes val titleRes: Int) {
    PRIVACY(R.string.privacy_policy),
    TERMS(R.string.terms_of_service),
    ABOUT(R.string.about_app),
}

@Composable
internal fun DeviceCenterScreen(
    state: CleanerUiState,
    onScan: (ScanFocus) -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onOpenWhatsApp: () -> Unit,
    onOpenCache: () -> Unit,
    onOpenAppManager: () -> Unit,
    onOptimizeMemory: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ToolsHero(state) }
        if (!state.hasAllFilesAccess) {
            item {
                PermissionToolCard(
                    title = stringResource(R.string.all_files_access_title),
                    subtitle = stringResource(R.string.all_files_access_description),
                    action = stringResource(R.string.grant_access),
                    onClick = onRequestAllFilesAccess,
                )
            }
        }
        item {
            Text(stringResource(R.string.cleaner_engine_tools), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(stringResource(R.string.cleaner_engine_tools_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            ToolActionCard(
                title = stringResource(R.string.deep_cleaner_title),
                subtitle = stringResource(R.string.deep_cleaner_subtitle),
                icon = Icons.Outlined.AutoAwesome,
                accent = Color(0xFF0A67DF),
                onClick = { onScan(ScanFocus.DEEP) },
            )
        }
        item {
            ToolActionCard(
                title = stringResource(R.string.junk_cleaner_title),
                subtitle = stringResource(R.string.junk_cleaner_subtitle_v050),
                icon = Icons.Outlined.DeleteSweep,
                accent = Color(0xFFEA6A22),
                onClick = { onScan(ScanFocus.JUNK) },
            )
        }
        item {
            ToolActionCard(
                title = stringResource(R.string.duplicates_tool_title),
                subtitle = stringResource(R.string.duplicates_tool_subtitle_v050),
                icon = Icons.Outlined.ContentCopy,
                accent = Color(0xFF7047E8),
                onClick = { onScan(ScanFocus.DUPLICATES) },
            )
        }
        item {
            ToolActionCard(
                title = stringResource(R.string.large_files_tool_title),
                subtitle = stringResource(R.string.large_files_tool_subtitle_v050),
                icon = Icons.Outlined.VideoFile,
                accent = Color(0xFFEA3E5C),
                onClick = { onScan(ScanFocus.LARGE_FILES) },
            )
        }
        item {
            ToolActionCard(
                title = stringResource(R.string.media_cleaner_title),
                subtitle = stringResource(R.string.media_cleaner_subtitle),
                icon = Icons.Outlined.PhotoLibrary,
                accent = Color(0xFF0B8DD8),
                onClick = { onScan(ScanFocus.MEDIA) },
            )
        }
        item {
            ToolActionCard(
                title = stringResource(R.string.whatsapp_cleaner_title),
                subtitle = stringResource(R.string.whatsapp_cleaner_subtitle_v050),
                icon = Icons.Outlined.Chat,
                accent = Color(0xFF10A861),
                onClick = onOpenWhatsApp,
            )
        }
        item {
            ToolActionCard(
                title = stringResource(R.string.downloads_apk_title),
                subtitle = stringResource(R.string.downloads_apk_subtitle),
                icon = Icons.Outlined.Download,
                accent = Color(0xFF8A5B17),
                onClick = { onScan(ScanFocus.DOWNLOADS) },
            )
        }
        item {
            ToolActionCard(
                title = stringResource(R.string.deep_cache_title),
                subtitle = stringResource(R.string.deep_cache_subtitle),
                icon = Icons.Outlined.CleaningServices,
                accent = Color(0xFF1852D5),
                onClick = onOpenCache,
            )
        }
        item {
            ToolActionCard(
                title = stringResource(R.string.app_manager_title),
                subtitle = stringResource(R.string.app_manager_subtitle),
                icon = Icons.Outlined.Android,
                accent = Color(0xFF1F8A68),
                onClick = onOpenAppManager,
            )
        }
        item {
            ToolActionCard(
                title = stringResource(R.string.storage_analyzer_title),
                subtitle = stringResource(R.string.storage_analyzer_subtitle),
                icon = Icons.Outlined.Storage,
                accent = Color(0xFF5E4BBA),
                onClick = { onScan(ScanFocus.ANALYZE) },
            )
        }
        item {
            ToolActionCard(
                title = stringResource(R.string.settings_title),
                subtitle = stringResource(R.string.settings_subtitle),
                icon = Icons.Outlined.Settings,
                accent = Color(0xFF526079),
                onClick = onOpenSettings,
            )
        }
    }
}

@Composable
private fun ToolsHero(state: CleanerUiState) {
    Card(shape = RoundedCornerShape(28.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF07143B), Color(0xFF0A5BDC), Color(0xFF14AE8C))))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.cleaner_engine_badge), color = Color.White.copy(alpha = .78f), fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.tools_rebuilt_title), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Text(
                stringResource(
                    R.string.tools_rebuilt_status,
                    ByteFormatter.format(state.storage.availableBytes),
                    ByteFormatter.format(state.appCache.totalCacheBytes),
                ),
                color = Color.White.copy(alpha = .86f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(
                    if (state.hasAllFilesAccess) stringResource(R.string.access_files_ready) else stringResource(R.string.access_files_missing),
                    state.hasAllFilesAccess,
                )
                StatusPill(
                    if (state.hasUsageAccess) stringResource(R.string.access_usage_ready) else stringResource(R.string.access_usage_optional),
                    state.hasUsageAccess,
                )
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, active: Boolean) {
    Surface(
        color = if (active) Color(0xFF9CF2C5).copy(alpha = .22f) else Color(0xFFFFD580).copy(alpha = .20f),
        shape = CircleShape,
    ) {
        Text(text, color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}

@Composable
private fun PermissionToolCard(title: String, subtitle: String, action: String, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2414)), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Security, contentDescription = null, tint = Color(0xFF9A6500))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Black)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun ToolActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(21.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = accent.copy(alpha = .13f), shape = RoundedCornerShape(15.dp)) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.padding(11.dp).size(29.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Black)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = accent)
        }
    }
}

@Composable
private fun RamOptimizerCard(state: CleanerUiState, onOptimizeMemory: () -> Unit) {
    Card(shape = RoundedCornerShape(21.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0D2A25))) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Color(0xFF0C9C70), shape = RoundedCornerShape(15.dp)) {
                    Icon(Icons.Outlined.Memory, contentDescription = null, tint = Color.White, modifier = Modifier.padding(11.dp).size(28.dp))
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.ram_optimizer_title_v050), fontWeight = FontWeight.Black)
                    Text(
                        stringResource(R.string.ram_optimizer_subtitle_v050, ByteFormatter.format(state.memory.availableBytes)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(stringResource(R.string.ram_optimizer_policy_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onOptimizeMemory, enabled = !state.optimizingMemory, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Bolt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (state.optimizingMemory) stringResource(R.string.memory_optimizing) else stringResource(R.string.ram_optimize_action))
            }
        }
    }
}

@Composable
internal fun AppCacheManagerScreen(
    state: CleanerUiState,
    onRequestUsageAccess: () -> Unit,
    onRefresh: () -> Unit,
    onClearAllAppCaches: () -> Unit,
    onClearOwnCache: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(shape = RoundedCornerShape(26.dp)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.horizontalGradient(listOf(Color(0xFF071B55), Color(0xFF0A67DF))))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.deep_cache_title), color = Color.White.copy(alpha = .8f), fontWeight = FontWeight.Bold)
                    Text(ByteFormatter.format(state.appCache.totalCacheBytes), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
                    Text(stringResource(R.string.deep_cache_detected), color = Color.White.copy(alpha = .85f))
                }
            }
        }
        if (!state.hasUsageAccess) {
            item {
                PermissionToolCard(
                    title = stringResource(R.string.cache_access_required),
                    subtitle = stringResource(R.string.cache_access_explanation_v050),
                    action = stringResource(R.string.cache_grant_action),
                    onClick = onRequestUsageAccess,
                )
            }
        }
        item {
            Card(shape = RoundedCornerShape(21.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.deep_cache_system_action_title), fontWeight = FontWeight.Black)
                    Text(stringResource(R.string.deep_cache_system_action_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onClearAllAppCaches, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.CleaningServices, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.deep_cache_system_action))
                    }
                    Text(stringResource(R.string.deep_cache_system_confirmation_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (state.appCache.entries.isNotEmpty()) {
            item { Text(stringResource(R.string.cache_other_apps), fontWeight = FontWeight.Black) }
            items(state.appCache.entries.take(50), key = { it.packageName }) { app ->
                Card(shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Android, contentDescription = null, tint = ElectricBlue)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.label, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(ByteFormatter.format(app.cacheBytes), fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        item {
            TextButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cache_refresh)) }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF101E3D)), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(15.dp)) {
                    Text(stringResource(R.string.cache_own_app), fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.cache_own_app_subtitle, ByteFormatter.format(state.ownCacheBytes)), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onClearOwnCache) { Text(stringResource(R.string.clear_only_smart_cleaner_cache)) }
                }
            }
        }
    }
}

@Composable
internal fun AppManagerScreen(
    state: CleanerUiState,
    onRequestUsageAccess: () -> Unit,
    onRefresh: () -> Unit,
    onUninstallApp: (String) -> Unit,
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
                Text(stringResource(R.string.apps_found, state.installedApps.size), fontWeight = FontWeight.Black)
                TextButton(onClick = onRefresh) { Text(stringResource(R.string.cache_refresh)) }
            }
        }
        items(state.installedApps, key = InstalledAppEntry::packageName) { app ->
            AppManagerRow(app, onUninstallApp)
        }
    }
}

@Composable
private fun AppManagerRow(app: InstalledAppEntry, onUninstallApp: (String) -> Unit) {
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
            TextButton(onClick = { onUninstallApp(app.packageName) }) { Text(stringResource(R.string.uninstall)) }
        }
    }
}

@Composable
internal fun SettingsDetailScreen(
    privacyOptionsRequired: Boolean,
    onOpenLanguageSettings: () -> Unit,
    onRateApp: () -> Unit,
    onSendFeedback: () -> Unit,
    onShareApp: () -> Unit,
    onShowPrivacyOptions: () -> Unit,
    onOpenLegalPage: (LegalPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(stringResource(R.string.settings_subtitle_v050), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            SettingsActionRow(
                title = stringResource(R.string.language_settings),
                subtitle = stringResource(R.string.language_settings_subtitle_v050),
                icon = Icons.Outlined.Language,
                onClick = onOpenLanguageSettings,
            )
        }
        item { SettingsActionRow(stringResource(R.string.rate_us), stringResource(R.string.rate_us_subtitle), Icons.Outlined.Bolt, onRateApp) }
        item { SettingsActionRow(stringResource(R.string.send_feedback), stringResource(R.string.send_feedback_subtitle), Icons.Outlined.Info, onSendFeedback) }
        item { SettingsActionRow(stringResource(R.string.share_app), stringResource(R.string.share_app_subtitle), Icons.Outlined.Android, onShareApp) }
        item { SettingsActionRow(stringResource(R.string.privacy_policy), stringResource(R.string.privacy_policy_subtitle), Icons.Outlined.Security) { onOpenLegalPage(LegalPage.PRIVACY) } }
        item { SettingsActionRow(stringResource(R.string.terms_of_service), stringResource(R.string.terms_of_service_subtitle), Icons.Outlined.Info) { onOpenLegalPage(LegalPage.TERMS) } }
        if (privacyOptionsRequired) {
            item { SettingsActionRow(stringResource(R.string.ad_privacy_preferences), stringResource(R.string.ad_privacy_preferences_subtitle), Icons.Outlined.Security, onShowPrivacyOptions) }
        }
        item { SettingsActionRow(stringResource(R.string.about_app), stringResource(R.string.about_app_subtitle), Icons.Outlined.Info) { onOpenLegalPage(LegalPage.ABOUT) } }
    }
}

@Composable
internal fun LegalDetailScreen(page: LegalPage, info: DeviceInfoSnapshot, modifier: Modifier = Modifier) {
    val body = when (page) {
        LegalPage.PRIVACY -> stringResource(R.string.privacy_policy_body_v050)
        LegalPage.TERMS -> stringResource(R.string.terms_of_service_body_v050)
        LegalPage.ABOUT -> stringResource(R.string.about_app_body_v050, info.appVersion)
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Surface(color = ElectricBlue.copy(alpha = .12f), shape = CircleShape) {
                Icon(
                    if (page == LegalPage.PRIVACY) Icons.Outlined.Security else Icons.Outlined.Info,
                    contentDescription = null,
                    tint = ElectricBlue,
                    modifier = Modifier.padding(16.dp).size(34.dp),
                )
            }
        }
        item { Text(stringResource(page.titleRes), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black) }
        item { Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun SettingsActionRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = ElectricBlue.copy(alpha = .12f), shape = CircleShape) {
                Icon(icon, contentDescription = null, tint = ElectricBlue, modifier = Modifier.padding(10.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
        }
    }
}
