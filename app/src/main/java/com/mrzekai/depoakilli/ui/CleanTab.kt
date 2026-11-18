package com.mrzekai.depoakilli.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.data.TrashEntry
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.model.CleanCategory
import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.model.FolderSize
import com.mrzekai.depoakilli.model.ScanFocus
import com.mrzekai.depoakilli.model.StorageFileType
import com.mrzekai.depoakilli.ui.common.BigRow
import com.mrzekai.depoakilli.ui.common.GiantButton
import com.mrzekai.depoakilli.ui.common.GiantNumber
import com.mrzekai.depoakilli.ui.common.LinkButton
import com.mrzekai.depoakilli.ui.common.PageFrame
import com.mrzekai.depoakilli.ui.common.PageLabel
import com.mrzekai.depoakilli.ui.common.DeleteConfirmDialog
import com.mrzekai.depoakilli.ui.common.rememberGiantScale
import com.mrzekai.depoakilli.ui.smart.ScanProgressBody
import com.mrzekai.depoakilli.ui.theme.AppWeights
import com.mrzekai.depoakilli.ui.theme.Ink
import com.mrzekai.depoakilli.ui.theme.InkSoft
import com.mrzekai.depoakilli.ui.theme.Lime
import java.text.NumberFormat

/** What Home's "easy part is done" rows open (T5.1). */
internal data class HomeHardActions(
    val onOpenMedia: () -> Unit,
    val onOpenApps: () -> Unit,
    val onOpenFolder: (FolderSize?) -> Unit,
    val onOpenDuplicates: () -> Unit,
)

/**
 * Home (T5.1): one giant figure and one giant button on Lime. Without access
 * it shows how full the phone is and MAKE ROOM (the disclosure opens first,
 * no system screen opens by itself). While a scan runs it shows the bytes
 * found so far (measured, never animated up). With a plan it shows "Up to X"
 * and CLEAN UP; when the easy part is done it lists what is taking space and
 * never calls a full phone clean.
 */
@Composable
internal fun CleanTab(
    state: CleanerUiState,
    onAllowAccess: () -> Unit,
    onCancelScan: () -> Unit,
    onStartScan: () -> Unit,
    onClean: (Set<String>) -> Unit,
    onToggleItem: (String) -> Unit,
    onOpenFindings: () -> Unit,
    onOpenTrash: () -> Unit,
    hardActions: HomeHardActions,
    modifier: Modifier = Modifier,
    accessDeniedOnce: Boolean = false,
    onOpenSettings: () -> Unit = onAllowAccess,
) {
    val showingPlan = state.scanFocus == ScanFocus.SMART && state.summary.scannedFileCount > 0
    val savedSummary = !showingPlan && state.dashboardSnapshotAtMillis > 0L
    val plan = state.summary.plan
    // C1: "up to X" is tier 1 plus the findings list (unused apps included),
    // from the same PlanFigures the findings screen and the Tools tiles read.
    val figures = state.planFigures()
    val potentialBytes = when {
        figures != null -> figures.homeMaxBytes
        savedSummary -> state.dashboardCleanableBytes + state.dashboardReviewBytes
        else -> 0L
    }
    val safeBytes = when {
        figures != null -> figures.safeBytes
        savedSummary -> state.dashboardCleanableBytes
        else -> 0L
    }
    val planIsTidy = potentialBytes < com.mrzekai.depoakilli.data.CleaningThresholds.PLAN_MIN_BYTES
    val usedPercent = HomeLogic.usedPercent(state.storage.totalBytes, state.storage.availableBytes)
    val mode = HomeLogic.mode(
        hasAccess = state.hasAllFilesAccess,
        scanning = state.scanning,
        hasPlan = showingPlan || savedSummary,
        planIsTidy = planIsTidy,
        usedPercent = usedPercent,
    )
    val baseScale = rememberGiantScale()
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    val screenHeightDp = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
    var confirmSafe by rememberSaveable { mutableStateOf(false) }

    PageFrame(modifier = modifier) {
        BoxWithConstraints {
            val viewport = maxHeight
            // D7: the scale fits the height left between the status bar and the
            // banner, one size smaller when the Trash line is shown.
            val scale = if (mode == HomeMode.PLAN || mode == HomeMode.TIDY) {
                com.mrzekai.depoakilli.ui.theme.GiantScale.forHome(
                    screenHeightDp = screenHeightDp,
                    availableHeightDp = viewport.value,
                    fontScale = fontScale,
                    trashLine = state.trashEntries.isNotEmpty(),
                )
            } else {
                baseScale
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = viewport)
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                when (mode) {
                    HomeMode.FIRST_RUN, HomeMode.READY -> FirstRunHome(
                        usedPercent = usedPercent,
                        freeBytes = state.storage.availableBytes,
                        scaleNumberSp = scale,
                        onMakeRoom = when {
                            mode == HomeMode.READY -> onStartScan
                            accessDeniedOnce -> onOpenSettings
                            else -> onAllowAccess
                        },
                        deniedOnce = accessDeniedOnce && mode == HomeMode.FIRST_RUN,
                    )
                    HomeMode.SCANNING -> ScanProgressBody(state = state, scale = scale, onCancel = onCancelScan)
                    HomeMode.PLAN -> PlanHome(
                        potentialBytes = potentialBytes,
                        safeBytes = safeBytes,
                        scale = scale,
                        busy = state.cleanupInProgress,
                        trashEntries = state.trashEntries,
                        onOpenTrash = onOpenTrash,
                        onCleanUp = {
                            when (HomeLogic.cleanAction(showingPlan, plan.selectedBytes)) {
                                HomeLogic.CleanAction.CONFIRM_SAFE -> confirmSafe = true
                                HomeLogic.CleanAction.OPEN_FINDINGS -> onOpenFindings()
                                HomeLogic.CleanAction.SCAN -> onStartScan()
                            }
                        },
                        onSeeRest = if (showingPlan && plan.reviewBytes > 0L) onOpenFindings else null,
                    )
                    HomeMode.HARD -> HardHome(
                        state = state,
                        showingPlan = showingPlan,
                        usedPercent = usedPercent,
                        actions = hardActions,
                        onOpenTrash = onOpenTrash,
                    )
                    HomeMode.TIDY -> TidyHome(
                        scale = scale,
                        onCheckAgain = onStartScan,
                        trashEntries = state.trashEntries,
                        onOpenTrash = onOpenTrash,
                    )
                }
            }
        }
    }

    if (confirmSafe && mode == HomeMode.PLAN) {
        val items = plan.selectedItems
        // B1: the shared confirmation writes the real split (Trash vs permanent)
        // of these files; "see all" opens the list where files can be removed.
        // D2: group rows the user can untick or open; only the ticked ids are cleaned.
        DeleteConfirmDialog(
            items = items,
            inProgress = state.cleanupInProgress,
            onDismiss = { confirmSafe = false },
            onConfirm = { ids ->
                confirmSafe = false
                onClean(ids)
            },
        )
    }
}

