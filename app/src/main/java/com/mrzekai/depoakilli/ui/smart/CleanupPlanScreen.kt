package com.mrzekai.depoakilli.ui.smart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Cached
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.model.CleanupGroup
import com.mrzekai.depoakilli.model.CleanupPlan
import com.mrzekai.depoakilli.model.AppPlan
import com.mrzekai.depoakilli.model.PlanFigures
import com.mrzekai.depoakilli.model.FolderSize
import com.mrzekai.depoakilli.model.InstalledAppEntry
import com.mrzekai.depoakilli.model.PlanGroup
import com.mrzekai.depoakilli.ui.common.BigRow
import com.mrzekai.depoakilli.ui.common.BlackCard
import com.mrzekai.depoakilli.ui.common.GiantNumber
import com.mrzekai.depoakilli.ui.common.PillButton
import com.mrzekai.depoakilli.ui.common.PillStyle
import com.mrzekai.depoakilli.ui.common.bidiWrap
import com.mrzekai.depoakilli.ui.common.rememberGiantScale
import com.mrzekai.depoakilli.ui.theme.AppWeights
import com.mrzekai.depoakilli.ui.theme.Ink
import com.mrzekai.depoakilli.ui.theme.InkSoft
import com.mrzekai.depoakilli.ui.theme.Lime
import com.mrzekai.depoakilli.ui.theme.OnBlack
import com.mrzekai.depoakilli.ui.theme.OnBlackSoft
import com.mrzekai.depoakilli.ui.common.LinkButton
import androidx.compose.ui.text.style.TextOverflow

/** Stable key of a plan row; the merged small row has no group. */
internal fun PlanGroup.rowKey(): String = group?.name ?: if (smallReview) "SMALL_REVIEW" else "SMALL_ITEMS"

@Composable
internal fun PlanGroup.title(): String = when {
    group != null -> stringResource(group.titleRes)
    smallReview -> stringResource(R.string.plan_small_review)
    else -> stringResource(R.string.plan_small_items)
}

/** Line icon of a findings row. */
internal fun planGroupIcon(group: CleanupGroup?): ImageVector = when (group) {
    null -> Icons.Outlined.InsertDriveFile
    CleanupGroup.DUPLICATE_SAFE,
    CleanupGroup.DUPLICATE_PERSONAL,
    CleanupGroup.DUPLICATE_UNVERIFIED -> Icons.Outlined.ContentCopy
    CleanupGroup.LARGE_VIDEO,
    CleanupGroup.OLD_VIDEO,
    CleanupGroup.WHATSAPP_VIDEO,
    CleanupGroup.WHATSAPP_OLD_VIDEO,
    CleanupGroup.DOWNLOAD_MEDIA -> Icons.Outlined.Movie
    CleanupGroup.SCREEN_RECORDING -> Icons.Outlined.Videocam
    CleanupGroup.SCREENSHOT -> Icons.Outlined.Screenshot
    CleanupGroup.RAW_COPY -> Icons.Outlined.CameraAlt
    CleanupGroup.OTHER_APP_MEDIA,
    CleanupGroup.WHATSAPP_SENT,
    CleanupGroup.WHATSAPP_STATUS,
    CleanupGroup.WHATSAPP_OLD_IMAGE -> Icons.Outlined.Image
    CleanupGroup.WHATSAPP_VOICE,
    CleanupGroup.WHATSAPP_OLD_AUDIO -> Icons.Outlined.AudioFile
    CleanupGroup.DOWNLOAD_FILE,
    CleanupGroup.PARTIAL_DOWNLOADS -> Icons.Outlined.Download
    CleanupGroup.DOWNLOAD_DOCUMENT,
    CleanupGroup.WHATSAPP_DOCUMENT -> Icons.Outlined.Description
    CleanupGroup.APK_INSTALLED,
    CleanupGroup.APK_OLD_VERSION,
    CleanupGroup.APK_OTHER -> Icons.Outlined.Apps
    CleanupGroup.THUMBNAILS,
    CleanupGroup.TEMP_FILES,
    CleanupGroup.SYSTEM_TRASH -> Icons.Outlined.Cached
    CleanupGroup.LARGE_OTHER -> Icons.Outlined.FolderZip
}

