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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.model.DeviceInfoSnapshot
import com.mrzekai.depoakilli.model.MemorySnapshot
import com.mrzekai.depoakilli.model.StorageSnapshot
import com.mrzekai.depoakilli.ui.theme.ElectricBlue

internal enum class LegalPage(@StringRes val titleRes: Int) {
    PRIVACY(R.string.privacy_policy),
    TERMS(R.string.terms_of_service),
    ABOUT(R.string.about_app),
}

@Composable
internal fun DeviceCenterScreen(
    state: CleanerUiState,
    onOptimizeMemory: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            DeviceHero(state.deviceInfo)
        }
        item {
            Text(
                stringResource(R.string.device_center_live_status),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
            )
        }
        item {
            StorageDiagnosticCard(state.storage)
        }
        item {
            MemoryDiagnosticCard(
                memory = state.memory,
                optimizing = state.optimizingMemory,
                onOptimize = onOptimizeMemory,
            )
        }
        item {
            BatteryDiagnosticCard(state.deviceInfo)
        }
        item {
            SystemDiagnosticCard(state.deviceInfo)
        }
        item {
            SettingsActionRow(
                title = stringResource(R.string.settings_center),
                subtitle = stringResource(R.string.settings_center_subtitle),
                icon = Icons.Outlined.Settings,
                onClick = onOpenSettings,
            )
        }
        item {
            Text(
                stringResource(R.string.device_center_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DeviceHero(info: DeviceInfoSnapshot) {
    Card(shape = RoundedCornerShape(28.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF090C48), Color(0xFF0758D8), Color(0xFF10BFB8)),
                    ),
                )
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(color = Color.White.copy(alpha = .14f), shape = CircleShape) {
                Icon(
                    Icons.Outlined.Android,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.padding(12.dp).size(32.dp),
                )
            }
            Text(
                stringResource(R.string.device_center_title),
                color = Color.White.copy(alpha = .8f),
                fontWeight = FontWeight.Bold,
            )
            Text(
                "${info.manufacturer} ${info.model}".trim(),
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                stringResource(
                    R.string.device_android_summary,
                    info.androidVersion,
                    info.sdkLevel,
                ),
                color = Color.White.copy(alpha = .88f),
            )
        }
    }
}

@Composable
private fun StorageDiagnosticCard(storage: StorageSnapshot) {
    DiagnosticCard(
        title = stringResource(R.string.device_storage_title),
        value = ByteFormatter.format(storage.availableBytes),
        subtitle = stringResource(
            R.string.device_storage_detail,
            ByteFormatter.format(storage.usedBytes),
            ByteFormatter.format(storage.totalBytes),
        ),
        icon = Icons.Outlined.Storage,
        progress = storage.usedFraction,
        accent = ElectricBlue,
    )
}

@Composable
private fun MemoryDiagnosticCard(
    memory: MemorySnapshot,
    optimizing: Boolean,
    onOptimize: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Color(0xFFE9F8F3), shape = CircleShape) {
                    Icon(
                        Icons.Outlined.Memory,
                        contentDescription = null,
                        tint = Color(0xFF00A979),
                        modifier = Modifier.padding(10.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.device_ram_title), fontWeight = FontWeight.Black)
                    Text(
                        ByteFormatter.format(memory.availableBytes),
                        color = Color(0xFF00A979),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                Text("%${(memory.usedFraction * 100).toInt()}", fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(
                progress = { memory.usedFraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = Color(0xFF00A979),
                trackColor = Color(0xFFE9F8F3),
            )
            Text(
                stringResource(
                    R.string.device_ram_detail,
                    ByteFormatter.format(memory.totalBytes),
                    ByteFormatter.format(memory.appUsedBytes),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onOptimize,
                enabled = !optimizing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Bolt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(
                        if (optimizing) R.string.memory_optimizing else R.string.ram_release_action,
                    ),
                )
            }
        }
    }
}

@Composable
private fun BatteryDiagnosticCard(info: DeviceInfoSnapshot) {
    val chargingText = stringResource(
        if (info.batteryCharging) R.string.battery_charging else R.string.battery_not_charging,
    )
    DiagnosticCard(
        title = stringResource(R.string.device_battery_title),
        value = "%${info.batteryPercent}",
        subtitle = stringResource(
            R.string.device_battery_detail,
            chargingText,
            info.batteryTemperatureCelsius,
        ),
        icon = Icons.Outlined.Bolt,
        progress = info.batteryPercent / 100f,
        accent = Color(0xFFFFA000),
    )
}

@Composable
private fun SystemDiagnosticCard(info: DeviceInfoSnapshot) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = ElectricBlue)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.device_system_title), fontWeight = FontWeight.Black)
            }
            DeviceInfoLine(stringResource(R.string.device_cpu), "${info.cpuAbi} • ${info.cpuCores}")
            DeviceInfoLine(stringResource(R.string.device_screen), info.screenResolution)
            DeviceInfoLine(stringResource(R.string.device_app_version), info.appVersion)
        }
    }
}