@Composable
private fun percentText(percent: Int?): String {
    val locale = LocalConfiguration.current.locales[0]
    return if (percent == null) "…" else NumberFormat.getPercentInstance(locale).format(percent / 100.0)
}

@Composable
private fun FirstRunHome(
    usedPercent: Int?,
    freeBytes: Long,
    scaleNumberSp: com.mrzekai.depoakilli.ui.theme.GiantScale,
    onMakeRoom: () -> Unit,
    deniedOnce: Boolean,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))
        PageLabel(stringResource(R.string.home_first_label), strong = true)
        GiantNumber(percentText(usedPercent), scaleNumberSp, modifier = Modifier.fillMaxWidth())
        PageLabel(stringResource(R.string.home_first_sub, ByteFormatter.format(freeBytes)))
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(20.dp))
        GiantButton(
            label = stringResource(if (deniedOnce) R.string.open_settings else R.string.home_cta_make_room),
            hint = if (deniedOnce) null else stringResource(R.string.home_cta_hint_find),
            onClick = onMakeRoom,
            diameter = scaleNumberSp.buttonDp.dp,
        )
        Spacer(Modifier.height(20.dp))
        if (deniedOnce) {
            // T2.1: back from Android's screen without access: one sentence and "Open settings".
            Text(
                stringResource(R.string.access_not_granted),
                color = Ink,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                fontWeight = AppWeights.Strong,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
    Text(
        stringResource(R.string.home_trust_line),
        color = InkSoft,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        fontWeight = AppWeights.Strong,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun PlanHome(
    potentialBytes: Long,
    safeBytes: Long,
    scale: com.mrzekai.depoakilli.ui.theme.GiantScale,
    busy: Boolean,
    trashEntries: List<TrashEntry>,
    onOpenTrash: () -> Unit,
    onCleanUp: () -> Unit,
    onSeeRest: (() -> Unit)?,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))
        PageLabel(stringResource(R.string.home_plan_label), strong = true)
        GiantNumber(ByteFormatter.format(potentialBytes), scale, modifier = Modifier.fillMaxWidth())
        PageLabel(stringResource(R.string.home_plan_sub))
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(20.dp))
        GiantButton(
            label = stringResource(R.string.home_cta_clean),
            hint = stringResource(R.string.home_cta_hint_tap),
            onClick = onCleanUp,
            diameter = scale.buttonDp.dp,
            busy = busy,
        )
        Spacer(Modifier.height(16.dp))
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (safeBytes > 0L) {
            Text(
                stringResource(
                    R.string.home_plan_split,
                    ByteFormatter.format(safeBytes),
                    ByteFormatter.format((potentialBytes - safeBytes).coerceAtLeast(0L)),
                ),
                color = Ink,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                fontWeight = AppWeights.Strong,
                textAlign = TextAlign.Center,
            )
        }
        if (onSeeRest != null) {
            LinkButton(stringResource(R.string.home_review_rest), onClick = onSeeRest)
        }
        HomeTrashLine(trashEntries, onOpenTrash)
        // D7: at least 12 dp between the last line and the banner.
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun HardHome(
    state: CleanerUiState,
    showingPlan: Boolean,
    usedPercent: Int?,
    actions: HomeHardActions,
    onOpenTrash: () -> Unit,
) {
    val summary = state.summary
    val mediaBytes = if (showingPlan) {
        summary.storageTypes
            .filter { it.type == StorageFileType.IMAGES || it.type == StorageFileType.VIDEOS }
            .sumOf { it.totalBytes }
            .takeIf { it > 0L }
    } else {
        null
    }
    val appBytes = if (state.hasUsageAccess) {
        state.installedApps.filterNot { it.systemApp }.sumOf { it.totalBytes }.takeIf { it > 0L }
    } else {
        null
    }
    val folder = if (showingPlan) summary.biggestFolders.firstOrNull() else null
    val duplicateBytes = (state.dashboardCategoryBytes[CleanCategory.DUPLICATE] ?: 0L).takeIf { it > 0L }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.home_hard_title),
            color = Ink,
            fontSize = 24.sp,
            lineHeight = 30.sp,
            fontWeight = AppWeights.Giant,
        )
        if (usedPercent != null) {
            Text(
                stringResource(R.string.home_hard_sub, usedPercent),
                color = InkSoft,
                fontSize = 16.sp,
                fontWeight = AppWeights.Strong,
            )
        }
        Spacer(Modifier.height(4.dp))
        BigRow(
            icon = Icons.Outlined.PhotoLibrary,
            title = stringResource(R.string.home_hard_photos),
            value = mediaBytes?.let(ByteFormatter::format),
            onClick = actions.onOpenMedia,
        )
        BigRow(
            icon = Icons.Outlined.Apps,
            title = stringResource(R.string.home_hard_apps),
            value = appBytes?.let(ByteFormatter::format),
            onClick = actions.onOpenApps,
        )
        BigRow(
            icon = Icons.Outlined.Folder,
            title = stringResource(R.string.plan_biggest_folders),
            detail = folder?.name,
            value = folder?.bytes?.let(ByteFormatter::format),
            onClick = { actions.onOpenFolder(folder) },
        )
        BigRow(
            icon = Icons.Outlined.ContentCopy,
            title = stringResource(R.string.category_duplicates),
            value = duplicateBytes?.let(ByteFormatter::format),
            onClick = actions.onOpenDuplicates,
        )
    }
    HomeTrashLine(state.trashEntries, onOpenTrash)
}

