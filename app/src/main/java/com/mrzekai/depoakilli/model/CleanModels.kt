package com.mrzekai.depoakilli.model

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.mrzekai.depoakilli.R
import java.util.Locale

enum class CleanCategory(
    @StringRes val titleRes: Int,
    @StringRes val shortDescriptionRes: Int,
) {
    JUNK(R.string.category_junk, R.string.category_junk_description),
    DUPLICATE(R.string.category_duplicates, R.string.category_duplicates_description),
    SCREENSHOT(R.string.category_screenshots, R.string.category_screenshots_description),
    LARGE_FILE(R.string.category_large_files, R.string.category_large_files_description),
    OLD_DOWNLOAD(R.string.category_old_downloads, R.string.category_old_downloads_description),
    APK_PACKAGE(R.string.category_apk_packages, R.string.category_apk_packages_description),
    APP_CACHE(R.string.category_app_cache, R.string.category_app_cache_description),
    WHATSAPP_MEDIA(R.string.category_whatsapp, R.string.category_whatsapp_description),
}

enum class DeleteMode { PERMANENT, TRASH }

/**
 * Rows of the cleanup plan. Auto groups are preselected; Review groups are
 * listed for the user to decide. Nothing is deleted without confirmation.
 */
enum class CleanupGroup(
    @StringRes val titleRes: Int,
    @StringRes val reasonRes: Int,
    @PluralsRes val reasonPluralRes: Int?,
    val auto: Boolean,
    val deleteMode: DeleteMode,
    val groupSelectable: Boolean = true,
) {
    APK_INSTALLED(R.string.plan_group_apk_installed, R.string.reason_apk_installed, null, true, DeleteMode.PERMANENT),
    DUPLICATE_SAFE(R.string.plan_group_duplicate_safe, R.string.reason_exact_duplicate, null, true, DeleteMode.PERMANENT),
    THUMBNAILS(R.string.plan_group_thumbnails, 0, R.plurals.reason_thumbnail_cache, true, DeleteMode.PERMANENT),
    TEMP_FILES(R.string.plan_group_temp_files, 0, R.plurals.reason_temporary_file, true, DeleteMode.PERMANENT),
    PARTIAL_DOWNLOADS(R.string.plan_group_partial_downloads, 0, R.plurals.reason_partial_download, true, DeleteMode.TRASH),
    APK_OLD_VERSION(R.string.plan_group_apk_old_version, R.string.reason_apk_old_version, null, false, DeleteMode.TRASH),
    APK_OTHER(R.string.plan_group_apk_other, R.string.reason_downloaded_installer, null, false, DeleteMode.TRASH),
    DUPLICATE_PERSONAL(R.string.plan_group_duplicate_personal, R.string.reason_exact_duplicate, null, false, DeleteMode.TRASH),
    DUPLICATE_UNVERIFIED(R.string.plan_group_duplicate_unverified, R.string.reason_sampled_duplicate, null, false, DeleteMode.TRASH, groupSelectable = false),
    LARGE_VIDEO(R.string.plan_group_large_video, 0, R.plurals.reason_large_file, false, DeleteMode.TRASH),
    SCREEN_RECORDING(R.string.plan_group_screen_recording, 0, R.plurals.reason_screen_recording, false, DeleteMode.TRASH),
    SCREENSHOT(R.string.plan_group_screenshot, 0, R.plurals.reason_old_screenshot, false, DeleteMode.TRASH),
    DOWNLOAD_FILE(R.string.plan_group_download_file, 0, R.plurals.reason_old_download, false, DeleteMode.TRASH),
    DOWNLOAD_DOCUMENT(R.string.plan_group_download_document, 0, R.plurals.reason_old_download, false, DeleteMode.TRASH, groupSelectable = false),
    LARGE_OTHER(R.string.plan_group_large_other, 0, R.plurals.reason_large_file, false, DeleteMode.TRASH),
    WHATSAPP_SENT(R.string.plan_group_whatsapp_sent, 0, R.plurals.reason_whatsapp_sent, false, DeleteMode.TRASH),
    WHATSAPP_VIDEO(R.string.plan_group_whatsapp_video, R.string.reason_whatsapp_video, null, false, DeleteMode.TRASH),
    WHATSAPP_VOICE(R.string.plan_group_whatsapp_voice, 0, R.plurals.reason_whatsapp_voice, false, DeleteMode.TRASH),
    WHATSAPP_STATUS(R.string.plan_group_whatsapp_status, R.string.reason_whatsapp_status, null, false, DeleteMode.TRASH),
    ;

    companion object {
        /** Fallback used by tool screens that build assessments without the plan engine. */
        fun fromCategory(category: CleanCategory): CleanupGroup = when (category) {
            CleanCategory.JUNK -> TEMP_FILES
            CleanCategory.DUPLICATE -> DUPLICATE_PERSONAL
            CleanCategory.SCREENSHOT -> SCREENSHOT
            CleanCategory.LARGE_FILE -> LARGE_OTHER
            CleanCategory.OLD_DOWNLOAD -> DOWNLOAD_FILE
            CleanCategory.APK_PACKAGE -> APK_OTHER
            CleanCategory.APP_CACHE -> LARGE_OTHER
            CleanCategory.WHATSAPP_MEDIA -> WHATSAPP_SENT
        }
    }
}

