package com.mrzekai.depoakilli.data

import android.content.Context

/** A folder the user asked never to be suggested from (V11). */
data class ExcludedFolder(val volumeRoot: String, val relativePath: String) {
    val name: String get() = relativePath.substringAfterLast('/').ifBlank { "/" }

    /** True for files in this folder or below it, on the same volume. */
    fun contains(fileVolumeRoot: String, fileRelativePath: String): Boolean {
        if (fileVolumeRoot != volumeRoot) return false
        val path = normalize(fileRelativePath)
        return relativePath.isEmpty() || path == relativePath || path.startsWith("$relativePath/")
    }

    companion object {
        fun normalize(path: String): String = path.replace('\\', '/').trim('/')
    }
}

/**
 * "Don't suggest files from this folder" (V11). Review groups only; Auto
 * groups (temporary files, previews, installed APKs, safe copies) still apply.
 * At most [MAX_FOLDERS] folders.
 */
class ExcludedFoldersStore(
    private val load: () -> String?,
    private val save: (String) -> Unit,
) {
    private val lock = Any()

    fun folders(): List<ExcludedFolder> = synchronized(lock) { read() }

    /** False when the list is full. */
    fun add(folder: ExcludedFolder): Boolean = synchronized(lock) {
        val clean = folder.copy(relativePath = ExcludedFolder.normalize(folder.relativePath))
        val list = read()
        if (clean in list) return@synchronized true
        if (list.size >= MAX_FOLDERS) return@synchronized false
        write(list + clean)
        true
    }

    fun remove(folder: ExcludedFolder) = synchronized(lock) { write(read() - folder) }

    private fun read(): List<ExcludedFolder> = load().orEmpty().lineSequence()
        .filter(String::isNotBlank)
        .mapNotNull { line ->
            val tab = line.indexOf('\t')
            if (tab < 0) null else ExcludedFolder(line.substring(0, tab), line.substring(tab + 1))
        }
        .distinct()
        .toList()

    private fun write(list: List<ExcludedFolder>) {
        save(list.joinToString("\n") { "${it.volumeRoot}\t${it.relativePath}" })
    }

    companion object {
        const val MAX_FOLDERS = 50
        private const val PREFS = "excluded_folders_v1"
        private const val KEY = "folders"

        fun create(context: Context): ExcludedFoldersStore {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return ExcludedFoldersStore(
                load = { prefs.getString(KEY, null) },
                save = { value -> prefs.edit().putString(KEY, value).apply() },
            )
        }
    }
}
