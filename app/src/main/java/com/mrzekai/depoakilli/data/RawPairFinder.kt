package com.mrzekai.depoakilli.data

import com.mrzekai.depoakilli.model.IndexedFile

/**
 * RAW + JPEG pairs (V5): a .dng file whose photo also exists as .jpg/.jpeg/.heic
 * with the same base name in the same folder. Only the RAW file is offered;
 * an unpaired .dng is never a candidate.
 */
object RawPairFinder {
    private val RAW_EXTENSIONS = setOf("dng")
    private val PAIR_EXTENSIONS = setOf("jpg", "jpeg", "heic")

    /** RAW file uri → name of its processed partner. */
    fun find(files: List<IndexedFile>): Map<String, String> {
        val partners = HashMap<String, String>()
        files.forEach { file ->
            val extension = file.name.substringAfterLast('.', "").lowercase()
            if (extension in PAIR_EXTENSIONS && !StoragePathRules.isSystemMarkerFile(file.name)) {
                partners.putIfAbsent(key(file), file.name)
            }
        }
        if (partners.isEmpty()) return emptyMap()
        val result = LinkedHashMap<String, String>()
        files.forEach { file ->
            val extension = file.name.substringAfterLast('.', "").lowercase()
            if (extension in RAW_EXTENSIONS && !StoragePathRules.isSystemMarkerFile(file.name)) {
                partners[key(file)]?.let { result[file.uri] = it }
            }
        }
        return result
    }

    private fun key(file: IndexedFile): String =
        file.volumeRoot + "|" + StoragePathRules.normalizePath(file.relativePath) + "|" +
            file.name.substringBeforeLast('.').lowercase()
}
