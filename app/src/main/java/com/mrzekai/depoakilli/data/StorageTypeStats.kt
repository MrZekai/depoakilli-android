package com.mrzekai.depoakilli.data

import android.webkit.MimeTypeMap
import com.mrzekai.depoakilli.model.IndexedFile
import com.mrzekai.depoakilli.model.StorageFileType
import com.mrzekai.depoakilli.model.StorageTypeStat
import java.io.File

// File-type grouping for the storage analyzer (T3.5).
private const val TYPE_STATS_APK_MIME = "application/vnd.android.package-archive"
private const val TYPE_STATS_MAX_STORAGE_PREVIEWS_PER_TYPE = 80
private val TYPE_STATS_ARCHIVE_EXTENSIONS = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")
private val TYPE_STATS_DOCUMENT_EXTENSIONS = setOf(
    "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "csv", "epub",
)

internal fun smartMediaTypeStats(files: List<IndexedFile>): List<StorageTypeStat> {
    var imageCount = 0
    var imageBytes = 0L
    var videoCount = 0
    var videoBytes = 0L

    files.forEach { file ->
        val mime = StoragePathRules.normalizeText(file.mimeType)
        when {
            mime.startsWith("image/") -> {
                if (!isWhatsAppSharedMedia(file)) {
                    imageCount++
                    imageBytes += file.sizeBytes
                }
            }
            mime.startsWith("video/") -> {
                if (!isWhatsAppSharedMedia(file)) {
                    videoCount++
                    videoBytes += file.sizeBytes
                }
            }
        }
    }

    return buildList {
        if (videoCount > 0) add(StorageTypeStat(StorageFileType.VIDEOS, videoCount, videoBytes))
        if (imageCount > 0) add(StorageTypeStat(StorageFileType.IMAGES, imageCount, imageBytes))
    }
}

internal fun isWhatsAppSharedMedia(file: IndexedFile): Boolean =
    StoragePathRules.isWhatsAppPath(file.relativePath)

internal fun storageTypeStats(files: List<IndexedFile>): List<StorageTypeStat> {
    return files.groupBy(::storageFileType)
        .map { (type, typedFiles) ->
            StorageTypeStat(
                type = type,
                fileCount = typedFiles.size,
                totalBytes = typedFiles.sumOf(IndexedFile::sizeBytes),
            )
        }
        .sortedByDescending(StorageTypeStat::totalBytes)
}

internal fun storageTypePreviews(files: List<IndexedFile>): Map<StorageFileType, List<IndexedFile>> {
    return files.groupBy(::storageFileType)
        .mapValues { (_, typedFiles) ->
            typedFiles
                .sortedByDescending(IndexedFile::sizeBytes)
                .take(TYPE_STATS_MAX_STORAGE_PREVIEWS_PER_TYPE)
        }
}

internal fun storageFileType(file: IndexedFile): StorageFileType {
    val mime = StoragePathRules.normalizeText(file.mimeType)
    val extension = StoragePathRules.normalizeText(file.name.substringAfterLast('.', ""))
    return when {
        mime.startsWith("image/") -> StorageFileType.IMAGES
        mime.startsWith("video/") -> StorageFileType.VIDEOS
        mime.startsWith("audio/") -> StorageFileType.AUDIO
        extension == "apk" || mime == TYPE_STATS_APK_MIME -> StorageFileType.APK
        extension in TYPE_STATS_ARCHIVE_EXTENSIONS -> StorageFileType.ARCHIVES
        mime.startsWith("text/") || extension in TYPE_STATS_DOCUMENT_EXTENSIONS -> StorageFileType.DOCUMENTS
        else -> StorageFileType.OTHER
    }
}

internal fun mimeType(file: File): String {
    val extension = StoragePathRules.normalizeText(file.extension)
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension).orEmpty()
}
