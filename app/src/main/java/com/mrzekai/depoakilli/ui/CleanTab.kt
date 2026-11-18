package com.mrzekai.depoakilli.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.model.ScanFocus

private val CleanTabBackground = Color(0xFF030A1D)
private val CleanTabCard = Color(0xFF0A1C42)
private val CleanTabSecondary = Color(0xFFB4C4DF)
private val CleanTabAccent = Color(0xFF28E58A)
private val CleanTabButtonText = Color(0xFF03122B)

/**
 * Clean tab (T1.10): the cleanup plan is the home screen. Without all-files
 * access an explanation and an "Allow access" button are shown (no system
 * screen opens by itself). While a quick scan runs, the previous plan stays
 * visible with "Updating…".
 */
@Composable
internal fun CleanTab(
    state: CleanerUiState,
    onAllowAccess: () -> Unit,
    onCancelScan: () -> Unit,
    accessDeniedOnce: Boolean = false,
    onOpenSettings: () -> Unit = onAllowAccess,
    onOpenTrash: () -> Unit,
    modifier: Modifier = Modifier,
    onShowDetails: () -> Unit = {},
    plan: @Composable (displayState: CleanerUiState, header: LazyListScope.() -> Unit) -> Unit,
) {
    val showingPlan = state.scanFocus == ScanFocus.SMART && state.summary.scannedFileCount > 0
    when {
        !state.hasAllFilesAccess -> AccessCard(
            onAllowAccess = if (accessDeniedOnce) onOpenSettings else onAllowAccess,
            deniedOnce = accessDeniedOnce,
            modifier = modifier,
        )
        showingPlan -> {
            val updating = state.scanning
            val header: LazyListScope.() -> Unit = {
                item(key = "clean-storage-line") { StorageLine(state) }
                if (updating) {
                    item(key = "clean-updating") { UpdatingRow(onCancel = onCancelScan) }
                }
                if (state.trashEntries.isNotEmpty()) {
                    item(key = "clean-trash-line") { TrashSummaryLine(state.trashEntries, onOpen = onOpenTrash) }
                }
            }
            plan(
                if (updating) state.copy(scanning = false, lastScanCompleted = true) else state.copy(lastScanCompleted = true),
                header,
            )
        }
        state.scanning -> SmartScanningState(state = state, onCancel = onCancelScan, modifier = modifier)
        // Finding 8: after a restart the last totals are shown instead of a new
        // full scan on every launch; details are one tap away.
        state.planSummary == null && state.dashboardSnapshotAtMillis > 0L -> SavedSummaryCard(
            state = state,
            onShowDetails = onShowDetails,
            onOpenTrash = onOpenTrash,
            modifier = modifier,
        )
        else -> plan(state.copy(lastScanCompleted = false), {})
    }
}

@Composable
private fun StorageLine(state: CleanerUiState) {
    val total = state.storage.totalBytes
    val free = state.storage.availableBytes
    if (total <= 0L) return
    val usedPercent = (((total - free).coerceAtLeast(0L) * 100L) / total).toInt().coerceIn(0, 100)
    Text(
        stringResource(R.string.home_storage_line, usedPercent, ByteFormatter.format(free)),
        color = CleanTabSecondary,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun UpdatingRow(onCancel: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = CleanTabAccent)
        Text(
            stringResource(R.string.home_updating),
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.cancel))
        }
    }
}

@Composable
private fun AccessCard(onAllowAccess: () -> Unit, deniedOnce: Boolean, modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CleanTabBackground)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(color = CleanTabCard, shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    stringResource(R.string.plan_recommended_title),
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    stringResource(if (deniedOnce) R.string.access_not_granted else R.string.home_access_explainer),
                    color = CleanTabSecondary,
                )
                Button(
                    onClick = onAllowAccess,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CleanTabAccent,
                        contentColor = CleanTabButtonText,
                    ),
                ) {
                    Text(
                        stringResource(if (deniedOnce) R.string.open_settings else R.string.home_allow_access),
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedSummaryCard(
    state: CleanerUiState,
    onShowDetails: () -> Unit,
    onOpenTrash: () -> Unit,
    modifier: Modifier,
) {
    val hours = ((System.currentTimeMillis() - state.dashboardSnapshotAtMillis).coerceAtLeast(0L) / 3_600_000L).toInt()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CleanTabBackground)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StorageLine(state)
        Surface(color = CleanTabCard, shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.plan_recommended_title),
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    stringResource(
                        R.string.home_saved_totals,
                        ByteFormatter.format(state.dashboardCleanableBytes),
                        ByteFormatter.format(state.dashboardReviewBytes),
                    ),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (hours < 1) {
                        stringResource(R.string.home_saved_checked_recently)
                    } else {
                        pluralStringResource(R.plurals.home_saved_checked_hours, hours, hours)
                    },
                    color = CleanTabSecondary,
                )
                Button(
                    onClick = onShowDetails,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CleanTabAccent,
                        contentColor = CleanTabButtonText,
                    ),
                ) {
                    Text(stringResource(R.string.home_saved_details), fontWeight = FontWeight.Black)
                }
            }
        }
        if (state.trashEntries.isNotEmpty()) {
            TrashSummaryLine(state.trashEntries, onOpen = onOpenTrash)
        }
    }
}