/** One findings row: a review group or the unused-apps group (V8). */
internal sealed interface FindingsRow {
    val bytes: Long

    data class Group(val row: PlanGroup) : FindingsRow {
        override val bytes: Long get() = row.bytes
    }

    data class UnusedApps(val count: Int, override val bytes: Long) : FindingsRow
}

/**
 * Tier 3 findings rows ("I'll ask you", C2), biggest first (T5.4). The "My
 * pick" files sit in their own card above. Unused apps join only with Usage
 * Access.
 */
internal fun findingsRows(plan: CleanupPlan, @Suppress("UNUSED_PARAMETER") unusedApps: List<InstalledAppEntry>?): List<FindingsRow> =
    // Faz 6 (E1): unused apps lead the apps section below instead of joining these rows.
    plan.askRows.map { FindingsRow.Group(it) }.sortedByDescending(FindingsRow::bytes)

/** The "unused apps" row that opens the apps section (E1); its bytes are in the findings total (C1). */
internal fun unusedAppsRow(unusedApps: List<InstalledAppEntry>?): FindingsRow.UnusedApps? =
    unusedApps?.takeIf { it.isNotEmpty() }?.let { list ->
        FindingsRow.UnusedApps(list.size, list.sumOf(InstalledAppEntry::totalBytes))
    }

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = Ink,
        fontSize = 18.sp,
        fontWeight = AppWeights.Giant,
        modifier = Modifier.padding(top = 14.dp, start = 4.dp),
    )
}

/**
 * The findings list (T5.4) on Lime: the giant review total, one black row per
 * group (biggest first, unused apps included), the in-list native after the
 * 2nd row, "Start with the biggest", then the other places space can come
 * from (app caches, largest apps, biggest folders, look-alike check).
 */
