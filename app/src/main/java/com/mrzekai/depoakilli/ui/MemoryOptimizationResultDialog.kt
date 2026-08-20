package com.mrzekai.depoakilli.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.ui.theme.Lime400

@Composable
internal fun MemoryOptimizationResultDialog(
    result: MemoryOptimizationResult,
    onDismiss: () -> Unit,
) {
    val releasedAppMemory = result.appMemoryReleasedBytes
    val availableGain = result.availableRamGainBytes
    val hasMeasuredGain = releasedAppMemory > 0L || availableGain > 0L || result.rebuildableStateReleased

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xB3000615))
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(30.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF0A2733),
                                    Color(0xFF0B3C35),
                                    Color(0xFF071A2E),
                                ),
                            ),
                        )
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Surface(
                        color = Lime400.copy(alpha = .16f),
                        shape = CircleShape,
                    ) {
                        Icon(
                            if (hasMeasuredGain) Icons.Outlined.Memory else Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = Lime400,
                            modifier = Modifier.padding(17.dp).size(44.dp),
                        )
                    }

                    Text(
                        stringResource(R.string.ram_result_title),
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                    )

                    if (releasedAppMemory > 0L) {
                        Text(
                            ByteFormatter.format(releasedAppMemory),
                            color = Lime400,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            stringResource(R.string.ram_result_app_released),
                            color = Color(0xFFD2E8E1),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                    } else if (availableGain > 0L) {
                        Text(
                            "+${ByteFormatter.format(availableGain)}",
                            color = Lime400,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            stringResource(R.string.ram_result_available_gain),
                            color = Color(0xFFD2E8E1),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        Text(
                            stringResource(
                                if (result.rebuildableStateReleased) {
                                    R.string.ram_result_rebuildable_released_title_v0515
                                } else {
                                    R.string.ram_result_stable
                                },
                            ),
                            color = Lime400,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                        )
                    }

                    if (result.rebuildableStateReleased) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF1976D2).copy(alpha = .13f),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Text(
                                stringResource(R.string.ram_result_rebuildable_released_v0515),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                                color = Color(0xFFB8E5FF),
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    RamMeasuredRow(
                        label = stringResource(R.string.ram_result_available_ram),
                        before = result.beforeAvailableBytes,
                        after = result.afterAvailableBytes,
                    )
                    RamMeasuredRow(
                        label = stringResource(R.string.ram_result_app_memory),
                        before = result.beforeAppUsedBytes,
                        after = result.afterAppUsedBytes,
                    )

                    Text(
                        stringResource(
                            R.string.ram_result_measured_note_v0515,
                            result.measurementSamples,
                        ),
                        color = Color(0xFFAFC7C6),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        textAlign = TextAlign.Center,
                    )

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.ram_result_done),
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RamMeasuredRow(
    label: String,
    before: Long,
    after: Long,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White.copy(alpha = .06f),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                label,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        stringResource(R.string.ram_result_before),
                        color = Color(0xFF9CB1BD),
                        fontSize = 10.sp,
                    )
                    Text(
                        ByteFormatter.format(before),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                    )
                }
                Text(
                    "→",
                    color = Lime400,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        stringResource(R.string.ram_result_after),
                        color = Color(0xFF9CB1BD),
                        fontSize = 10.sp,
                    )
                    Text(
                        ByteFormatter.format(after),
                        color = Lime400,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
    }
}
