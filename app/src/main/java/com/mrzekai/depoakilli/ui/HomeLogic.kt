package com.mrzekai.depoakilli.ui


/**
 * The four looks of Home (T5.1). FIRST_RUN, SCANNING and PLAN each show one
 * giant figure and one giant button; HARD and TIDY appear when the plan has
 * nothing worth deleting.
 */
internal enum class HomeMode {
    /** No all-files access yet: "Your phone is 85 % full" and MAKE ROOM. */
    FIRST_RUN,

    /** A Smart scan runs: "Found so far" with the measured total. */
    SCANNING,

    /** A plan (fresh or saved) can free something: "Up to X" and CLEAN UP. */
    PLAN,

    /** Nothing easy left but storage is ≥ 70 % full: what is taking space. */
    HARD,

    /** Nothing worth deleting and storage below 70 %: one sentence and "Check again". */
    TIDY,

    /** Access granted but no scan and no saved summary yet: MAKE ROOM starts the scan. */
    READY,
}

/** Pure Home decisions (T5.1), covered by unit tests. */
internal object HomeLogic {
    /** At or above this fill level Home never says the phone is tidy. */
    const val FULL_PERCENT = 70

    fun usedPercent(totalBytes: Long, availableBytes: Long): Int? {
        if (totalBytes <= 0L) return null
        val used = (totalBytes - availableBytes).coerceIn(0L, totalBytes)
        return ((used * 100L) / totalBytes).toInt().coerceIn(0, 100)
    }

    fun mode(
        hasAccess: Boolean,
        scanning: Boolean,
        hasPlan: Boolean,
        planIsTidy: Boolean,
        usedPercent: Int?,
    ): HomeMode = when {
        !hasAccess -> HomeMode.FIRST_RUN
        scanning -> HomeMode.SCANNING
        !hasPlan -> HomeMode.READY
        !planIsTidy -> HomeMode.PLAN
        // Unknown fill level: never claim the phone is tidy.
        usedPercent == null || usedPercent >= FULL_PERCENT -> HomeMode.HARD
        else -> HomeMode.TIDY
    }

    /** What the giant button does on the PLAN look. */
    enum class CleanAction {
        /** Safe files are selected: open the "Move to Trash?" confirmation. */
        CONFIRM_SAFE,

        /** Nothing safe is selected: go straight to the findings (T5.4). */
        OPEN_FINDINGS,

        /** Only a saved summary is known: run a quick scan first. */
        SCAN,
    }

    fun cleanAction(hasLiveItems: Boolean, selectedBytes: Long): CleanAction = when {
        !hasLiveItems -> CleanAction.SCAN
        selectedBytes > 0L -> CleanAction.CONFIRM_SAFE
        else -> CleanAction.OPEN_FINDINGS
    }
}
