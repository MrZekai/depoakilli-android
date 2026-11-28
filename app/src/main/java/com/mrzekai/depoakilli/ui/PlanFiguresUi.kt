package com.mrzekai.depoakilli.ui

import com.mrzekai.depoakilli.model.InstalledAppEntry
import com.mrzekai.depoakilli.model.PlanFigures
import com.mrzekai.depoakilli.model.ScanFocus
import com.mrzekai.depoakilli.model.ScanSummary
import com.mrzekai.depoakilli.ui.smart.unusedAppsForSmartReview

/**
 * The Smart scan result, wherever it currently lives: the results area after a
 * Smart scan, or the stash a tool scan keeps while it borrows that area.
 */
internal fun CleanerUiState.smartSummary(): ScanSummary? = when {
    scanFocus == ScanFocus.SMART && summary.scannedFileCount > 0 -> summary
    else -> planSummary?.takeIf { it.scannedFileCount > 0 }
}

/** Unused apps on the findings list: only with Usage access and a loaded app list. */
internal fun CleanerUiState.findingsUnusedApps(): List<InstalledAppEntry>? =
    if (hasUsageAccess && installedApps.isNotEmpty()) unusedAppsForSmartReview(installedApps, true) else null

/** C1: Home, the findings list and the Tools tiles all read these figures. */
internal fun CleanerUiState.planFigures(): PlanFigures? =
    smartSummary()?.let { PlanFigures.of(it.plan, findingsUnusedApps()) }

/**
 * Faz 2.1: Pro's measured extra on this phone, from the current Smart scan
 * only. Before a scan, or before the free first "My pick" cleanup is spent,
 * there is nothing to add.
 */
internal fun CleanerUiState.proEstimate(
    videos: com.mrzekai.depoakilli.video.VideoCandidateFinder.Result? = null,
): com.mrzekai.depoakilli.pro.ProGainEstimator.Estimate {
    val figures = planFigures()
    val plan = summary.plan
    return com.mrzekai.depoakilli.pro.ProGainEstimator.estimate(
        suggestedBytes = figures?.suggestedBytes ?: 0L,
        suggestedCount = if (figures != null) plan.suggestedItems.size else 0,
        firstCleanupUsed = freeTier.firstCleanupUsed,
        videoSavingBytes = videos?.totalSavingBytes ?: 0L,
        videoCount = videos?.candidates?.size ?: 0,
    )
}
