package com.mrzekai.depoakilli.data

import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.AiAssessment
import com.mrzekai.depoakilli.model.CleanCategory
import com.mrzekai.depoakilli.model.IndexedFile
import java.util.concurrent.TimeUnit

/**
 * Conservative on-device rules used by Smart Scan.
 *
 * The engine only preselects items that are strongly likely to be disposable
 * (temporary files, stale installers, WhatsApp statuses, and unambiguous exact
 * duplicate copies). User-created downloads, screenshots, large files and sent
 * media are shown for review but are not silently selected.
 */
class AiCleaningEngine(
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    fun assess(file: IndexedFile): AiAssessment? {
        val ageDays = ageDays(file.modifiedAtMillis)
        val path = StoragePathRules.normalizePath(file.relativePath)
        val name = StoragePathRules.normalizeText(file.name)
        val mime = StoragePathRules.normalizeText(file.mimeType)

        val isWhatsApp = path.contains("whatsapp")
        val isWhatsAppStatus = isWhatsApp && path.contains("/.statuses/")
        val isWhatsAppSent = isWhatsApp && (path.contains("/sent/") || path.endsWith("/sent"))
        if (isWhatsAppStatus || (isWhatsAppSent && ageDays >= 30)) {
            return AiAssessment(
                category = CleanCategory.WHATSAPP_MEDIA,
                safetyScore = if (isWhatsAppStatus) 98 else 78,
                reasonRes = if (isWhatsAppStatus) R.string.reason_whatsapp_status else R.string.reason_whatsapp_sent,
                reasonArgs = if (isWhatsAppStatus) emptyList() else listOf(ageDays),
                recommended = isWhatsAppStatus,
            )
        }

        if (isTemporary(file, path, name, ageDays)) {
            return AiAssessment(
                category = CleanCategory.JUNK,
                safetyScore = if (path.contains("/.thumbnails/")) 97 else 94,
                reasonRes = if (path.contains("/.thumbnails/")) {
                    R.string.reason_thumbnail_cache
                } else {
                    R.string.reason_temporary_file
                },
                reasonArgs = listOf(ageDays),
                recommended = true,
            )
        }

        if (name.endsWith(".apk") || mime == APK_MIME) {
            val oldEnough = ageDays >= 7
            return AiAssessment(
                category = CleanCategory.APK_PACKAGE,
                safetyScore = if (ageDays >= 30) 96 else if (oldEnough) 91 else 70,
                reasonRes = if (oldEnough) R.string.reason_old_installer else R.string.reason_downloaded_installer,
                reasonArgs = if (oldEnough) listOf(ageDays) else emptyList(),
                recommended = oldEnough,
            )
        }

        val isScreenshot = isScreenshot(path, name)
        if (isScreenshot && ageDays >= 14) {
            return AiAssessment(
                category = CleanCategory.SCREENSHOT,
                safetyScore = if (ageDays >= 90) 88 else 78,
                reasonRes = R.string.reason_old_screenshot,
                reasonArgs = listOf(ageDays),
                recommended = false,
            )
        }

        val isDownload = StoragePathRules.isDownloadPath(file.relativePath)
        if (isDownload && ageDays >= 90) {
            return AiAssessment(
                category = CleanCategory.OLD_DOWNLOAD,
                safetyScore = if (ageDays >= 365) 84 else 72,
                reasonRes = R.string.reason_old_download,
                reasonArgs = listOf(ageDays),
                recommended = false,
            )
        }

        if (file.sizeBytes >= LARGE_FILE_BYTES) {
            return AiAssessment(
                category = CleanCategory.LARGE_FILE,
                safetyScore = if (ageDays >= 180) 76 else 60,
                reasonRes = R.string.reason_large_file,
                reasonArgs = listOf(ageDays.coerceAtLeast(0)),
                recommended = false,
            )
        }

        return null
    }

    /**
     * Broader review rules for Deep Clean.
     *
     * Deep Clean intentionally surfaces more candidates than Smart Clean, but
     * never preselects these lower-confidence review items.
     */
    fun assessDeep(file: IndexedFile): AiAssessment? {
        assess(file)?.let { return it }

        val ageDays = ageDays(file.modifiedAtMillis)
        val path = StoragePathRules.normalizePath(file.relativePath)
        val name = StoragePathRules.normalizeText(file.name)

        val isWhatsAppSent = path.contains("whatsapp") &&
            (path.contains("/sent/") || path.endsWith("/sent"))
        if (isWhatsAppSent && ageDays >= DEEP_WHATSAPP_SENT_DAYS) {
            return AiAssessment(
                category = CleanCategory.WHATSAPP_MEDIA,
                safetyScore = 64,
                reasonRes = R.string.reason_whatsapp_sent,
                reasonArgs = listOf(ageDays),
                recommended = false,
            )
        }

        val isScreenshot = isScreenshot(path, name)
        if (isScreenshot && ageDays >= DEEP_SCREENSHOT_DAYS) {
            return AiAssessment(
                category = CleanCategory.SCREENSHOT,
                safetyScore = 62,
                reasonRes = R.string.reason_old_screenshot,
                reasonArgs = listOf(ageDays),
                recommended = false,
            )
        }

        val isDownload = StoragePathRules.isDownloadPath(file.relativePath)
        if (isDownload && ageDays >= DEEP_DOWNLOAD_DAYS) {
            return AiAssessment(
                category = CleanCategory.OLD_DOWNLOAD,
                safetyScore = 58,
                reasonRes = R.string.reason_old_download,
                reasonArgs = listOf(ageDays),
                recommended = false,
            )
        }

        if (file.sizeBytes >= DEEP_LARGE_FILE_BYTES) {
            return AiAssessment(
                category = CleanCategory.LARGE_FILE,
                safetyScore = 50,
                reasonRes = R.string.reason_large_file,
                reasonArgs = listOf(ageDays.coerceAtLeast(0)),
                recommended = false,
            )
        }

        return null
    }


    /**
     * Dedicated Screenshots tool policy.
     *
     * Every recognized accessible screenshot is visible regardless of age.
     * Screenshots are user-created content, so nothing is ever preselected.
     */
    fun assessFocusedScreenshot(file: IndexedFile): AiAssessment? {
        val ageDays = ageDays(file.modifiedAtMillis)
        val path = StoragePathRules.normalizePath(file.relativePath)
        val name = StoragePathRules.normalizeText(file.name)
        if (!isScreenshot(path, name)) return null

        return AiAssessment(
            category = CleanCategory.SCREENSHOT,
            safetyScore = when {
                ageDays >= 90 -> 88
                ageDays >= 30 -> 78
                else -> 70
            },
            reasonRes = R.string.reason_screenshot_review,
            recommended = false,
        )
    }

    fun duplicateAssessment(automaticSelectionIsSafe: Boolean): AiAssessment = AiAssessment(
        category = CleanCategory.DUPLICATE,
        safetyScore = if (automaticSelectionIsSafe) 99 else 86,
        reasonRes = R.string.reason_exact_duplicate,
        recommended = automaticSelectionIsSafe,
    )


    private fun isScreenshot(path: String, name: String): Boolean {
        return path.contains("/screenshots/") ||
            path.contains("/screenshot/") ||
            path.endsWith("/screenshots") ||
            path.endsWith("/screenshot") ||
            name.startsWith("screenshot") ||
            name.startsWith("screen_shot") ||
            name.startsWith("screencap") ||
            name.startsWith("ekran_goruntusu") ||
            name.startsWith("ekran goruntusu")
    }

    private fun isTemporary(
        file: IndexedFile,
        path: String,
        name: String,
        ageDays: Long,
    ): Boolean {
        if (ageDays < TEMP_MIN_AGE_DAYS) return false
        val tempExtension = TEMP_EXTENSIONS.any(name::endsWith)
        val generatedThumbnail = path.contains("/.thumbnails/")
        val mediaMime = StoragePathRules.normalizeText(file.mimeType)
        val mediaFile = mediaMime.startsWith("image/") || mediaMime.startsWith("video/")
        val tempFolder = !mediaFile &&
            (path.contains("/temp/") || path.contains("/tmp/") || path.contains("/temporary/"))
        val knownTinyArtifact = file.sizeBytes <= 10L * 1024L * 1024L &&
            (name == "thumbs.db" || name.endsWith(".log.old") || name.endsWith(".bak.tmp"))
        return tempExtension || generatedThumbnail || tempFolder || knownTinyArtifact
    }

    private fun ageDays(modifiedAtMillis: Long): Long {
        if (modifiedAtMillis <= 0L) return 0
        return TimeUnit.MILLISECONDS.toDays((nowMillis() - modifiedAtMillis).coerceAtLeast(0))
    }

    companion object {
        private const val APK_MIME = "application/vnd.android.package-archive"
        private const val LARGE_FILE_BYTES = 100L * 1024L * 1024L
        private const val DEEP_LARGE_FILE_BYTES = 50L * 1024L * 1024L
        private const val DEEP_SCREENSHOT_DAYS = 7L
        private const val DEEP_DOWNLOAD_DAYS = 30L
        private const val DEEP_WHATSAPP_SENT_DAYS = 14L
        private const val TEMP_MIN_AGE_DAYS = 3L
        private val TEMP_EXTENSIONS = listOf(".tmp", ".temp", ".part", ".crdownload", ".download", ".cache")
    }
}
