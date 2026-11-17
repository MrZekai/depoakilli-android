package com.mrzekai.depoakilli.model

import com.mrzekai.depoakilli.data.CleaningThresholds

/**
 * Faz 5C "one number" rule (C1). Every big figure is a suggested amount and
 * comes from here, so Home, the findings list and the tool tiles always agree:
 *
 * - Home "up to X" = [homeMaxBytes] = tier 1 + the findings list.
 * - Findings giant = [findingsBytes] = X − tier 1 (My pick + I'll ask you +
 *   unused apps). Unused apps count in both, or in neither when unknown.
 */
data class PlanFigures(
    /** Tier 1: preselected Auto groups, cleaned with Home's big button. */
    val safeBytes: Long,
    /** Tier 2: "My pick". */
    val suggestedBytes: Long,
    /** Tier 3: files the user decides on. */
    val askBytes: Long,
    /** Unused apps on the findings list (only with Usage access). */
    val unusedAppsBytes: Long,
) {
    val findingsBytes: Long get() = suggestedBytes + askBytes + unusedAppsBytes
    val homeMaxBytes: Long get() = safeBytes + findingsBytes

    /** The "My pick" card appears only for an amount worth a tap (C2). */
    val showsSuggestion: Boolean get() = suggestedBytes >= CleaningThresholds.PLAN_MIN_BYTES

    companion object {
        fun of(plan: CleanupPlan, unusedApps: List<InstalledAppEntry>?): PlanFigures = PlanFigures(
            safeBytes = plan.recommendedBytes,
            suggestedBytes = plan.suggestedBytes,
            askBytes = plan.askBytes,
            unusedAppsBytes = unusedApps.orEmpty().sumOf(InstalledAppEntry::totalBytes),
        )
    }
}

/** Which tool a suggested file belongs to (C1, C3). */
enum class SuggestionTool { LARGE_FILES, DOWNLOADS, WHATSAPP, DUPLICATES, SCREENSHOTS }

/**
 * The single source of a tool's big number (C1): the Smart scan's suggested
 * files (tier 1 and tier 2) that belong to the tool. The Tools tile and the
 * tool screen both call [suggestedFor] / [suggestedBytes] with the same plan.
 */
object ToolSuggestion {
    private val DOWNLOAD_GROUPS = setOf(
        CleanupGroup.DOWNLOAD_FILE,
        CleanupGroup.DOWNLOAD_DOCUMENT,
        CleanupGroup.PARTIAL_DOWNLOADS,
        CleanupGroup.APK_INSTALLED,
        CleanupGroup.APK_OLD_VERSION,
        CleanupGroup.APK_OTHER,
    )
    private val WHATSAPP_GROUPS = setOf(CleanupGroup.WHATSAPP_SENT, CleanupGroup.WHATSAPP_STATUS)
    private val DUPLICATE_GROUPS = setOf(CleanupGroup.DUPLICATE_SAFE, CleanupGroup.DUPLICATE_PERSONAL)

    fun toolOf(focus: ScanFocus): SuggestionTool? = when (focus) {
        ScanFocus.LARGE_FILES -> SuggestionTool.LARGE_FILES
        ScanFocus.DOWNLOADS, ScanFocus.APKS -> SuggestionTool.DOWNLOADS
        ScanFocus.DUPLICATES -> SuggestionTool.DUPLICATES
        ScanFocus.SCREENSHOTS -> SuggestionTool.SCREENSHOTS
        else -> null
    }

    fun suggestedFor(tool: SuggestionTool, plan: CleanupPlan): List<CleanableItem> {
        val picks = plan.autoItems + plan.suggestedItems
        return picks.filter { item ->
            val group = item.assessment.group
            when (tool) {
                SuggestionTool.LARGE_FILES -> item.sizeBytes >= CleaningThresholds.LARGE_BYTES
                SuggestionTool.DOWNLOADS -> group in DOWNLOAD_GROUPS
                SuggestionTool.WHATSAPP -> group in WHATSAPP_GROUPS
                SuggestionTool.DUPLICATES -> group in DUPLICATE_GROUPS
                SuggestionTool.SCREENSHOTS -> group == CleanupGroup.SCREENSHOT
            }
        }.sortedByDescending(CleanableItem::sizeBytes)
    }

    fun suggestedBytes(tool: SuggestionTool, plan: CleanupPlan): Long =
        suggestedFor(tool, plan).sumOf(CleanableItem::sizeBytes)
}