enum class ScanFocus {
    SMART,
    DEEP,
    JUNK,
    DUPLICATES,
    LARGE_FILES,
    WHATSAPP,
    MEDIA,
    SCREENSHOTS,
    DOWNLOADS,
    APKS,
    ANALYZE,
}

enum class WhatsAppMediaCategory(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
) {
    IMAGES(R.string.whatsapp_category_images, R.string.whatsapp_category_images_description),
    VIDEOS(R.string.whatsapp_category_videos, R.string.whatsapp_category_videos_description),
    DOCUMENTS(R.string.whatsapp_category_documents, R.string.whatsapp_category_documents_description),
    AUDIO(R.string.whatsapp_category_audio, R.string.whatsapp_category_audio_description),
    VOICE_NOTES(R.string.whatsapp_category_voice, R.string.whatsapp_category_voice_description),
    STICKERS_GIFS(R.string.whatsapp_category_stickers, R.string.whatsapp_category_stickers_description),
    STATUSES(R.string.whatsapp_category_statuses, R.string.whatsapp_category_statuses_description),
    OTHER(R.string.whatsapp_category_other, R.string.whatsapp_category_other_description),
}

enum class StorageFileType(@StringRes val titleRes: Int) {
    IMAGES(R.string.storage_type_images),
    VIDEOS(R.string.storage_type_videos),
    AUDIO(R.string.storage_type_audio),
    DOCUMENTS(R.string.storage_type_documents),
    ARCHIVES(R.string.storage_type_archives),
    APK(R.string.storage_type_apk),
    OTHER(R.string.storage_type_other),
}

data class IndexedFile(
    val uri: String,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val modifiedAtMillis: Long,
    val relativePath: String,
    val volumeRoot: String = "",
    val createdAtMillis: Long = 0,
)



data class StorageReviewItem(
    val file: IndexedFile,
    val selected: Boolean = false,
) {
    val id: String get() = file.uri
}

data class StorageReviewSummary(
    val type: StorageFileType? = null,
    val excludeWhatsAppMedia: Boolean = false,
    val items: List<StorageReviewItem> = emptyList(),
    val scannedFileCount: Int = 0,
    val scanLimitReached: Boolean = false,
    val loading: Boolean = false,
) {
    val totalBytes: Long get() = items.sumOf { it.file.sizeBytes }
    val selectedItems: List<StorageReviewItem> get() = items.filter(StorageReviewItem::selected)
    val selectedBytes: Long get() = selectedItems.sumOf { it.file.sizeBytes }
}

data class AiAssessment(
    val category: CleanCategory,
    val safetyScore: Int,
    @StringRes val reasonRes: Int,
    val reasonArgs: List<Any> = emptyList(),
    val recommended: Boolean,
    val reasonPluralRes: Int? = null,
    val reasonQuantity: Int = 0,
    val group: CleanupGroup = CleanupGroup.fromCategory(category),
    val apkPackageName: String? = null,
    val apkVersionCode: Long? = null,
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
    val protectedDuplicateName: String? = null,
    val protectedDuplicateUri: String? = null,
    val protectedDuplicateSizeBytes: Long? = null,
    val volumeRoot: String = "",
    val protectedDuplicateRelativePath: String? = null,
    val protectedDuplicateVolumeRoot: String = "",
)

data class WhatsAppMediaItem(
    val id: String,
    val uri: String,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val modifiedAtMillis: Long,
    val relativePath: String,
    val category: WhatsAppMediaCategory,
    val selected: Boolean = false,
)

