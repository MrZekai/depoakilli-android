package com.mrzekai.depoakilli.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.ads.CleanupResultAdSurface
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.model.PlanGroup
import com.mrzekai.depoakilli.ui.common.AdTag
import com.mrzekai.depoakilli.ui.common.BlackCard
import com.mrzekai.depoakilli.ui.common.GiantNumber
import com.mrzekai.depoakilli.ui.common.LinkButton
import com.mrzekai.depoakilli.ui.common.PageFrame
import com.mrzekai.depoakilli.ui.common.PageLabel
import com.mrzekai.depoakilli.ui.common.PermanentDeleteSheet
import com.mrzekai.depoakilli.ui.common.PillButton
import com.mrzekai.depoakilli.ui.common.PillStyle
import com.mrzekai.depoakilli.ui.common.rememberGiantScale
import com.mrzekai.depoakilli.ui.theme.AppWeights
import com.mrzekai.depoakilli.ui.theme.Black
import com.mrzekai.depoakilli.ui.theme.InkSoft
import com.mrzekai.depoakilli.ui.theme.Lime
import com.mrzekai.depoakilli.ui.theme.OnBlack

/**
 * Result (T5.6): full-screen Lime. A black circle with a check, the giant
 * amount moved to Trash and when that space is really freed; "X freed" only
 * for bytes actually deleted. No percentage claims. Buttons: black "Done",
 * outlined "Free the space now" (the permanent-delete sheet comes first) and
 * "Undo"; the native ad sits below them with its "Ad" label, 24 dp away.
 */
