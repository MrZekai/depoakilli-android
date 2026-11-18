package com.mrzekai.depoakilli.ui.tools

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.model.ScanFocus
import com.mrzekai.depoakilli.ui.*

internal val ToolBackground = Color(0xFF030A1D)
internal val ToolCard = Color(0xFF081A38)
internal val ToolCardAlt = Color(0xFF0C2348)
internal val ToolTextSecondary = Color(0xFFA9B9D4)
internal val ToolGreen = Color(0xFF24E58A)
internal val ToolCyan = Color(0xFF20C8FF)
internal val ToolPurple = Color(0xFF9A63FF)
internal val ToolAmber = Color(0xFFFFB21A)
internal val ToolRed = Color(0xFFFF5D68)

internal enum class ToolSortMode {
    LARGEST,
    NEWEST,
    SELECTED,
}

internal data class ToolVisualConfig(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val icon: ImageVector,
    val accent: Color,
    val accent2: Color,
)

internal data class ToolSection(
    val key: String,
    val title: String,
    val subtitle: String,
    val accent: Color,
    val items: List<CleanableItem>,
)

@Composable
internal fun PremiumCleanerToolScreen(
    state: CleanerUiState,
    onRequestAllFilesAccess: () -> Unit,
    onScan: () -> Unit,
    onToggleItem: (String) -> Unit,
    onSetItemsSelected: (ids: Set<String>, selected: Boolean, bulk: Boolean) -> Unit,
    onClean: (Set<String>?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = ToolBackground) {
        when {
            !state.hasAllFilesAccess -> PremiumToolAccessRequired(
                focus = state.scanFocus,
                onRequest = onRequestAllFilesAccess,
            )
            state.scanning -> PremiumToolScanning(state)
            !state.lastScanCompleted -> PremiumToolEmpty(state.scanFocus, onScan)
            else -> PremiumToolResults(
                focus = state.scanFocus,
                summary = state.summary,
                cleanupInProgress = state.cleanupInProgress,
                onScan = onScan,
                onToggleItem = onToggleItem,
                onSetItemsSelected = onSetItemsSelected,
                onClean = onClean,
            )
        }
    }
}

@Composable
internal fun PremiumToolAccessRequired(
    focus: ScanFocus,
    onRequest: () -> Unit,
) {
    val config = toolConfig(focus)
    Box(Modifier.fillMaxSize().padding(22.dp), contentAlignment = Alignment.Center) {
        Card(
            colors = CardDefaults.cardColors(containerColor = ToolCard),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, config.accent.copy(alpha = .35f)),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(color = config.accent.copy(alpha = .16f), shape = CircleShape) {
                    Icon(config.icon, contentDescription = null, tint = config.accent, modifier = Modifier.padding(20.dp).size(46.dp))
                }
                Text(stringResource(R.string.all_files_access_title), color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(stringResource(R.string.all_files_access_clean_screen), color = ToolTextSecondary)
                Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.grant_access))
                }
            }
        }
    }
}

@Composable
internal fun PremiumToolScanning(state: CleanerUiState) {
    val config = toolConfig(state.scanFocus)
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(
            shape = RoundedCornerShape(30.dp),
            colors = CardDefaults.cardColors(containerColor = ToolCard),
            border = BorderStroke(1.dp, config.accent.copy(alpha = .34f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(listOf(config.accent.copy(alpha = .62f), Color(0xFF0A2A65), config.accent2.copy(alpha = .55f))))
                    .padding(22.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(100.dp),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = .15f),
                            strokeWidth = 8.dp,
                        )
                        Surface(color = Color(0xCC06132D), shape = CircleShape) {
                            Icon(config.icon, contentDescription = null, tint = Color.White, modifier = Modifier.padding(18.dp).size(38.dp))
                        }
                    }
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(stringResource(config.titleRes), color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        Text(stringResource(R.string.premium_tool_scanning), color = Color.White.copy(alpha = .88f), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PremiumMetricCard(
                value = state.scanProgressFiles.toString(),
                label = stringResource(R.string.smart_scan_files_label),
                icon = Icons.Outlined.InsertDriveFile,
                accent = config.accent,
                modifier = Modifier.weight(1f),
            )
            PremiumMetricCard(
                value = state.scanProgressDirectories.toString(),
                label = stringResource(R.string.smart_scan_folders_label),
                icon = Icons.Outlined.Folder,
                accent = config.accent2,
                modifier = Modifier.weight(1f),
            )
        }

        Surface(color = ToolCard, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = .08f))) {
            Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Security, contentDescription = null, tint = ToolGreen)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(stringResource(R.string.premium_tool_on_device_title), color = Color.White, fontWeight = FontWeight.Black)
                    Text(stringResource(R.string.scan_private), color = ToolTextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
internal fun PremiumMetricCard(
    value: String,
    label: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.height(92.dp),
        colors = CardDefaults.cardColors(containerColor = ToolCard),
        shape = RoundedCornerShape(21.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .07f)),
    ) {
        Row(Modifier.fillMaxSize().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = accent.copy(alpha = .15f), shape = RoundedCornerShape(14.dp)) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.padding(10.dp).size(24.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(value, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text(label, color = ToolTextSecondary, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
internal fun PremiumToolEmpty(focus: ScanFocus, onScan: () -> Unit) {
    val config = toolConfig(focus)
    Box(Modifier.fillMaxSize().padding(22.dp), contentAlignment = Alignment.Center) {
        Card(
            colors = CardDefaults.cardColors(containerColor = ToolCard),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, config.accent.copy(alpha = .35f)),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(color = config.accent.copy(alpha = .14f), shape = CircleShape) {
                    Icon(config.icon, contentDescription = null, tint = config.accent, modifier = Modifier.padding(22.dp).size(48.dp))
                }
                Text(stringResource(config.titleRes), color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(stringResource(config.subtitleRes), color = ToolTextSecondary)
                Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.start_scan)) }
            }
        }
    }
}
