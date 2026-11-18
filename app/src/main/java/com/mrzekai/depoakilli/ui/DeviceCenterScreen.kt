package com.mrzekai.depoakilli.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.mrzekai.depoakilli.model.ScanFocus

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
    onOpenTrash: () -> Unit = {},
    listAd: @Composable () -> Unit = {},
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
                title = stringResource(R.string.tools_cleaning_section_title),
                subtitle = stringResource(R.string.tools_cleaning_section_subtitle),
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
                title = stringResource(R.string.scan_focus_duplicates),
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

        item(key = "tools-list-native") { listAd() }

        item {
            ToolsSectionHeader(
                title = stringResource(R.string.tools_storage_section_title),
                subtitle = stringResource(R.string.tools_storage_section_subtitle),
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
            ToolActionCard(
                title = stringResource(R.string.trash_title),
                subtitle = stringResource(R.string.trash_description),
                icon = Icons.Outlined.Delete,
                accent = Color(0xFFB0475A),
                onClick = onOpenTrash,
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
                title = stringResource(R.string.cache_manager_title),
                subtitle = stringResource(R.string.cache_manager_subtitle),
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
    }
}

@Composable
internal fun ToolsHero(state: CleanerUiState) {
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
            // Without Usage Access Android reports no per-app cache at all.
            // Say so instead of printing "0 B", which read as a real
            // measurement (QA v43 #03).
            Text(
                if (state.appCache.accessGranted) {
                    stringResource(
                        R.string.tools_rebuilt_status,
                        ByteFormatter.format(state.storage.availableBytes),
                        ByteFormatter.format(state.appCache.totalCacheBytes),
                    )
                } else {
                    stringResource(
                        R.string.tools_rebuilt_status_cache_unmeasured,
                        ByteFormatter.format(state.storage.availableBytes),
                    )
                },
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
internal fun StatusPill(
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
internal fun PermissionToolCard(title: String, subtitle: String, action: String, onClick: () -> Unit) {
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
internal fun ToolsSectionHeader(
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
internal fun ToolActionCard(
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
                // Full description, wrapped: cutting it at two lines hid the
                // important part ("lower-co…", "outside Downl…") (QA v43 #13).
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = accent)
        }
    }
}