@Composable
internal fun CleanupResultDialog(
    result: CleanupResult,
    canRequestAds: Boolean,
    showRewardedOffer: Boolean,
    onShowRewardedAd: () -> Unit,
    onSystemDismiss: () -> Unit,
    onDone: (resultAdPresented: Boolean) -> Unit,
    onUndo: () -> Unit = {},
    onEmptyTrash: () -> Unit = {},
    nextReview: PlanGroup? = null,
    onReviewNext: () -> Unit = {},
) {
    var resultAdPresented by remember(result) { mutableStateOf(false) }
    var confirmEmptyTrash by remember { mutableStateOf(false) }
    val partial = result.failedCount > 0 || result.cancelledCount > 0
    val figures = ResultFigures.of(result)
    val scale = rememberGiantScale()

    if (confirmEmptyTrash) {
        PermanentDeleteSheet(
            bytes = result.trashedBytes,
            body = stringResource(R.string.result_empty_trash_confirm, ByteFormatter.format(result.trashedBytes)),
            onDismiss = { confirmEmptyTrash = false },
            onDeletePermanently = {
                confirmEmptyTrash = false
                onEmptyTrash()
            },
        )
    }

    Dialog(
        onDismissRequest = onSystemDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        PageFrame(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .systemBarsPadding()
                    .padding(horizontal = 22.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Black),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (partial) Icons.Outlined.ErrorOutline else Icons.Outlined.Check,
                        contentDescription = null,
                        tint = Lime,
                        modifier = Modifier.size(40.dp),
                    )
                }

                when (figures.headline) {
                    ResultFigures.Headline.TRASHED -> {
                        GiantNumber(ByteFormatter.format(result.trashedBytes), scale, modifier = Modifier.fillMaxWidth())
                        PageLabel(stringResource(R.string.result_moved_to_trash), strong = true)
                        PageLabel(stringResource(R.string.result_space_note))
                        if (figures.showFreed) {
                            PageLabel(
                                stringResource(R.string.result_freed_for_good, ByteFormatter.format(result.deletedBytes)),
                                strong = true,
                            )
                        }
                    }
                    ResultFigures.Headline.FREED -> {
                        GiantNumber(ByteFormatter.format(result.deletedBytes), scale, modifier = Modifier.fillMaxWidth())
                        PageLabel(
                            stringResource(
                                if (result.kind != CleanupResultKind.FILES) {
                                    R.string.cleanup_result_system_cache_reduced
                                } else {
                                    R.string.cleanup_result_space_reclaimed
                                },
                            ),
                            strong = true,
                        )
                    }
                    ResultFigures.Headline.NOTHING -> {
                        PageLabel(resultTitle(result, partial), strong = true)
                        PageLabel(
                            stringResource(
                                if (result.kind != CleanupResultKind.FILES) {
                                    R.string.cleanup_result_system_cache_unmeasured
                                } else {
                                    R.string.cleanup_result_zero
                                },
                            ),
                        )
                    }
                }
                if (figures.headline != ResultFigures.Headline.NOTHING && result.kind != CleanupResultKind.FILES) {
                    PageLabel(resultTitle(result, partial))
                }

                ResultDetails(result)

                // V9: the biggest group still left in the plan, one line above "Done".
                val nextGroup = nextReview?.group
                if (nextReview != null && nextGroup != null) {
                    LinkButton(
                        text = stringResource(
                            R.string.result_next_group,
                            stringResource(nextGroup.titleRes),
                            ByteFormatter.format(nextReview.bytes),
                        ) + " · " + stringResource(R.string.result_review_next),
                        onClick = onReviewNext,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                PillButton(
                    text = stringResource(R.string.result_done),
                    onClick = { onDone(resultAdPresented) },
                    style = PillStyle.BLACK,
                )
                if (figures.canEmptyTrash) {
                    PillButton(
                        text = stringResource(R.string.result_free_now),
                        onClick = { confirmEmptyTrash = true },
                        style = PillStyle.INK_OUTLINE,
                    )
                    LinkButton(
                        text = stringResource(R.string.result_undo),
                        onClick = onUndo,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Text(
                    stringResource(
                        when (result.kind) {
                            CleanupResultKind.SYSTEM_CACHE -> R.string.cleanup_result_system_cache_note
                            CleanupResultKind.APP_CACHE -> R.string.cleanup_result_app_cache_note
                            CleanupResultKind.FILES -> R.string.cleanup_result_note
                        },
                    ),
                    color = InkSoft,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    textAlign = TextAlign.Center,
                )

                if (canRequestAds) {
                    // At least 24 dp between the buttons and the ad; the ad carries its label above it.
                    Spacer(Modifier.height(24.dp))
                    AdTag(modifier = Modifier.align(Alignment.Start))
                    CleanupResultAdSurface(
                        canRequestAds = canRequestAds,
                        onAdPresented = { resultAdPresented = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(24.dp))
                }
                if (showRewardedOffer) {
                    LinkButton(
                        text = stringResource(R.string.rewarded_ad_result_action),
                        onClick = onShowRewardedAd,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** Which giant figure the result shows (pure; covered by unit tests). */
internal data class ResultFigures(
    val headline: Headline,
    /** "X freed" line under a Trash headline: only for bytes really deleted. */
    val showFreed: Boolean,
    val canEmptyTrash: Boolean,
) {
    enum class Headline { TRASHED, FREED, NOTHING }

    companion object {
        fun of(result: CleanupResult): ResultFigures {
            val trashed = result.kind == CleanupResultKind.FILES &&
                result.trashedBytes > 0L &&
                result.trashEntryIds.isNotEmpty()
            val headline = when {
                trashed -> Headline.TRASHED
                result.deletedBytes > 0L -> Headline.FREED
                else -> Headline.NOTHING
            }
            return ResultFigures(
                headline = headline,
                showFreed = trashed && result.deletedBytes > 0L,
                canEmptyTrash = trashed,
            )
        }
    }
}

@Composable
private fun resultTitle(result: CleanupResult, partial: Boolean): String = when {
    result.kind == CleanupResultKind.APP_CACHE ->
        stringResource(
            R.string.cleanup_result_app_cache_title,
            result.subjectLabel ?: stringResource(R.string.app_name),
        )
    result.kind == CleanupResultKind.SYSTEM_CACHE ->
        stringResource(R.string.cleanup_result_system_cache_title)
    partial -> stringResource(R.string.cleanup_result_partial_title)
    else -> stringResource(R.string.cleanup_result_title)
}

/** Counts that matter (files, skipped, restored, cancelled) in one black card. */
@Composable
private fun ResultDetails(result: CleanupResult) {
    val lines = buildList {
        if (result.kind == CleanupResultKind.FILES && result.deletedCount > 0) {
            add(pluralStringResource(R.plurals.cleanup_result_files_removed, result.deletedCount, result.deletedCount))
        }
        if (result.deletedBytes > 0L && result.beforeAvailableBytes > 0L && result.afterAvailableBytes > 0L) {
            add(
                stringResource(
                    R.string.cleanup_result_storage_snapshot,
                    ByteFormatter.format(result.beforeAvailableBytes),
                    ByteFormatter.format(result.afterAvailableBytes),
                ),
            )
        }
        if (result.changedCount > 0) {
            add(pluralStringResource(R.plurals.result_changed, result.changedCount, result.changedCount))
        }
        val notDeleted = (result.failedCount - result.changedCount).coerceAtLeast(0)
        if (notDeleted > 0) add(pluralStringResource(R.plurals.result_not_deleted, notDeleted, notDeleted))
        if (result.restoredCount > 0) {
            add(pluralStringResource(R.plurals.result_restored, result.restoredCount, result.restoredCount))
        }
        if (result.cancelledCount > 0) {
            add(pluralStringResource(R.plurals.cleanup_result_cancelled, result.cancelledCount, result.cancelledCount))
        }
    }
    if (lines.isEmpty()) return
    BlackCard(contentPadding = PaddingValues(16.dp)) {
        lines.forEach { line ->
            Text(
                line,
                color = OnBlack,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = AppWeights.Strong,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}
