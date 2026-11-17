package com.mrzekai.depoakilli.data

import com.mrzekai.depoakilli.model.FolderSize
import com.mrzekai.depoakilli.model.IndexedFile

/**
 * Biggest folders (V7) from the scan index: every file counts toward its
 * folder cut to at most [maxDepth] levels below the volume root (so
 * DCIM/Camera/2024 counts as DCIM/Camera/2024, and A/B/C/D as A/B/C).
 * App-private folders, Smart Cleaner's Trash and files at the volume root
 * are left out.
 */
object FolderSizes {
    fun top(files: List<IndexedFile>, limit: Int = 5, maxDepth: Int = 3): List<FolderSize> {
        val totals = HashMap<Pair<String, String>, LongArray>()
        files.forEach { file ->
            val segments = file.relativePath.replace('\\', '/').split('/').filter(String::isNotBlank)
            if (segments.isEmpty()) return@forEach
            val folder = segments.take(maxDepth).joinToString("/")
            if (isExcluded(folder)) return@forEach
            val total = totals.getOrPut(file.volumeRoot to folder) { LongArray(2) }
            total[0] += file.sizeBytes
            total[1] += 1
        }
        return totals.entries
            .sortedByDescending { it.value[0] }
            .take(limit)
            .map { (key, total) ->
                FolderSize(
                    volumeRoot = key.first,
                    relativePath = key.second,
                    name = key.second.substringAfterLast('/'),
                    bytes = total[0],
                    fileCount = total[1].toInt(),
                )
            }
    }

    private fun isExcluded(folder: String): Boolean {
        val normalized = StoragePathRules.normalizePath(folder)
        return StoragePathRules.isProtectedAppPrivatePath(folder) ||
            normalized.startsWith("/.smartcleanertrash/") ||
            normalized.split('/').any { it.startsWith(".") }
    }

    /** True when [file] lies in [folder] or below it. */
    fun contains(folder: FolderSize, file: IndexedFile): Boolean {
        if (file.volumeRoot != folder.volumeRoot) return false
        val path = file.relativePath.replace('\\', '/').trim('/')
        return path == folder.relativePath || path.startsWith(folder.relativePath + "/")
    }
}
