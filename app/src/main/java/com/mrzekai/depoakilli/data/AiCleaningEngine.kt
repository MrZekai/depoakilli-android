package com.mrzekai.depoakilli.data

import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.AiAssessment
import com.mrzekai.depoakilli.model.CleanCategory
import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.model.CleanupGroup
import com.mrzekai.depoakilli.model.IndexedFile
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * On-device cleanup rules (Smart plan engine).
 *
 * Every candidate is placed in exactly one [CleanupGroup] by the first matching
 * rule. Only Auto groups (installers of apps that are already installed with
 * the same or a newer version, verified extra copies in suitable locations,
 * generated preview caches, old temporary files and old unfinished downloads)
 * are preselected. Nothing inside WhatsApp, the camera folder or personal
 * documents is ever preselected; the user decides and confirms every deletion.
 */
class AiCleaningEngine(
    private val apkInfoReader: ApkInfoReader = ApkInfoReader.NONE,
    private val installedVersions: InstalledVersionLookup = InstalledVersionLookup.NONE,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    fun assess(file: IndexedFile): AiAssessment? {
        val ageDays = ageDays(file)
        val path = StoragePathRules.normalizePath(file.relativePath)
        val name = StoragePathRules.normalizeText(file.name)
        val mime = StoragePathRules.normalizeText(file.mimeType)
        val isVideo = mime.startsWith("video/")

        // 1. Protected user data is never a candidate.
        if (StoragePathRules.isProtectedUserDataPath(file.relativePath, file.name)) return null

        // 2. WhatsApp: review only, never Auto.
        if (StoragePathRules.isWhatsAppPath(path)) {
            return when {
                path.contains("/.statuses/") && ageDays >= CleaningThresholds.WHATSAPP_STATUS_DAYS ->
                    assessment(CleanupGroup.WHATSAPP_STATUS, ageDays)
                path.contains("/sent/") && ageDays >= CleaningThresholds.WHATSAPP_SENT_DAYS ->
                    assessment(CleanupGroup.WHATSAPP_SENT, ageDays)
                isVideo && file.sizeBytes >= CleaningThresholds.WHATSAPP_VIDEO_BYTES ->
                    assessment(CleanupGroup.WHATSAPP_VIDEO, ageDays)
                (path.contains("voice notes/") || name.startsWith("ptt-")) &&
                    ageDays >= CleaningThresholds.WHATSAPP_VOICE_DAYS ->
                    assessment(CleanupGroup.WHATSAPP_VOICE, ageDays)
                else -> null
            }
        }

        // 3. Generated preview caches in the standard media folders.
        if (THUMBNAIL_PREFIXES.any(path::startsWith)) {
            return assessment(CleanupGroup.THUMBNAILS, ageDays)
        }

        // 4. Old unfinished downloads.
        if (
            PARTIAL_DOWNLOAD_EXTENSIONS.any(name::endsWith) &&
            StoragePathRules.isDownloadPath(file.relativePath) &&
            ageDays >= CleaningThresholds.PARTIAL_DOWNLOAD_DAYS
        ) {
            return assessment(CleanupGroup.PARTIAL_DOWNLOADS, ageDays)
        }

        // 5. Old temporary files outside other apps' media folders.
        if (
            TEMP_EXTENSIONS.any(name::endsWith) &&
            ageDays >= CleaningThresholds.TEMP_DAYS &&
            !path.contains("/android/media/")
        ) {
            return assessment(CleanupGroup.TEMP_FILES, ageDays)
        }

        // 6. Installers.
        if (name.endsWith(".apk") || mime == APK_MIME) {
            val info = absolutePath(file)?.let(apkInfoReader::read)
            val installed = info?.let { installedVersions.versionOf(it.packageName) }
            val group = if (info != null && installed != null && installed >= info.versionCode) {
                CleanupGroup.APK_INSTALLED
            } else {
                CleanupGroup.APK_OTHER
            }
            return assessment(group, ageDays, apkPackage = info)
        }

        // 7. Screen recordings.
        if (isVideo && isScreenRecording(path, name) && ageDays >= CleaningThresholds.SCREEN_RECORDING_DAYS) {
            return assessment(CleanupGroup.SCREEN_RECORDING, ageDays)
        }

        // 8. Screenshots.
        if (isScreenshot(path, name, file.mimeType) && ageDays >= CleaningThresholds.SCREENSHOT_DAYS) {
            return assessment(CleanupGroup.SCREENSHOT, ageDays)
        }

        // 9. Large old videos.
        if (
            isVideo &&
            file.sizeBytes >= CleaningThresholds.LARGE_BYTES &&
            ageDays >= CleaningThresholds.LARGE_VIDEO_DAYS
        ) {
            return assessment(CleanupGroup.LARGE_VIDEO, ageDays)
        }

        // 10. Old downloads.
        if (StoragePathRules.isDownloadPath(file.relativePath) && ageDays >= CleaningThresholds.DOWNLOAD_DAYS) {
            val extension = name.substringAfterLast('.', "")
            val group = if (extension in DOCUMENT_EXTENSIONS) {
                CleanupGroup.DOWNLOAD_DOCUMENT
            } else {
                CleanupGroup.DOWNLOAD_FILE
            }
            return assessment(group, ageDays)
        }

        // 11. Other large files.
        if (file.sizeBytes >= CleaningThresholds.LARGE_BYTES) {
            return assessment(CleanupGroup.LARGE_OTHER, ageDays)
        }

        return null
    }

    /**
     * When several installers of the same app are present, every installer
     * below the highest version (and not already covered by [CleanupGroup.APK_INSTALLED])
     * becomes [CleanupGroup.APK_OLD_VERSION].
     */
    fun markOlderApkVersions(items: List<CleanableItem>): List<CleanableItem> {
        val newestByPackage = items.asSequence()
            .filter { it.assessment.group == CleanupGroup.APK_OTHER }
            .filter { it.assessment.apkPackageName != null && it.assessment.apkVersionCode != null }
            .groupBy { it.assessment.apkPackageName!! }
            .filterValues { it.size > 1 }
            .mapValues { (_, apks) -> apks.maxOf { it.assessment.apkVersionCode!! } }
        if (newestByPackage.isEmpty()) return items
        return items.map { item ->
            val current = item.assessment
            val newest = current.apkPackageName?.let(newestByPackage::get)
            val version = current.apkVersionCode
            if (current.group == CleanupGroup.APK_OTHER && newest != null && version != null && version < newest) {
                val old = assessment(CleanupGroup.APK_OLD_VERSION, current.reasonQuantity.toLong()).copy(
                    apkPackageName = current.apkPackageName,
                    apkVersionCode = version,
                )
                item.copy(assessment = old, selected = old.recommended)
            } else {
                item
            }
        }
    }

    /**
     * Broader review rules for Deep Clean.
     *
     * Deep Clean intentionally surfaces more candidates than Smart Clean, but
     * never preselects these lower-confidence review items.
     */
    fun assessDeep(file: IndexedFile): AiAssessment? {
        assess(file)?.let { return it }
        if (StoragePathRules.isProtectedUserDataPath(file.relativePath, file.name)) return null

        val ageDays = ageDays(file)
        val path = StoragePathRules.normalizePath(file.relativePath)
        val name = StoragePathRules.normalizeText(file.name)
        val isWhatsApp = StoragePathRules.isWhatsAppPath(path)

        if (isWhatsApp && path.contains("/sent/") && ageDays >= DEEP_WHATSAPP_SENT_DAYS) {
            return assessment(CleanupGroup.WHATSAPP_SENT, ageDays)
        }
        if (isWhatsApp) return null

        if (isScreenshot(path, name, file.mimeType) && ageDays >= DEEP_SCREENSHOT_DAYS) {
            return assessment(CleanupGroup.SCREENSHOT, ageDays)
        }

        if (StoragePathRules.isDownloadPath(file.relativePath) && ageDays >= DEEP_DOWNLOAD_DAYS) {
            val extension = name.substringAfterLast('.', "")
            return assessment(
                if (extension in DOCUMENT_EXTENSIONS) CleanupGroup.DOWNLOAD_DOCUMENT else CleanupGroup.DOWNLOAD_FILE,
                ageDays,
            )
        }

        if (file.sizeBytes >= CleaningThresholds.LARGE_BYTES) {
            return assessment(CleanupGroup.LARGE_OTHER, ageDays)
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
        val ageDays = ageDays(file)
        val path = StoragePathRules.normalizePath(file.relativePath)
        val name = StoragePathRules.normalizeText(file.name)
        if (!isScreenshot(path, name, file.mimeType)) return null

        return AiAssessment(
            category = CleanCategory.SCREENSHOT,
            safetyScore = when {
                ageDays >= 90 -> 88
                ageDays >= 30 -> 78
                else -> 70
            },
            reasonRes = R.string.reason_screenshot_review,
            recommended = false,
            group = CleanupGroup.SCREENSHOT,
        )
    }

    /** Fully verified copy. Auto only when [DuplicatePolicy.autoSelectable] allows it. */
    fun duplicateAssessment(automaticSelectionIsSafe: Boolean): AiAssessment = assessment(
        if (automaticSelectionIsSafe) CleanupGroup.DUPLICATE_SAFE else CleanupGroup.DUPLICATE_PERSONAL,
        ageDays = 0,
    )

    /** Same size and matching samples only; never selectable as a group. */
    fun sampledDuplicateAssessment(): AiAssessment = assessment(CleanupGroup.DUPLICATE_UNVERIFIED, ageDays = 0)

    fun assessment(group: CleanupGroup, ageDays: Long, apkPackage: ApkInfo? = null): AiAssessment {
        val quantity = ageDays.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
        return AiAssessment(
            category = categoryOf(group),
            safetyScore = if (group.auto) 95 else 70,
            reasonRes = group.reasonRes,
            reasonArgs = emptyList(),
            recommended = group.auto,
            reasonPluralRes = group.reasonPluralRes,
            reasonQuantity = quantity,
            group = group,
            apkPackageName = apkPackage?.packageName,
            apkVersionCode = apkPackage?.versionCode,
        )
    }

    private fun isScreenRecording(path: String, name: String): Boolean =
        SCREEN_RECORDING_FOLDERS.any(path::contains) ||
            SCREEN_RECORDING_NAME_PREFIXES.any(name::startsWith)

    private fun isScreenshot(path: String, name: String, mimeType: String): Boolean {
        // Many devices save screen recordings (.mp4) into the Screenshots
        // folder. A recording is not a screenshot and must not be labelled or
        // grouped as one.
        if (!isStillImage(name, mimeType)) return false
        return path.contains("/screenshots/") ||
            path.contains("/screenshot/") ||
            name.startsWith("screenshot") ||
            name.startsWith("screen_shot") ||
            name.startsWith("screencap") ||
            name.startsWith("ekran_goruntusu") ||
            name.startsWith("ekran goruntusu")
    }

    private fun isStillImage(name: String, mimeType: String): Boolean {
        val mime = StoragePathRules.normalizeText(mimeType)
        if (mime.isNotEmpty()) return mime.startsWith("image/")
        return name.substringAfterLast('.', "") in SCREENSHOT_IMAGE_EXTENSIONS
    }

    private fun absolutePath(file: IndexedFile): String? = runCatching {
        val uri = URI(file.uri)
        if (uri.scheme != "file") null else uri.path
    }.getOrNull()

    /** Age from the newer of creation and modification time. */
    fun ageDays(file: IndexedFile): Long {
        val reference = maxOf(file.createdAtMillis, file.modifiedAtMillis)
        if (reference <= 0L) return 0
        return TimeUnit.MILLISECONDS.toDays((nowMillis() - reference).coerceAtLeast(0))
    }

    companion object {
        private const val APK_MIME = "application/vnd.android.package-archive"
        private const val DEEP_SCREENSHOT_DAYS = 7L
        private const val DEEP_DOWNLOAD_DAYS = 30L
        private const val DEEP_WHATSAPP_SENT_DAYS = 14L
        private val THUMBNAIL_PREFIXES = listOf(
            "/dcim/.thumbnails/",
            "/pictures/.thumbnails/",
            "/movies/.thumbnails/",
            "/music/.thumbnails/",
        )
        private val PARTIAL_DOWNLOAD_EXTENSIONS = listOf(".crdownload", ".part", ".download")
        private val TEMP_EXTENSIONS = listOf(".tmp", ".temp")
        private val SCREEN_RECORDING_FOLDERS = listOf(
            "/screenrecord",
            "/screen recordings/",
            "/screenshots/",
            "/screenrecorder/",
        )
        private val SCREEN_RECORDING_NAME_PREFIXES = listOf("screen_recording", "screenrecord", "screen-")
        private val SCREENSHOT_IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "heic", "heif")
        val DOCUMENT_EXTENSIONS = setOf(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "csv", "epub",
            "jpg", "jpeg", "png", "heic", "webp",
        )

        fun categoryOf(group: CleanupGroup): CleanCategory = when (group) {
            CleanupGroup.THUMBNAILS,
            CleanupGroup.TEMP_FILES,
            CleanupGroup.PARTIAL_DOWNLOADS -> CleanCategory.JUNK
            CleanupGroup.APK_INSTALLED,
            CleanupGroup.APK_OLD_VERSION,
            CleanupGroup.APK_OTHER -> CleanCategory.APK_PACKAGE
            CleanupGroup.DUPLICATE_SAFE,
            CleanupGroup.DUPLICATE_PERSONAL,
            CleanupGroup.DUPLICATE_UNVERIFIED -> CleanCategory.DUPLICATE
            CleanupGroup.LARGE_VIDEO,
            CleanupGroup.SCREEN_RECORDING,
            CleanupGroup.LARGE_OTHER -> CleanCategory.LARGE_FILE
            CleanupGroup.SCREENSHOT -> CleanCategory.SCREENSHOT
            CleanupGroup.DOWNLOAD_FILE,
            CleanupGroup.DOWNLOAD_DOCUMENT -> CleanCategory.OLD_DOWNLOAD
            CleanupGroup.WHATSAPP_SENT,
            CleanupGroup.WHATSAPP_VIDEO,
            CleanupGroup.WHATSAPP_VOICE,
            CleanupGroup.WHATSAPP_STATUS -> CleanCategory.WHATSAPP_MEDIA
        }
    }
}
