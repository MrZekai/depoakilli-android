package com.mrzekai.depoakilli.plan

import java.time.Instant
import java.time.ZoneId

/**
 * Faz 3C ready-made filters (chips instead of a free-text box). Within one
 * row the chips are alternatives; rows combine. Showing is not deleting: the
 * result opens in the usual review screen. Pure; unit-tested.
 */
enum class FilterType { PHOTO, VIDEO, SCREENSHOT, DOWNLOADED, AUDIO, DOCUMENT }

enum class FilterSource { CAMERA, WHATSAPP, OTHER_APPS, DOWNLOADS }

enum class FilterDate { THIS_YEAR, LAST_YEAR, OLDER_6_MONTHS, OLDER_1_YEAR }

enum class FilterSize(val minBytes: Long) { MB10(10_000_000L), MB100(100_000_000L), GB1(1_000_000_000L) }

/** What a file is, as the filters see it (from the storage index). */
data class FilterFile(val name: String, val relativePath: String, val mimeType: String, val sizeBytes: Long, val modifiedMillis: Long)

data class FileFilter(
    val types: Set<FilterType> = emptySet(),
    val sources: Set<FilterSource> = emptySet(),
    val date: FilterDate? = null,
    val size: FilterSize? = null,
) {
    val isEmpty: Boolean get() = types.isEmpty() && sources.isEmpty() && date == null && size == null

    fun matches(f: FilterFile, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (types.isNotEmpty() && typesOf(f).none { it in types }) return false
        if (sources.isNotEmpty() && sourceOf(f) !in sources) return false
        if (size != null && f.sizeBytes < size.minBytes) return false
        val d = date
        if (d != null) {
            val year = Instant.ofEpochMilli(f.modifiedMillis).atZone(zone).year
            val nowYear = Instant.ofEpochMilli(nowMillis).atZone(zone).year
            val age = nowMillis - f.modifiedMillis
            val ok = when (d) {
                FilterDate.THIS_YEAR -> year == nowYear
                FilterDate.LAST_YEAR -> year == nowYear - 1
                FilterDate.OLDER_6_MONTHS -> age >= 182L * DAY
                FilterDate.OLDER_1_YEAR -> age >= 365L * DAY
            }
            if (!ok) return false
        }
        return true
    }

    companion object {
        private const val DAY = 86_400_000L
        private val DOCS = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "csv", "odt", "ods", "epub")

        fun typesOf(f: FilterFile): Set<FilterType> {
            val out = HashSet<FilterType>()
            val path = f.relativePath.lowercase()
            val ext = f.name.substringAfterLast('.', "").lowercase()
            when {
                f.mimeType.startsWith("image/") ->
                    out += if (path.contains("screenshot") || f.name.lowercase().startsWith("screenshot")) FilterType.SCREENSHOT else FilterType.PHOTO
                f.mimeType.startsWith("video/") -> out += FilterType.VIDEO
                f.mimeType.startsWith("audio/") -> out += FilterType.AUDIO
                ext in DOCS -> out += FilterType.DOCUMENT
            }
            if (isDownloads(path)) out += FilterType.DOWNLOADED
            return out
        }

        fun sourceOf(f: FilterFile): FilterSource {
            val path = f.relativePath.lowercase().trim('/')
            return when {
                path.contains("whatsapp") -> FilterSource.WHATSAPP
                isDownloads(path) -> FilterSource.DOWNLOADS
                path == "dcim" || path.startsWith("dcim/") && !path.contains("screenshot") -> FilterSource.CAMERA
                else -> FilterSource.OTHER_APPS
            }
        }

        private fun isDownloads(path: String) = path.trim('/').let { it == "download" || it.startsWith("download/") }

        /** "Last year's screenshots". */
        val LAST_YEAR_SCREENSHOTS = FileFilter(types = setOf(FilterType.SCREENSHOT), date = FilterDate.LAST_YEAR)

        /** "Big videos from WhatsApp". */
        val WHATSAPP_BIG_VIDEOS = FileFilter(types = setOf(FilterType.VIDEO), sources = setOf(FilterSource.WHATSAPP), size = FilterSize.MB10)

        /** "Downloads older than a year". */
        val OLD_DOWNLOADS = FileFilter(sources = setOf(FilterSource.DOWNLOADS), date = FilterDate.OLDER_1_YEAR)
    }
}
