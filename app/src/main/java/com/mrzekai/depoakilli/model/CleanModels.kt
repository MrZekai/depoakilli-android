package com.mrzekai.depoakilli.model

import androidx.annotation.StringRes
import com.mrzekai.depoakilli.R

enum class CleanCategory(
    @StringRes val titleRes: Int,
    @StringRes val shortDescriptionRes: Int,
) {
    DUPLICATE(R.string.category_duplicates, R.string.category_duplicates_description),
    SCREENSHOT(R.string.category_screenshots, R.string.category_screenshots_description),
    LARGE_VIDEO(R.string.category_large_videos, R.string.category_large_videos_description),
    OLD_DOWNLOAD(R.string.category_old_downloads, R.string.category_old_downloads_description),
    APK_PACKAGE(R.string.category_apk_packages, R.string.category_apk_packages_description),
    APP_CACHE(R.string.category_app_cache, R.string.category_app_cache_description),
}

data class IndexedFile(
    val uri: String,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val modifiedAtMillis: Long,
    val relativePath: String,
)

data class AiAssessment(
    val category: CleanCategory,
    val safetyScore: Int,
    @StringRes val reasonRes: Int,
    val reasonArgs: List<Any> = emptyList(),
    val recommended: Boolean,
)

data class CleanableItem(
    val id: String,
    val uri: String,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val modifiedAtMillis: Long,
    val relativePath: String,
    val assessment: AiAssessment,
    val selected: Boolean = true,
)

data class StorageSnapshot(
    val totalBytes: Long = 0,
    val availableBytes: Long = 0,
) {
    val usedBytes: Long get() = (totalBytes - availableBytes).coerceAtLeast(0)
    val usedFraction: Float
        get() = if (totalBytes == 0L) 0f else usedBytes.toFloat() / totalBytes.toFloat()
}

data class MemorySnapshot(
    val totalBytes: Long = 0,
    val availableBytes: Long = 0,
    val appUsedBytes: Long = 0,
    val lowMemory: Boolean = false,
) {
    val usedBytes: Long get() = (totalBytes - availableBytes).coerceAtLeast(0)
    val usedFraction: Float
        get() = if (totalBytes == 0L) 0f else usedBytes.toFloat() / totalBytes.toFloat()
}

data class AppCacheEntry(
    val packageName: String,
    val label: String,
    val cacheBytes: Long,
)

data class AppCacheSnapshot(
    val supported: Boolean = true,
    val accessGranted: Boolean = false,
    val entries: List<AppCacheEntry> = emptyList(),
    val scannedAppCount: Int = 0,
    val reportedOtherAppsCacheBytes: Long = 0L,
) {
    val totalCacheBytes: Long
        get() = maxOf(
            reportedOtherAppsCacheBytes,
            entries.sumOf(AppCacheEntry::cacheBytes),
        )
}

data class ScanSummary(
    val items: List<CleanableItem> = emptyList(),
    val scannedFileCount: Int = 0,
    val limitedAccess: Boolean = false,
) {
    val selectedItems: List<CleanableItem> get() = items.filter(CleanableItem::selected)
    val selectedBytes: Long get() = selectedItems.sumOf(CleanableItem::sizeBytes)
    val totalSuggestedBytes: Long get() = items.sumOf(CleanableItem::sizeBytes)
    val byCategory: Map<CleanCategory, List<CleanableItem>> get() = items.groupBy { it.assessment.category }
}

object ByteFormatter {
    private val units = listOf("B", "KB", "MB", "GB", "TB")

    fun format(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        var value = bytes.toDouble()
        var unitIndex = 0
        while (value >= 1024 && unitIndex < units.lastIndex) {
            value /= 1024
            unitIndex++
        }
        return if (value >= 100 || unitIndex == 0) {
            "%.0f %s".format(value, units[unitIndex])
        } else {
            "%.1f %s".format(value, units[unitIndex])
        }
    }
}
