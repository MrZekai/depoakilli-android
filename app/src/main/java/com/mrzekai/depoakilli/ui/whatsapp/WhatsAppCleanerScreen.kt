package com.mrzekai.depoakilli.ui.whatsapp

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.WhatsAppMediaCategory
import com.mrzekai.depoakilli.ui.*
import com.mrzekai.depoakilli.ui.theme.WhatsAppGreen

internal val WaBackground = Color(0xFF030A1D)
internal val WaCard = Color(0xFF081A38)
internal val WaCardSecondary = Color(0xFF0B2146)
internal val WaTextSecondary = Color(0xFF9FB1D0)
internal val WaCyan = Color(0xFF19DCCB)
internal val WaAmber = Color(0xFFFFB21A)

internal enum class WhatsAppDirectionFilter {
    ALL,
    INCOMING,
    SENT,
}

internal enum class WhatsAppUiGroup {
    IMAGES,
    VIDEOS,
    DOCUMENTS_OTHER,
}

internal enum class WhatsAppSortMode {
    LARGEST,
    NEWEST,
}

@Composable
internal fun WhatsAppCleanerDetailScreen(
    state: CleanerUiState,
    onBack: () -> Unit,
    onRequestAccess: () -> Unit,
    onScan: () -> Unit,
    onToggleItem: (String) -> Unit,
    onToggleCategory: (WhatsAppMediaCategory) -> Unit,
    onDeleteSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = WaBackground) {
        when {
            !state.hasWhatsAppAccess -> WhatsAppAccessScreen(
                onBack = onBack,
                onRequestAccess = onRequestAccess,
            )

            state.whatsAppScanning -> WhatsAppScanningScreen(
                progress = state.whatsAppScanProgress,
                onBack = onBack,
            )

            !state.whatsAppLastScanCompleted -> WhatsAppReadyScreen(
                onBack = onBack,
                onScan = onScan,
            )

            else -> WhatsAppResultsScreen(
                summary = state.whatsAppSummary,
                cleanupInProgress = state.cleanupInProgress,
                onBack = onBack,
                onScan = onScan,
                onToggleItem = onToggleItem,
                onToggleCategory = onToggleCategory,
                onDeleteSelected = onDeleteSelected,
            )
        }
    }
}

@Composable
internal fun WhatsAppHeader(
    onBack: () -> Unit,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = Color(0xFF07152D),
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .08f)),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = Color.White,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.whatsapp_cleaner_title),
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    color = WhatsAppGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
        }
        // The decorative sparkle button that did nothing on tap was removed
        // (QA v43 #15).
    }
}

@Composable
internal fun WhatsAppAccessScreen(
    onBack: () -> Unit,
    onRequestAccess: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        WhatsAppHeader(onBack = onBack)
        Box(Modifier.weight(1f).fillMaxWidth().padding(22.dp), contentAlignment = Alignment.Center) {
            Card(
                colors = CardDefaults.cardColors(containerColor = WaCard),
                shape = RoundedCornerShape(28.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, WhatsAppGreen.copy(alpha = .25f)),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(color = WhatsAppGreen.copy(alpha = .14f), shape = CircleShape) {
                        Icon(
                            Icons.Outlined.Folder,
                            contentDescription = null,
                            tint = WhatsAppGreen,
                            modifier = Modifier.padding(20.dp).size(46.dp),
                        )
                    }
                    Text(
                        stringResource(R.string.whatsapp_access_title),
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        stringResource(R.string.whatsapp_access_description),
                        color = WaTextSecondary,
                    )
                    Button(onClick = onRequestAccess, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.whatsapp_choose_folder))
                    }
                    Text(
                        stringResource(R.string.whatsapp_access_once),
                        style = MaterialTheme.typography.bodySmall,
                        color = WaTextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
internal fun WhatsAppReadyScreen(
    onBack: () -> Unit,
    onScan: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        WhatsAppHeader(onBack = onBack)
        Box(Modifier.weight(1f).fillMaxWidth().padding(22.dp), contentAlignment = Alignment.Center) {
            Card(
                colors = CardDefaults.cardColors(containerColor = WaCard),
                shape = RoundedCornerShape(28.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(color = WhatsAppGreen.copy(alpha = .14f), shape = CircleShape) {
                        Icon(
                            Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = WhatsAppGreen,
                            modifier = Modifier.padding(24.dp).size(50.dp),
                        )
                    }
                    Text(
                        stringResource(R.string.whatsapp_ready_title),
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        stringResource(R.string.whatsapp_ready_description),
                        color = WaTextSecondary,
                    )
                    Button(onClick = onScan, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.whatsapp_scan_action), fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}
