package com.mrzekai.depoakilli.ui.smart

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import com.mrzekai.depoakilli.model.CleanCategory
import com.mrzekai.depoakilli.ui.*

@Composable
internal fun SmartAccessRequired(onRequest: () -> Unit, modifier: Modifier) {
    Box(
        modifier.fillMaxSize().background(SmartBackground).padding(22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = SmartCard),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(Icons.Outlined.Security, contentDescription = null, tint = SmartCyan, modifier = Modifier.size(54.dp))
                Text(stringResource(R.string.all_files_access_title), color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(stringResource(R.string.all_files_access_clean_screen), color = SmartTextSecondary)
                Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.grant_access))
                }
            }
        }
    }
}

@Composable
internal fun SmartScanningState(state: CleanerUiState, onCancel: () -> Unit, modifier: Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF03091B),
                        Color(0xFF071A4B),
                        Color(0xFF05142E),
                    ),
                ),
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card(
                shape = RoundedCornerShape(30.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF071B45)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF0A2D87),
                                    Color(0xFF0B59C9),
                                    Color(0xFF0B8B86),
                                ),
                            ),
                        )
                        .padding(20.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(100.dp),
                                strokeWidth = 8.dp,
                                color = SmartGreen,
                                trackColor = Color.White.copy(alpha = .14f),
                            )
                            Surface(
                                color = Color(0xFF071B45).copy(alpha = .84f),
                                shape = CircleShape,
                            ) {
                                Icon(
                                    Icons.Outlined.CleaningServices,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.padding(18.dp).size(38.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(18.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Text(
                                stringResource(R.string.smart_scan_progress_title),
                                color = Color.White,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black,
                            )
                            Text(
                                stringResource(R.string.smart_scan_progress_subtitle),
                                color = Color.White.copy(alpha = .86f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            Text(
                stringResource(R.string.smart_scan_live_title),
                color = Color.White,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleMedium,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ScanMetricCard(
                    value = state.scanProgressFiles.toString(),
                    label = stringResource(R.string.smart_scan_files_label),
                    icon = Icons.Outlined.InsertDriveFile,
                    modifier = Modifier.weight(1f),
                )
                ScanMetricCard(
                    value = state.scanProgressDirectories.toString(),
                    label = stringResource(R.string.smart_scan_folders_label),
                    icon = Icons.Outlined.Folder,
                    modifier = Modifier.weight(1f),
                )
            }

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SmartCard),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(R.string.smart_scan_checking_title),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ScanStagePill(
                            title = stringResource(CleanCategory.JUNK.titleRes),
                            accent = Color(0xFF35BFFF),
                            modifier = Modifier.weight(1f),
                        )
                        ScanStagePill(
                            title = stringResource(CleanCategory.DUPLICATE.titleRes),
                            accent = Color(0xFF9A63FF),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ScanStagePill(
                            title = stringResource(CleanCategory.LARGE_FILE.titleRes),
                            accent = Color(0xFFFFB02E),
                            modifier = Modifier.weight(1f),
                        )
                        ScanStagePill(
                            title = stringResource(CleanCategory.APK_PACKAGE.titleRes),
                            accent = Color(0xFF6ADE48),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    ScanStagePill(
                        title = stringResource(R.string.smart_scan_media_stage),
                        accent = Color(0xFF31E0D0),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF09224E),
                shape = RoundedCornerShape(18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Security, contentDescription = null, tint = SmartGreen, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(9.dp))
                    Text(
                        stringResource(R.string.scan_private),
                        color = Color.White.copy(alpha = .90f),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (state.scanHashing) {
                Text(
                    stringResource(R.string.scan_stage_exact_copies),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                val total = state.scanHashTotalBytes
                if (total > 0L) {
                    LinearProgressIndicator(
                        progress = { (state.scanHashDoneBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = SmartGreen,
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = SmartGreen)
                }
            }

            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}

@Composable
internal fun ScanMetricCard(
    value: String,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.heightIn(min = 100.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SmartCard),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = SmartCyan, modifier = Modifier.size(22.dp))
            Column {
                Text(value, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text(label, color = SmartTextSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
internal fun ScanStagePill(
    title: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = accent.copy(alpha = .13f),
        shape = RoundedCornerShape(15.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = .45f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
internal fun SmartEmptyState(onScan: () -> Unit, modifier: Modifier) {
    Box(
        modifier.fillMaxSize().background(SmartBackground).padding(22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = SmartCard),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = SmartCyan, modifier = Modifier.size(56.dp))
                Text(stringResource(R.string.smart_clean_results_title), color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(stringResource(R.string.smart_clean_results_empty_intro), color = SmartTextSecondary)
                Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.start_scan)) }
            }
        }
    }
}