@Composable
private fun TidyHome(
    scale: com.mrzekai.depoakilli.ui.theme.GiantScale,
    onCheckAgain: () -> Unit,
    trashEntries: List<TrashEntry>,
    onOpenTrash: () -> Unit,
) {
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.home_tidy_title),
        color = Ink,
        fontSize = (scale.numberSp * 0.36f).coerceAtLeast(26f).sp,
        lineHeight = (scale.numberSp * 0.44f).coerceAtLeast(32f).sp,
        fontWeight = AppWeights.Giant,
        textAlign = TextAlign.Center,
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GiantButton(
            label = stringResource(R.string.home_check_again),
            onClick = onCheckAgain,
            diameter = scale.buttonDp.dp,
        )
        Spacer(Modifier.height(12.dp))
        HomeTrashLine(trashEntries, onOpenTrash)
    }
}

/** "Trash: 1.2 GB — empties in 6 days" under Home's actions. */
@Composable
private fun HomeTrashLine(entries: List<TrashEntry>, onOpen: () -> Unit) {
    if (entries.isEmpty()) return
    val days = remember(entries) {
        val now = System.currentTimeMillis()
        entries.minOf { trashDaysLeft(it, now) }
    }
    LinkButton(
        text = pluralStringResource(
            R.plurals.trash_home_line,
            days,
            ByteFormatter.format(entries.sumOf(TrashEntry::sizeBytes)),
            days,
        ),
        onClick = onOpen,
        color = InkSoft,
    )
}
