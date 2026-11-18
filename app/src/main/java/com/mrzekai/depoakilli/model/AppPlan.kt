package com.mrzekai.depoakilli.model

import java.util.concurrent.TimeUnit

/**
 * Faz 6 (E1): the apps that take the most space, as rows of the findings list.
 * The app never removes an app or deletes inside one; each row only opens
 * Android's own uninstall confirmation or the app's storage settings.
 */
object AppPlan {
    /** At most this many app rows. */
    const val MAX_ROWS = 5

    /** Only apps of at least this size get a row (500 MB). */
    const val MIN_BYTES = 500L * 1024L * 1024L

    /** Not opened for this long: the row offers Android's uninstall screen. */
    const val UNUSED_DAYS = 30L

    enum class Action {
        /** Android's uninstall confirmation (ACTION_DELETE). */
        UNINSTALL,

        /** The app's storage settings; most of it is saved data. */
        SETTINGS_DATA_HEAVY,

        /** The app's storage settings. */
        SETTINGS,
    }

    data class Row(val app: InstalledAppEntry, val action: Action, val unusedDays: Long)

    /**
     * The rows, biggest first: no system apps, not this app, at least
     * [MIN_BYTES], at most [MAX_ROWS]. Without Usage access the sizes are not
     * known, so there are no rows (the screen offers access instead).
     */
    fun rows(
        apps: List<InstalledAppEntry>,
        hasUsageAccess: Boolean,
        ownPackage: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<Row>? {
        if (!hasUsageAccess) return null
        return apps.asSequence()
            .filterNot(InstalledAppEntry::systemApp)
            .filterNot { it.packageName == ownPackage }
            .filter { it.totalBytes >= MIN_BYTES }
            .sortedWith(compareByDescending<InstalledAppEntry> { it.totalBytes }.thenBy { it.label.lowercase() })
            .take(MAX_ROWS)
            .map { app -> Row(app, actionFor(app, nowMillis), unusedDays(app, nowMillis)) }
            .toList()
    }

    fun unusedDays(app: InstalledAppEntry, nowMillis: Long): Long =
        if (app.lastUsedMillis <= 0L) 0L else TimeUnit.MILLISECONDS.toDays((nowMillis - app.lastUsedMillis).coerceAtLeast(0L))

    /**
     * Unused for [UNUSED_DAYS] → uninstall; in use with mostly saved data
     * (data, cache included, over half the total) → storage settings
     * ("mostly saved data"); otherwise → storage settings.
     */
    fun actionFor(app: InstalledAppEntry, nowMillis: Long): Action = when {
        app.lastUsedMillis > 0L && unusedDays(app, nowMillis) >= UNUSED_DAYS -> Action.UNINSTALL
        app.dataBytes.coerceAtLeast(app.cacheBytes) * 2L > app.totalBytes -> Action.SETTINGS_DATA_HEAVY
        else -> Action.SETTINGS
    }

    /**
     * What apps hold beyond the "up to X" promise (E1): only apps the user
     * still uses. Unused apps are already in X (C1); used apps are never added
     * to any total because the app cannot delete their data itself.
     */
    fun moreBytes(rows: List<Row>?, unusedInPlan: List<InstalledAppEntry>?): Long {
        val unused = unusedInPlan.orEmpty().mapTo(HashSet(), InstalledAppEntry::packageName)
        return rows.orEmpty().filter { it.app.packageName !in unused }.sumOf { it.app.totalBytes }
    }
}