internal fun LazyListScope.cleanupPlanItems(
    plan: CleanupPlan,
    figures: PlanFigures,
    onConfirmSuggested: () -> Unit,
    onSeeSuggested: () -> Unit,
    appCacheBytes: Long?,
    unusedApps: List<InstalledAppEntry>?,
    onOpenRow: (PlanGroup) -> Unit,
    onClearAppCaches: () -> Unit,
    onOpenUnusedApps: () -> Unit,
    onScanAgain: () -> Unit,
    deepCheck: Pair<Int, Long> = 0 to 0L,
    deepCheckRunning: Boolean = false,
    onDeepCheck: () -> Unit = {},
    biggestFolders: List<FolderSize> = emptyList(),
    hiddenKeptCount: Int = 0,
    onOpenFolder: (FolderSize) -> Unit = {},
    appRows: List<AppPlan.Row>? = null,
    onOpenAppInfo: (String) -> Unit = {},
    onUninstallApp: (String) -> Unit = {},
    onRequestUsageAccess: () -> Unit = {},
    showAppsNote: Boolean = false,
    onAppsNoteShown: () -> Unit = {},
    /** Faz 2 Y7: tier cards. */
    tiers: PlanTiers = PlanTiers(),
) {
    val rows = findingsRows(plan, unusedApps)
    val openFindingsRow: (FindingsRow) -> Unit = { row ->
        when (row) {
            is FindingsRow.Group -> onOpenRow(row.row)
            is FindingsRow.UnusedApps -> onOpenUnusedApps()
        }
    }
    val showSafeCard = tiers.safeItems.isNotEmpty() && figures.safeBytes >= com.mrzekai.depoakilli.data.CleaningThresholds.PLAN_MIN_BYTES
    item(key = "plan-card") {
        val scale = rememberGiantScale()
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // C1: the same figure Home subtracts from its "up to X".
            // Y7: with the "Ready to clear" card on this page, its bytes are part of the figure.
            val giantBytes = if (showSafeCard) figures.homeMaxBytes else figures.findingsBytes
            GiantNumber(ByteFormatter.format(giantBytes), scale, modifier = Modifier.fillMaxWidth())
            Text(
                stringResource(R.string.plan_found_title),
                color = Ink,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                fontWeight = AppWeights.Title,
            )
        }
    }
    // Y7 tier 1: always open, no limit.
    if (showSafeCard) {
        item(key = "plan-tier-now") {
            ReadyNowCard(bytes = figures.safeBytes, onClean = tiers.onCleanSafe)
        }
    }
    // C2 tier 2: one card, one button, one confirmation; everything to Trash.
    // Y7: after the free first cleanup, one-tap cleanup is Pro; picking by hand stays open.
    if (figures.showsSuggestion) {
        item(key = "plan-suggest") {
            if (tiers.oneTapAllowed) {
                SuggestionCard(
                    plan = plan,
                    bytes = figures.suggestedBytes,
                    onConfirm = onConfirmSuggested,
                    onSee = onSeeSuggested,
                )
            } else {
                LockedSuggestionCard(
                    bytes = figures.suggestedBytes,
                    onPickOneByOne = tiers.onPickSuggested,
                    onOneTap = tiers.onOpenPaywall,
                )
            }
        }
    }
    // Faz 3A-2 Y7: "About X without deleting" when big videos can be shrunk.
    item(key = "plan-video-shrink") {
        LocalVideoShrinkEntry.current?.let { entry -> VideoShrinkCard(entry) }
    }
    if (rows.isNotEmpty()) {
        item(key = "plan-ask-card") {
            AskCard(
                plan = plan,
                fileCount = plan.askRows.sumOf { it.items.size },
                dailyLimit = tiers.dailyLimit,
                onShowOneByOne = { openFindingsRow(rows.first()) },
            )
        }
    }
    rows.forEachIndexed { index, row ->
        item(key = "review-" + when (row) {
            is FindingsRow.Group -> row.row.rowKey()
            is FindingsRow.UnusedApps -> "UNUSED_APPS"
        }) {
            when (row) {
                is FindingsRow.Group -> {
                    val count = row.row.items.size
                    // B3/C5: camera files are chosen one by one; say so briefly ("156 videos · from Camera").
                    val oneByOne = row.row.oneByOneCount
                    BigRow(
                        icon = planGroupIcon(row.row.group),
                        title = row.row.title(),
                        detail = if (oneByOne > 0) {
                            val videos = row.row.items.all { it.mimeType.startsWith("video/") }
                            pluralStringResource(
                                if (videos) R.plurals.plan_camera_videos else R.plurals.plan_camera_files,
                                oneByOne,
                                oneByOne,
                            )
                        } else {
                            pluralStringResource(R.plurals.home_scan_files_count, count, count)
                        },
                        value = ByteFormatter.format(row.row.bytes),
                        onClick = { onOpenRow(row.row) },
                    )
                }
                is FindingsRow.UnusedApps -> BigRow(
                    icon = Icons.Outlined.Apps,
                    title = pluralStringResource(R.plurals.plan_unused_apps, row.count, row.count),
                    value = ByteFormatter.format(row.bytes),
                    onClick = onOpenUnusedApps,
                )
            }
        }
    }
    appsSection(
        appRows = appRows,
        unusedRow = unusedAppsRow(unusedApps),
        showNote = showAppsNote,
        onNoteShown = onAppsNoteShown,
        onOpenUnusedApps = onOpenUnusedApps,
        onOpenAppInfo = onOpenAppInfo,
        onUninstallApp = onUninstallApp,
        onRequestUsageAccess = onRequestUsageAccess,
    )
    // V10/V11: files the user kept and folders not suggested stay hidden; say so.
    if (hiddenKeptCount > 0) {
        item(key = "plan-kept-note") {
            Text(
                stringResource(R.string.plan_kept_hidden, hiddenKeptCount),
                color = InkSoft,
                fontSize = 14.sp,
                fontWeight = AppWeights.Strong,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
    if (plan.truncated) {
        item(key = "plan-truncated") {
            val limit = com.mrzekai.depoakilli.data.CleaningThresholds.MAX_RESULT_ITEMS
            Text(
                pluralStringResource(R.plurals.plan_truncated, limit, limit),
                color = Ink,
                fontSize = 14.sp,
                fontWeight = AppWeights.Strong,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }

    item(key = "plan-system-title") { SectionTitle(stringResource(R.string.plan_section_system)) }
    item(key = "plan-system-cache") {
        BigRow(
            icon = Icons.Outlined.Cached,
            title = stringResource(R.string.plan_clear_app_caches),
            detail = appCacheBytes?.takeIf { it > 0L }?.let {
                stringResource(R.string.plan_cache_about, ByteFormatter.format(it))
            },
            onClick = onClearAppCaches,
        )
    }
    if (deepCheck.first > 0 || deepCheckRunning) {
        item(key = "plan-deep-check") {
            DeepCheckRow(groups = deepCheck.first, bytes = deepCheck.second, running = deepCheckRunning, onClick = onDeepCheck)
        }
    }
    // V7: where the space is, folder by folder (opens a review list, nothing selected).
    if (biggestFolders.isNotEmpty()) {
        item(key = "plan-folders-title") { SectionTitle(stringResource(R.string.plan_biggest_folders)) }
        items(biggestFolders, key = { "folder-" + it.volumeRoot + "/" + it.relativePath }) { folder ->
            BigRow(
                icon = Icons.Outlined.Folder,
                title = folder.name,
                detail = bidiWrap(folder.relativePath),
                value = ByteFormatter.format(folder.bytes),
                onClick = { onOpenFolder(folder) },
            )
        }
    }
    item(key = "plan-scan-again") {
        PillButton(
            text = stringResource(R.string.scan_again),
            onClick = onScanAgain,
            style = PillStyle.INK_OUTLINE,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Key of the apps section title; Home's apps line scrolls here (E1). */
internal const val PLAN_APPS_KEY = "plan-apps-title"

/**
 * Faz 6 (E1): "Apps take the most space", under the files the app asks about.
 * Unused apps first, then up to five big apps with their real icons. A row
 * only opens Android's uninstall confirmation (unused 30+ days) or the app's
 * storage settings; the app never deletes inside another app. Without Usage
 * access one row asks for it — no sizes are guessed. No ads in this section.
 */
private fun LazyListScope.appsSection(
    appRows: List<AppPlan.Row>?,
    unusedRow: FindingsRow.UnusedApps?,
    showNote: Boolean,
    onNoteShown: () -> Unit,
    onOpenUnusedApps: () -> Unit,
    onOpenAppInfo: (String) -> Unit,
    onUninstallApp: (String) -> Unit,
    onRequestUsageAccess: () -> Unit,
) {
    if (appRows == null) {
        item(key = PLAN_APPS_KEY) { SectionTitle(stringResource(R.string.plan_apps_title)) }
        item(key = "plan-apps-access") {
            BigRow(
                icon = Icons.Outlined.Apps,
                title = stringResource(R.string.plan_apps_need_access),
                onClick = onRequestUsageAccess,
            )
        }
        return
    }
    if (appRows.isEmpty() && unusedRow == null) return
    item(key = PLAN_APPS_KEY) { SectionTitle(stringResource(R.string.plan_apps_title)) }
    if (unusedRow != null) {
        item(key = "plan-apps-unused") {
            BigRow(
                icon = Icons.Outlined.Apps,
                title = pluralStringResource(R.plurals.plan_unused_apps, unusedRow.count, unusedRow.count),
                value = ByteFormatter.format(unusedRow.bytes),
                onClick = onOpenUnusedApps,
            )
        }
    }
    appRows.forEach { row ->
        item(key = "plan-app-" + row.app.packageName) {
            val days = row.unusedDays.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            BigRow(
                icon = Icons.Outlined.Apps,
                title = row.app.label,
                detail = when (row.action) {
                    AppPlan.Action.UNINSTALL -> pluralStringResource(R.plurals.plan_app_unused, days, days)
                    AppPlan.Action.SETTINGS_DATA_HEAVY -> stringResource(R.string.plan_app_data_heavy)
                    AppPlan.Action.SETTINGS -> stringResource(R.string.plan_app_big)
                },
                value = ByteFormatter.format(row.app.totalBytes),
                leading = {
                    com.mrzekai.depoakilli.ui.AsyncAppIcon(
                        packageName = row.app.packageName,
                        modifier = Modifier.size(44.dp),
                    )
                },
                onClick = {
                    if (row.action == AppPlan.Action.UNINSTALL) {
                        onUninstallApp(row.app.packageName)
                    } else {
                        onOpenAppInfo(row.app.packageName)
                    }
                },
            )
        }
    }
    if (showNote && appRows.isNotEmpty()) {
        item(key = "plan-apps-note") {
            androidx.compose.runtime.LaunchedEffect(Unit) { onNoteShown() }
            Text(
                stringResource(R.string.plan_apps_note),
                color = InkSoft,
                fontSize = 14.sp,
                fontWeight = AppWeights.Strong,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

/**
 * "My pick" (C2): the suggested total, the group names on one line, a Lime
 * "Move X to Trash" and "See files". Nothing is preselected; the button opens
 * the shared confirmation with the file list.
 */
@Composable
private fun SuggestionCard(plan: CleanupPlan, bytes: Long, onConfirm: () -> Unit, onSee: () -> Unit) {
    val groupNames = plan.suggestedItems
        .groupBy { it.assessment.group }
        .entries
        .sortedByDescending { (_, items) -> items.sumOf(com.mrzekai.depoakilli.model.CleanableItem::sizeBytes) }
        .map { (group, _) -> stringResource(group.titleRes) }
    BlackCard(modifier = Modifier.padding(top = 6.dp), contentPadding = PaddingValues(18.dp)) {
        Text(
            stringResource(R.string.plan_suggest_title),
            color = OnBlack,
            fontSize = 17.sp,
            lineHeight = 22.sp,
            fontWeight = AppWeights.Title,
        )
        Text(
            ByteFormatter.format(bytes),
            color = Lime,
            fontSize = 40.sp,
            lineHeight = 46.sp,
            fontWeight = AppWeights.Giant,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            groupNames.joinToString(" · "),
            color = OnBlackSoft,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        PillButton(
            text = stringResource(R.string.plan_suggest_action, ByteFormatter.format(bytes)),
            onClick = onConfirm,
            style = PillStyle.LIME,
            modifier = Modifier.padding(top = 14.dp),
        )
        LinkButton(
            text = stringResource(R.string.plan_suggest_see),
            onClick = onSee,
            color = OnBlack,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Faz 2 Y7: what the plan page needs to show its three tiers and the Pro gate. */
internal data class PlanTiers(
    /** Tier 1 files ("Ready to clear"), cleaned through the usual confirmation. */
    val safeItems: List<com.mrzekai.depoakilli.model.CleanableItem> = emptyList(),
    val onCleanSafe: () -> Unit = {},
    /** The free first "My pick" cleanup is still unused, or the user has Pro. */
    val oneTapAllowed: Boolean = true,
    val onPickSuggested: () -> Unit = {},
    val onOpenPaywall: () -> Unit = {},
    /** Free daily decisions; null with Pro. */
    val dailyLimit: Int? = null,
)

/** Y7 tier 1: app leftovers, old APKs and exact copies; always free. */
@Composable
private fun ReadyNowCard(bytes: Long, onClean: () -> Unit) {
    BlackCard(modifier = Modifier.padding(top = 6.dp), contentPadding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.plan_tier_now_title), color = OnBlack, fontSize = 18.sp, fontWeight = AppWeights.Title, modifier = Modifier.weight(1f))
            Text(ByteFormatter.format(bytes), color = Lime, fontSize = 18.sp, fontWeight = AppWeights.Giant)
        }
        Text(stringResource(R.string.plan_tier_now_body), color = OnBlackSoft, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 6.dp))
        PillButton(
            text = stringResource(R.string.plan_tier_now_action),
            onClick = onClean,
            style = PillStyle.LIME,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/** Y7 tier 2 after the free first cleanup: PRO badge; picking by hand stays open. */
@Composable
private fun LockedSuggestionCard(bytes: Long, onPickOneByOne: () -> Unit, onOneTap: () -> Unit) {
    BlackCard(modifier = Modifier.padding(top = 6.dp), contentPadding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.plan_tier_pick_title), color = OnBlack, fontSize = 18.sp, fontWeight = AppWeights.Title)
            com.mrzekai.depoakilli.ui.pro.ProBadge(Modifier.padding(start = 8.dp))
            Spacer(Modifier.weight(1f))
            Text(ByteFormatter.format(bytes), color = Lime, fontSize = 18.sp, fontWeight = AppWeights.Giant)
        }
        Text(stringResource(R.string.plan_tier_pick_locked), color = OnBlackSoft, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 6.dp))
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(
                text = stringResource(R.string.plan_tier_pick_by_hand),
                onClick = onPickOneByOne,
                style = PillStyle.LIME_OUTLINE,
                modifier = Modifier.weight(1f),
            )
            PillButton(
                text = stringResource(R.string.plan_tier_pick_one_tap),
                onClick = onOneTap,
                style = PillStyle.LIME,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Y7 tier 3: files the app asks about, one by one; the free daily allowance is said up front. */
@Composable
private fun AskCard(plan: CleanupPlan, fileCount: Int, dailyLimit: Int?, onShowOneByOne: () -> Unit) {
    val groupNames = plan.askRows
        .sortedByDescending(PlanGroup::bytes)
        .take(2)
        .map { it.title() }
    BlackCard(modifier = Modifier.padding(top = 6.dp), contentPadding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.plan_ask_title), color = OnBlack, fontSize = 18.sp, fontWeight = AppWeights.Title, modifier = Modifier.weight(1f))
            Text(
                pluralStringResource(R.plurals.home_scan_files_count, fileCount, fileCount),
                color = Lime,
                fontSize = 18.sp,
                fontWeight = AppWeights.Giant,
            )
        }
        val lines = listOfNotNull(
            groupNames.joinToString(" · ").takeIf(String::isNotBlank),
            dailyLimit?.let { pluralStringResource(R.plurals.plan_ask_daily, it, it) },
        )
        if (lines.isNotEmpty()) {
            Text(lines.joinToString(". "), color = OnBlackSoft, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 6.dp))
        }
        PillButton(
            text = stringResource(R.string.plan_ask_one_by_one),
            onClick = onShowOneByOne,
            style = PillStyle.LIME_OUTLINE,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/** Faz 3A-2: what Y7 needs to offer shrinking videos; provided by CleanerApp only while there are candidates. */
internal data class VideoShrinkEntry(val savingBytes: Long, val count: Int, val onOpen: () -> Unit)

internal val LocalVideoShrinkEntry = androidx.compose.runtime.staticCompositionLocalOf<VideoShrinkEntry?> { null }

@Composable
private fun VideoShrinkCard(entry: VideoShrinkEntry) {
    BlackCard(modifier = Modifier.padding(top = 6.dp), contentPadding = PaddingValues(18.dp)) {
        Text(
            stringResource(R.string.video_y7_card, ByteFormatter.format(entry.savingBytes)),
            color = Lime,
            fontSize = 20.sp,
            lineHeight = 25.sp,
            fontWeight = AppWeights.Giant,
        )
        Text(
            pluralStringResource(R.plurals.video_head_body, entry.count, entry.count),
            color = OnBlackSoft,
            fontSize = 14.sp,
            lineHeight = 19.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
        PillButton(
            text = stringResource(R.string.video_title),
            onClick = entry.onOpen,
            style = PillStyle.LIME_OUTLINE,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/** Optional feature: verify large look-alike files (T1B.6). */
@Composable
internal fun DeepCheckRow(groups: Int, bytes: Long, running: Boolean, onClick: () -> Unit) {
    BlackCard(contentPadding = PaddingValues(16.dp)) {
        if (running) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Lime)
                Text(stringResource(R.string.deep_check_running), color = OnBlack)
            }
        } else {
            Text(
                pluralStringResource(R.plurals.deep_check_row, groups, groups, ByteFormatter.format(bytes)),
                color = OnBlack,
                fontWeight = AppWeights.Strong,
            )
            PillButton(
                text = stringResource(R.string.deep_check_action),
                onClick = onClick,
                style = PillStyle.LIME_OUTLINE,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}
