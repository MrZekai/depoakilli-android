package com.mrzekai.depoakilli.ui

import android.content.Context

data class CleanupHistorySnapshot(
    val lastCleanupAtMillis: Long = 0L,
    val lastDeletedBytes: Long = 0L,
    val lastDeletedCount: Int = 0,
    val totalDeletedBytes: Long = 0L,
    val totalDeletedCount: Int = 0,
    val cleanupCount: Int = 0,
) {
    val hasHistory: Boolean
        get() = lastCleanupAtMillis > 0L && lastDeletedCount > 0
}

internal class CleanupHistoryStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(): CleanupHistorySnapshot = CleanupHistorySnapshot(
        lastCleanupAtMillis = preferences.getLong(KEY_LAST_AT, 0L).coerceAtLeast(0L),
        lastDeletedBytes = preferences.getLong(KEY_LAST_BYTES, 0L).coerceAtLeast(0L),
        lastDeletedCount = preferences.getInt(KEY_LAST_COUNT, 0).coerceAtLeast(0),
        totalDeletedBytes = preferences.getLong(KEY_TOTAL_BYTES, 0L).coerceAtLeast(0L),
        totalDeletedCount = preferences.getInt(KEY_TOTAL_COUNT, 0).coerceAtLeast(0),
        cleanupCount = preferences.getInt(KEY_CLEANUP_COUNT, 0).coerceAtLeast(0),
    )

    fun record(
        deletedBytes: Long,
        deletedCount: Int,
        timestampMillis: Long = System.currentTimeMillis(),
    ): CleanupHistorySnapshot {
        if (deletedCount <= 0) return load()

        val current = load()
        val safeBytes = deletedBytes.coerceAtLeast(0L)
        val safeCount = deletedCount.coerceAtLeast(0)

        val updated = CleanupHistorySnapshot(
            lastCleanupAtMillis = timestampMillis.coerceAtLeast(0L),
            lastDeletedBytes = safeBytes,
            lastDeletedCount = safeCount,
            totalDeletedBytes = safeAdd(current.totalDeletedBytes, safeBytes),
            totalDeletedCount = safeAdd(current.totalDeletedCount, safeCount),
            cleanupCount = safeAdd(current.cleanupCount, 1),
        )

        preferences.edit()
            .putLong(KEY_LAST_AT, updated.lastCleanupAtMillis)
            .putLong(KEY_LAST_BYTES, updated.lastDeletedBytes)
            .putInt(KEY_LAST_COUNT, updated.lastDeletedCount)
            .putLong(KEY_TOTAL_BYTES, updated.totalDeletedBytes)
            .putInt(KEY_TOTAL_COUNT, updated.totalDeletedCount)
            .putInt(KEY_CLEANUP_COUNT, updated.cleanupCount)
            .apply()

        return updated
    }

    private fun safeAdd(left: Long, right: Long): Long {
        if (right <= 0L) return left.coerceAtLeast(0L)
        val safeLeft = left.coerceAtLeast(0L)
        return if (Long.MAX_VALUE - safeLeft < right) Long.MAX_VALUE else safeLeft + right
    }

    private fun safeAdd(left: Int, right: Int): Int {
        if (right <= 0) return left.coerceAtLeast(0)
        val safeLeft = left.coerceAtLeast(0)
        return if (Int.MAX_VALUE - safeLeft < right) Int.MAX_VALUE else safeLeft + right
    }

    private companion object {
        const val PREFS_NAME = "cleanup_history_v1"
        const val KEY_LAST_AT = "last_cleanup_at"
        const val KEY_LAST_BYTES = "last_deleted_bytes"
        const val KEY_LAST_COUNT = "last_deleted_count"
        const val KEY_TOTAL_BYTES = "total_deleted_bytes"
        const val KEY_TOTAL_COUNT = "total_deleted_count"
        const val KEY_CLEANUP_COUNT = "cleanup_count"
    }
}
