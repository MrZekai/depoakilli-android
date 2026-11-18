package com.mrzekai.depoakilli.data

import android.content.Context
import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.model.IndexedFile

/**
 * Files the user chose to keep in the swipe review (V10). A record names the
 * file and its size and modification time, so the decision lapses when the
 * file changes. Only review groups are filtered with it; Auto groups never.
 */
class KeptItemsStore(
    private val load: () -> String?,
    private val save: (String) -> Unit,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()

    fun keys(): Set<String> = synchronized(lock) { read().keys }

    fun count(): Int = synchronized(lock) { read().size }

    fun add(key: String) = synchronized(lock) {
        val entries = read()
        entries.remove(key)
        entries[key] = nowMillis()
        while (entries.size > MAX_ENTRIES) {
            val oldest = entries.minByOrNull { it.value }?.key ?: break
            entries.remove(oldest)
        }
        write(entries)
    }

    fun remove(key: String) = synchronized(lock) {
        val entries = read()
        if (entries.remove(key) != null) write(entries)
    }

    fun clear() = synchronized(lock) { write(LinkedHashMap()) }

    /**
     * Drops records of files that are no longer on disk (or changed), but only
     * on the volumes this scan saw: decisions on an SD card that is not
     * inserted right now are kept.
     */
    fun retainOnly(existing: Set<String>, scannedVolumes: Set<String>) = synchronized(lock) {
        val entries = read()
        val removed = entries.keys.removeAll { key ->
            key !in existing && key.substringBefore('|') in scannedVolumes
        }
        if (removed) write(entries)
    }

    private fun read(): LinkedHashMap<String, Long> {
        val result = LinkedHashMap<String, Long>()
        load().orEmpty().lineSequence().filter(String::isNotBlank).forEach { line ->
            val tab = line.indexOf('\t')
            if (tab <= 0) return@forEach
            val time = line.substring(0, tab).toLongOrNull() ?: return@forEach
            result[line.substring(tab + 1)] = time
        }
        return result
    }

    private fun write(entries: Map<String, Long>) {
        save(entries.entries.joinToString("\n") { (key, time) -> "$time\t$key" })
    }

    companion object {
        const val MAX_ENTRIES = 5000
        private const val PREFS = "kept_items_v1"
        private const val KEY = "entries"

        fun create(context: Context): KeptItemsStore {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return KeptItemsStore(
                load = { prefs.getString(KEY, null) },
                save = { value -> prefs.edit().putString(KEY, value).apply() },
            )
        }

        fun keyOf(volumeRoot: String, relativePath: String, name: String, size: Long, modified: Long): String =
            listOf(volumeRoot, relativePath.replace('\\', '/').trim('/'), name, size.toString(), modified.toString())
                .joinToString("|")
                .replace('\t', ' ')
                .replace('\n', ' ')

        fun keyOf(item: CleanableItem): String =
            keyOf(item.volumeRoot, item.relativePath, item.name, item.sizeBytes, item.modifiedAtMillis)

        fun keyOf(file: IndexedFile): String =
            keyOf(file.volumeRoot, file.relativePath, file.name, file.sizeBytes, file.modifiedAtMillis)
    }
}
