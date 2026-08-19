package com.mrzekai.depoakilli.ui

import android.content.Context
import com.mrzekai.depoakilli.model.CleanCategory

internal data class DashboardSnapshot(
    val cleanableBytes: Long,
    val reviewBytes: Long,
    val categoryBytes: Map<CleanCategory, Long>,
    val analyzedAtMillis: Long,
)

internal class DashboardSnapshotStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(): DashboardSnapshot? {
        val analyzedAtMillis = preferences.getLong(KEY_ANALYZED_AT, 0L)
        if (analyzedAtMillis <= 0L) return null

        val categoryBytes = CleanCategory.values()
            .associateWith { category ->
                preferences.getLong(KEY_CATEGORY_PREFIX + category.name, 0L)
            }
            .filterValues { it > 0L }

        return DashboardSnapshot(
            cleanableBytes = preferences.getLong(KEY_CLEANABLE_BYTES, 0L).coerceAtLeast(0L),
            reviewBytes = preferences.getLong(KEY_REVIEW_BYTES, 0L).coerceAtLeast(0L),
            categoryBytes = categoryBytes,
            analyzedAtMillis = analyzedAtMillis,
        )
    }

    fun save(snapshot: DashboardSnapshot) {
        preferences.edit().apply {
            putLong(KEY_CLEANABLE_BYTES, snapshot.cleanableBytes.coerceAtLeast(0L))
            putLong(KEY_REVIEW_BYTES, snapshot.reviewBytes.coerceAtLeast(0L))
            putLong(KEY_ANALYZED_AT, snapshot.analyzedAtMillis.coerceAtLeast(0L))
            CleanCategory.values().forEach { category ->
                putLong(
                    KEY_CATEGORY_PREFIX + category.name,
                    snapshot.categoryBytes[category]?.coerceAtLeast(0L) ?: 0L,
                )
            }
        }.apply()
    }

    private companion object {
        const val PREFS_NAME = "dashboard_snapshot_v1"
        const val KEY_CLEANABLE_BYTES = "cleanable_bytes"
        const val KEY_REVIEW_BYTES = "review_bytes"
        const val KEY_ANALYZED_AT = "analyzed_at"
        const val KEY_CATEGORY_PREFIX = "category_"
    }
}
