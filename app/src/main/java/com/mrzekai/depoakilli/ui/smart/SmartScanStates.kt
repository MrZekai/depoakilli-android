package com.mrzekai.depoakilli.ui.smart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.model.CleanCategory
import com.mrzekai.depoakilli.ui.CleanerUiState
import com.mrzekai.depoakilli.ui.common.BlackCard
import com.mrzekai.depoakilli.ui.common.GiantNumber
import com.mrzekai.depoakilli.ui.common.IconChip
import com.mrzekai.depoakilli.ui.common.LimeProgressBar
import com.mrzekai.depoakilli.ui.common.PageFrame
import com.mrzekai.depoakilli.ui.common.PageLabel
import com.mrzekai.depoakilli.ui.common.PillButton
import com.mrzekai.depoakilli.ui.common.PillStyle
import com.mrzekai.depoakilli.ui.common.rememberGiantScale
import com.mrzekai.depoakilli.ui.theme.AppWeights
import com.mrzekai.depoakilli.ui.theme.BlackChip
import com.mrzekai.depoakilli.ui.theme.GiantScale
import com.mrzekai.depoakilli.ui.theme.InkSoft
import com.mrzekai.depoakilli.ui.theme.Lime
import com.mrzekai.depoakilli.ui.theme.OnBlack
import com.mrzekai.depoakilli.ui.theme.OnBlackSoft
import java.text.NumberFormat

/** Asks for all-files access before a tool scan can run. */
@Composable
internal fun SmartAccessRequired(onRequest: () -> Unit, modifier: Modifier) {
    PageFrame(modifier = modifier) {
        Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
            BlackCard(contentPadding = PaddingValues(22.dp)) {
                IconChip(Icons.Outlined.Security, size = 52.dp)
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(R.string.all_files_access_title),
                    color = OnBlack,
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    fontWeight = AppWeights.Title,
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.all_files_access_clean_screen), color = OnBlackSoft, fontSize = 15.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(16.dp))
                PillButton(text = stringResource(R.string.grant_access), onClick = onRequest, style = PillStyle.LIME)
            }
        }
    }
}

/** A tool scan in progress: the same "Found so far" look as Home. */
@Composable
internal fun SmartScanningState(state: CleanerUiState, onCancel: () -> Unit, modifier: Modifier) {
    val scale = rememberGiantScale()
    PageFrame(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ScanProgressBody(state = state, scale = scale, onCancel = onCancel)
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.scan_private),
                color = InkSoft,
                fontSize = 14.sp,
                fontWeight = AppWeights.Strong,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** No scan yet on a results screen: one card and "Start scan". */
@Composable
internal fun SmartEmptyState(onScan: () -> Unit, modifier: Modifier) {
    PageFrame(modifier = modifier) {
        Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
            BlackCard(contentPadding = PaddingValues(22.dp)) {
                IconChip(Icons.Outlined.AutoAwesome, size = 52.dp)
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(R.string.smart_clean_results_title),
                    color = OnBlack,
                    fontSize = 22.sp,
                    fontWeight = AppWeights.Title,
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.smart_clean_results_empty_intro), color = OnBlackSoft, fontSize = 15.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(16.dp))
                PillButton(text = stringResource(R.string.start_scan), onClick = onScan, style = PillStyle.LIME)
            }
        }
    }
}

/** Status of one line of the scan card. */
private enum class StageStatus { WAITING, RUNNING, DONE }

/**
 * "Found so far" (T5.1 scanning look, also used by the tool scans): the
 * measured bytes of the files classified so far (never animated up), which
 * group is running, and Cancel.
 */
@Composable
internal fun ScanProgressBody(
    state: CleanerUiState,
    scale: GiantScale,
    onCancel: () -> Unit,
) {
    val found = state.scanFoundByCategory
    val foundBytes = found?.values?.sum() ?: 0L
    val indexing = found == null
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))
        PageLabel(stringResource(R.string.home_scan_label), strong = true)
        GiantNumber(ByteFormatter.format(foundBytes), scale, modifier = Modifier.fillMaxWidth())
        PageLabel(
            if (state.scanHashing) {
                stringResource(R.string.scan_stage_exact_copies)
            } else {
                stringResource(
                    R.string.home_scan_now,
                    pluralStringResource(R.plurals.home_scan_files_count, state.scanProgressFiles, state.scanProgressFiles),
                )
            },
        )
    }
    Spacer(Modifier.height(18.dp))
    BlackCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ScanStageLine(
                title = stringResource(R.string.smart_scan_files_label),
                value = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0]).format(state.scanProgressFiles),
                status = if (indexing) StageStatus.RUNNING else StageStatus.DONE,
            )
            val shown = if (found == null) {
                listOf(CleanCategory.JUNK, CleanCategory.LARGE_FILE, CleanCategory.OLD_DOWNLOAD, CleanCategory.APK_PACKAGE)
                    .map { it to null }
            } else {
                found.filterKeys { it != CleanCategory.DUPLICATE }
                    .filterValues { it > 0L }
                    .entries
                    .sortedByDescending { it.value }
                    .map { it.key to it.value }
            }
            shown.forEach { (category, bytes) ->
                ScanStageLine(
                    title = stringResource(category.titleRes),
                    value = bytes?.let(ByteFormatter::format),
                    status = if (bytes == null) StageStatus.WAITING else StageStatus.DONE,
                )
            }
            ScanStageLine(
                title = stringResource(CleanCategory.DUPLICATE.titleRes),
                value = null,
                status = if (state.scanHashing) StageStatus.RUNNING else StageStatus.WAITING,
            )
            val hashTotal = state.scanHashTotalBytes
            when {
                indexing -> LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.CircleShape),
                    color = Lime,
                    trackColor = BlackChip,
                )
                state.scanHashing && hashTotal > 0L -> LimeProgressBar(
                    progress = 0.5f + 0.5f * (state.scanHashDoneBytes.toFloat() / hashTotal.toFloat()),
                )
                else -> LimeProgressBar(progress = 0.5f)
            }
        }
    }
    Spacer(Modifier.height(18.dp))
    PillButton(
        text = stringResource(R.string.cancel),
        onClick = onCancel,
        style = PillStyle.INK_OUTLINE,
    )
}

@Composable
private fun ScanStageLine(title: String, value: String?, status: StageStatus) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (status) {
            StageStatus.DONE -> Icon(Icons.Outlined.Check, contentDescription = null, tint = Lime, modifier = Modifier.size(20.dp))
            StageStatus.RUNNING -> CircularProgressIndicator(color = Lime, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            StageStatus.WAITING -> Text("•", color = OnBlackSoft, fontSize = 20.sp, modifier = Modifier.size(20.dp), textAlign = TextAlign.Center)
        }
        Text(
            title,
            color = if (status == StageStatus.WAITING) OnBlackSoft else OnBlack,
            fontWeight = AppWeights.Strong,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
        if (value != null) Text(value, color = Lime, fontWeight = AppWeights.Giant, fontSize = 15.sp)
    }
}

