package com.mrzekai.depoakilli.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
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
import com.mrzekai.depoakilli.ui.theme.ElectricBlue
import com.mrzekai.depoakilli.ui.theme.Lime400

@Composable
internal fun CleanupResultDialog(
    result: CleanupResult,
    onDismiss: () -> Unit,
) {
    val partial = result.failedCount > 0 || result.cancelledCount > 0
    val accent = if (partial) Color(0xFFFFB74D) else Lime400

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xB8000615))
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
                                    Color(0xFF071B31),
                                    Color(0xFF0B3A34),
                                    Color(0xFF07152A),
                                ),
                            ),
                        )
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(15.dp),
                ) {
                    Surface(
                        color = accent.copy(alpha = .16f),
                        shape = CircleShape,
                    ) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.padding(16.dp).size(44.dp),
                        )
                    }

                    Text(
                        stringResource(
                            if (partial) R.string.cleanup_result_partial_title
                            else R.string.cleanup_result_title,
                        ),
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                    )

                    if (result.deletedBytes > 0L) {
                        Text(
                            ByteFormatter.format(result.deletedBytes),
                            color = accent,
                            fontSize = 43.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            stringResource(R.string.cleanup_result_space_reclaimed),
                            color = Color(0xFFD4E9E3),
                            fontWeight = FontWeight.Bold,
                        )
                    } else {
                        Text(
                            stringResource(R.string.cleanup_result_zero),
                            color = Color(0xFFD4E9E3),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White.copy(alpha = .06f),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CleanupResultLine(
                                text = stringResource(
                                    R.string.cleanup_result_files_removed,
                                    result.deletedCount,
                                ),
                                accent = Lime400,
                            )
                            if (result.beforeAvailableBytes > 0L && result.afterAvailableBytes > 0L) {
                                CleanupResultLine(
                                    text = stringResource(
                                        R.string.cleanup_result_storage_snapshot,
                                        ByteFormatter.format(result.beforeAvailableBytes),
                                        ByteFormatter.format(result.afterAvailableBytes),
                                    ),
                                    accent = ElectricBlue,
                                )
                            }
                            if (result.failedCount > 0) {
                                CleanupResultLine(
                                    text = stringResource(
                                        R.string.cleanup_result_failed,
                                        result.failedCount,
                                    ),
                                    accent = Color(0xFFFF8A80),
                                )
                            }
                            if (result.cancelledCount > 0) {
                                CleanupResultLine(
                                    text = stringResource(
                                        R.string.cleanup_result_cancelled,
                                        result.cancelledCount,
                                    ),
                                    accent = Color(0xFFFFC46B),
                                )
                            }
                        }
                    }

                    Text(
                        stringResource(R.string.cleanup_result_note),
                        color = Color(0xFF9FB8C2),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        textAlign = TextAlign.Center,
                    )

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.cleanup_result_done),
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CleanupResultLine(
    text: String,
    accent: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = accent.copy(alpha = .18f),
            shape = CircleShape,
        ) {
            Box(Modifier.size(9.dp))
        }
        Text(
            text,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 9.dp),
        )
    }
}