@Composable
private fun DeviceInfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DiagnosticCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    progress: Float,
    accent: Color,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = accent.copy(alpha = .12f), shape = CircleShape) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.padding(10.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Black)
                    Text(value, color = accent, fontSize = 24.sp, fontWeight = FontWeight.Black)
                }
            }
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = accent,
                trackColor = accent.copy(alpha = .12f),
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SettingsDetailScreen(
    privacyOptionsRequired: Boolean,
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
            Text(
                stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
            )
            Text(
                stringResource(R.string.settings_subtitle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            SettingsActionRow(
                title = stringResource(R.string.rate_us),
                subtitle = stringResource(R.string.rate_us_subtitle),
                icon = Icons.Outlined.Bolt,
                onClick = onRateApp,
            )
        }
        item {
            SettingsActionRow(
                title = stringResource(R.string.send_feedback),
                subtitle = stringResource(R.string.send_feedback_subtitle),
                icon = Icons.Outlined.Info,
                onClick = onSendFeedback,
            )
        }
        item {
            SettingsActionRow(
                title = stringResource(R.string.share_app),
                subtitle = stringResource(R.string.share_app_subtitle),
                icon = Icons.Outlined.Android,
                onClick = onShareApp,
            )
        }
        item {
            SettingsActionRow(
                title = stringResource(R.string.privacy_policy),
                subtitle = stringResource(R.string.privacy_policy_subtitle),
                icon = Icons.Outlined.Security,
                onClick = { onOpenLegalPage(LegalPage.PRIVACY) },
            )
        }
        item {
            SettingsActionRow(
                title = stringResource(R.string.terms_of_service),
                subtitle = stringResource(R.string.terms_of_service_subtitle),
                icon = Icons.Outlined.Info,
                onClick = { onOpenLegalPage(LegalPage.TERMS) },
            )
        }
        if (privacyOptionsRequired) {
            item {
                SettingsActionRow(
                    title = stringResource(R.string.ad_privacy_preferences),
                    subtitle = stringResource(R.string.ad_privacy_preferences_subtitle),
                    icon = Icons.Outlined.Security,
                    onClick = onShowPrivacyOptions,
                )
            }
        }
        item {
            SettingsActionRow(
                title = stringResource(R.string.about_app),
                subtitle = stringResource(R.string.about_app_subtitle),
                icon = Icons.Outlined.Info,
                onClick = { onOpenLegalPage(LegalPage.ABOUT) },
            )
        }
    }
}

@Composable
internal fun LegalDetailScreen(
    page: LegalPage,
    info: DeviceInfoSnapshot,
    modifier: Modifier = Modifier,
) {
    val body = when (page) {
        LegalPage.PRIVACY -> stringResource(R.string.privacy_policy_body)
        LegalPage.TERMS -> stringResource(R.string.terms_of_service_body)
        LegalPage.ABOUT -> stringResource(R.string.about_app_body, info.appVersion)
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
        item {
            Text(
                stringResource(page.titleRes),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
            )
        }
        item {
            Text(
                body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(17.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(color = ElectricBlue.copy(alpha = .12f), shape = CircleShape) {
                Icon(icon, contentDescription = null, tint = ElectricBlue, modifier = Modifier.padding(10.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
        }
    }
}