data class WhatsAppLibrarySummary(
    val items: List<WhatsAppMediaItem> = emptyList(),
    val scannedFileCount: Int = 0,
) {
    val totalBytes: Long get() = items.sumOf(WhatsAppMediaItem::sizeBytes)
    val selectedItems: List<WhatsAppMediaItem> get() = items.filter(WhatsAppMediaItem::selected)
    val selectedBytes: Long get() = selectedItems.sumOf(WhatsAppMediaItem::sizeBytes)
    val byCategory: Map<WhatsAppMediaCategory, List<WhatsAppMediaItem>>
        get() = items.groupBy(WhatsAppMediaItem::category)
}

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
    val lowMemoryThresholdBytes: Long = 0L,
) {
    val usedBytes: Long get() = (totalBytes - availableBytes).coerceAtLeast(0)
    val usedFraction: Float
        get() = if (totalBytes == 0L) 0f else usedBytes.toFloat() / totalBytes.toFloat()
}

data class DeviceInfoSnapshot(
    val manufacturer: String = "",
    val model: String = "",
    val androidVersion: String = "",
    val sdkLevel: Int = 0,
    val cpuAbi: String = "",
    val cpuCores: Int = 0,
    val screenResolution: String = "",
    val batteryPercent: Int = 0,
    val batteryTemperatureCelsius: Float = 0f,
    val batteryCharging: Boolean = false,
    val appVersion: String = "",
)

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

data class InstalledAppEntry(
    val packageName: String,
    val label: String,
    val appBytes: Long = 0L,
    val dataBytes: Long = 0L,
    val cacheBytes: Long = 0L,
    val lastUsedMillis: Long = 0L,
    /** Preinstalled with the device; Android does not let the user uninstall it. */
    val systemApp: Boolean = false,
) {
    val totalBytes: Long get() = (appBytes + dataBytes).coerceAtLeast(cacheBytes)
}

data class StorageTypeStat(
    val type: StorageFileType,
    val fileCount: Int,
    val totalBytes: Long,
)

data class ScanSummary(
    val items: List<CleanableItem> = emptyList(),
    val scannedFileCount: Int = 0,
    val scannedBytes: Long = 0L,
    val limitedAccess: Boolean = false,
    val scanLimitReached: Boolean = false,
    val storageTypes: List<StorageTypeStat> = emptyList(),
    val storagePreviews: Map<StorageFileType, List<IndexedFile>> = emptyMap(),
    val smartMediaTypes: List<StorageTypeStat> = emptyList(),
    val truncated: Boolean = false,
) {
    /** Cleanup plan derived from [items]; always reflects the current selection. */
    val plan: CleanupPlan by lazy(LazyThreadSafetyMode.NONE) { buildPlan(items, truncated) }
    val selectedItems: List<CleanableItem> by lazy(LazyThreadSafetyMode.NONE) {
        items.filter(CleanableItem::selected)
    }
    val selectedBytes: Long by lazy(LazyThreadSafetyMode.NONE) {
        selectedItems.sumOf(CleanableItem::sizeBytes)
    }
    val safeSuggestedBytes: Long by lazy(LazyThreadSafetyMode.NONE) { plan.recommendedBytes }
    val reviewItems: List<CleanableItem> by lazy(LazyThreadSafetyMode.NONE) {
        items.filterNot { it.assessment.recommended }
    }
    val reviewBytes: Long by lazy(LazyThreadSafetyMode.NONE) {
        reviewItems.sumOf(CleanableItem::sizeBytes)
    }
    val totalSuggestedBytes: Long by lazy(LazyThreadSafetyMode.NONE) {
        items.sumOf(CleanableItem::sizeBytes)
    }
    val byCategory: Map<CleanCategory, List<CleanableItem>> by lazy(LazyThreadSafetyMode.NONE) {
        items.groupBy { it.assessment.category }
    }
}

object ByteFormatter {
    private val units = listOf("B", "KB", "MB", "GB", "TB")

    fun format(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        var value = bytes.toDouble()
        var unitIndex = 0
        while (value >= 1000 && unitIndex < units.lastIndex) {
            value /= 1000
            unitIndex++
        }
        return if (value >= 100 || unitIndex == 0) {
            String.format(Locale.ROOT, "%.0f %s", value, units[unitIndex])
        } else {
            String.format(Locale.ROOT, "%.1f %s", value, units[unitIndex])
        }
    }
}
