package com.mrzekai.depoakilli.ui.tools

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.mrzekai.depoakilli.ui.theme.OnBlack

// Faz 5 palette: Lime page, black cards, one Lime accent, Coral only for permanent deletes.
internal val ToolBackground = com.mrzekai.depoakilli.ui.theme.Lime
internal val ToolCard = com.mrzekai.depoakilli.ui.theme.Black
internal val ToolCardAlt = com.mrzekai.depoakilli.ui.theme.BlackChip
internal val ToolTextSecondary = com.mrzekai.depoakilli.ui.theme.OnBlackSoft
internal val ToolGreen = com.mrzekai.depoakilli.ui.theme.Lime
internal val ToolCyan = com.mrzekai.depoakilli.ui.theme.Lime
internal val ToolPurple = com.mrzekai.depoakilli.ui.theme.Lime
internal val ToolAmber = com.mrzekai.depoakilli.ui.theme.Lime
internal val ToolRed = com.mrzekai.depoakilli.ui.theme.Coral

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
    listAd: @Composable () -> Unit = {},
    onCancelScan: () -> Unit = {},
    onRefresh: () -> Unit = onScan,
) {
    Surface(modifier = modifier.fillMaxSize(), color = ToolBackground, contentColor = com.mrzekai.depoakilli.ui.theme.Ink) {
        when {
            !state.hasAllFilesAccess -> PremiumToolAccessRequired(
                focus = state.scanFocus,
                onRequest = onRequestAllFilesAccess,
            )
            state.scanning -> PremiumToolScanning(state, onCancelScan)
            !state.lastScanCompleted -> PremiumToolEmpty(state.scanFocus, onScan)
            else -> PremiumToolResults(
                focus = state.scanFocus,
                summary = state.summary,
                cleanupInProgress = state.cleanupInProgress,
                onScan = onScan,
                onToggleItem = onToggleItem,
                onSetItemsSelected = onSetItemsSelected,
                onClean = onClean,
                listAd = listAd,
                onRefresh = onRefresh,
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
                Text(stringResource(R.string.all_files_access_title), color = OnBlack, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(stringResource(R.string.all_files_access_clean_screen), color = ToolTextSecondary)
                Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.grant_access))
                }
            }
        }
    }
}

/** A tool scan in progress: the shared "Found so far" look (measured bytes only). */
@Composable
internal fun PremiumToolScanning(state: CleanerUiState, onCancel: () -> Unit = {}) {
    com.mrzekai.depoakilli.ui.smart.SmartScanningState(state = state, onCancel = onCancel, modifier = Modifier)
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
        border = BorderStroke(1.dp, OnBlack.copy(alpha = .07f)),
    ) {
        Row(Modifier.fillMaxSize().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = accent.copy(alpha = .15f), shape = RoundedCornerShape(14.dp)) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.padding(10.dp).size(24.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(value, color = OnBlack, fontSize = 22.sp, fontWeight = FontWeight.Black)
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
                Text(stringResource(config.titleRes), color = OnBlack, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(stringResource(config.subtitleRes), color = ToolTextSecondary)
                Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.start_scan)) }
            }
        }
    }
}
