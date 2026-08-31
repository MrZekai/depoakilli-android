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
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Timeline
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
import com.mrzekai.depoakilli.BuildConfig
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
    onOpenStorageChange: () -> Unit,
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
            ToolsSectionHeader(
                title = stringResource(R.string.tools_broad_scan_section_title),
                subtitle = stringResource(R.string.tools_broad_scan_section_subtitle),
            )
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
            ToolsSectionHeader(
                title = stringResource(R.string.tools_cleaning_section_title),
                subtitle = stringResource(R.string.tools_cleaning_section_subtitle),
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
                title = stringResource(R.string.category_screenshots),
                subtitle = stringResource(R.string.screenshots_tool_subtitle),
                icon = Icons.Outlined.PhotoLibrary,
                accent = Color(0xFFDA57FF),
                onClick = { onScan(ScanFocus.SCREENSHOTS) },
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
            ToolsSectionHeader(
                title = stringResource(R.string.tools_storage_section_title),
                subtitle = stringResource(R.string.tools_storage_section_subtitle),
            )
        }

        item {
            ToolActionCard(
                title = stringResource(R.string.scan_focus_downloads),
                subtitle = stringResource(R.string.tools_downloads_subtitle),
                icon = Icons.Outlined.Download,
                accent = Color(0xFF8A5B17),
                onClick = { onScan(ScanFocus.DOWNLOADS) },
            )
        }
        item {
            ToolActionCard(
                title = stringResource(R.string.scan_focus_apks),
                subtitle = stringResource(R.string.tools_apk_subtitle),
                icon = Icons.Outlined.Android,
                accent = Color(0xFF3A9B55),
                onClick = { onScan(ScanFocus.APKS) },
            )
        }
        item {
            ToolActionCard(
                title = stringResource(R.string.cache_manager_title),
                subtitle = stringResource(R.string.cache_manager_subtitle),
                icon = Icons.Outlined.CleaningServices,
                accent = Color(0xFF1852D5),
                onClick = onOpenCache,
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
                title = stringResource(R.string.storage_change_title),
                subtitle = stringResource(R.string.tools_storage_change_subtitle),
                icon = Icons.Outlined.Timeline,
                accent = Color(0xFF0E9A8A),
                onClick = onOpenStorageChange,
            )
        }

        item {
            ToolsSectionHeader(
                title = stringResource(R.string.tools_apps_section_title),
                subtitle = stringResource(R.string.tools_apps_section_subtitle),
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusPill(
                    text = if (state.hasAllFilesAccess) {
                        stringResource(R.string.access_files_ready)
                    } else {
                        stringResource(R.string.access_files_missing)
                    },
                    active = state.hasAllFilesAccess,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(
                    text = if (state.hasUsageAccess) {
                        stringResource(R.string.access_usage_ready)
                    } else {
                        stringResource(R.string.access_usage_optional)
                    },
                    active = state.hasUsageAccess,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = if (active) {
            Color(0xFF9CF2C5).copy(alpha = .22f)
        } else {
            Color(0xFFFFD580).copy(alpha = .20f)
        },
        shape = CircleShape,
    ) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
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
private fun ToolsSectionHeader(
    title: String,
    subtitle: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
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
    onOpenPrivacyAccess: () -> Unit,
    onOpenLegalPage: (LegalPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SettingsActionRow(
                title = stringResource(R.string.security_center_title),
                subtitle = stringResource(R.string.security_center_subtitle),
                icon = Icons.Outlined.Security,
                onClick = onOpenPrivacyAccess,
            )
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
    val policyBody = when (page) {
        LegalPage.PRIVACY -> stringResource(R.string.privacy_policy_body_v050)
        LegalPage.TERMS -> stringResource(R.string.terms_of_service_body_v050)
        LegalPage.ABOUT -> stringResource(R.string.about_app_body_v050, info.appVersion)
    }
    val supportEmail = BuildConfig.SUPPORT_EMAIL.trim()
    val body = if (page != LegalPage.ABOUT && supportEmail.isNotBlank()) {
        policyBody + "\n\n" + stringResource(R.string.legal_contact, supportEmail)
    } else {
        policyBody
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
