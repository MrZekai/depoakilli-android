package com.mrzekai.depoakilli.data

import com.mrzekai.depoakilli.model.CleanupGroup
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

data class TrashEntry(
    val id: String,
    val originalPath: String,
    val trashPath: String,
    val sizeBytes: Long,
    val modifiedAtMillis: Long,
    val deletedAtMillis: Long,
    val group: String,
) {
    fun expiresAtMillis(): Long =
        deletedAtMillis + TimeUnit.DAYS.toMillis(CleaningThresholds.TRASH_RETENTION_DAYS.toLong())
}

data class RestoreResult(
    val restoredIds: Set<String>,
    val failedIds: Set<String>,
    val restoredBytes: Long,
    val restoredPaths: List<String>,
)

/**
 * In-app trash. Files are moved with a same-volume rename into
 * `<volume>/.SmartCleanerTrash/<uuid>` and removed after
 * [CleaningThresholds.TRASH_RETENTION_DAYS] days. A failed rename leaves the
 * file untouched: there is no copy-and-delete fallback and no silent
 * permanent deletion.
 *
 * Pure JVM: Android hooks (MediaStore removal, media rescan and the shared
 * storage safety gate) are injected.
 */
class TrashStore(
    private val indexFile: File,
    private val trashRoots: () -> List<File>,
    private val isAllowed: (File) -> Boolean,
    private val removeFromMediaStore: (String) -> Unit = {},
    private val rescan: (String) -> Unit = {},
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()

    fun moveToTrash(file: File, volumeRoot: File, group: CleanupGroup): TrashEntry? = synchronized(lock) {
        if (!file.isFile || !isAllowed(file)) return null
        val directory = trashDirectory(volumeRoot) ?: return null
        val size = file.length()
        val modified = file.lastModified()
        val id = UUID.randomUUID().toString()
        val target = File(directory, id)
        val entry = TrashEntry(
            id = id,
            originalPath = file.absolutePath,
            trashPath = target.absolutePath,
            sizeBytes = size,
            modifiedAtMillis = modified,
            deletedAtMillis = nowMillis(),
            group = group.name,
        )
        // Record first, move second: a file is never in the trash without a
        // record of where it came from. Each entry also gets a sidecar next to
        // the file so the index can be rebuilt if it is ever lost.
        val sidecar = sidecarOf(target)
        val previous = load()
        val recorded = runCatching { sidecar.writeText(TrashIndexCodec.encode(listOf(entry))); true }.getOrDefault(false) &&
            save(previous + entry)
        if (!recorded) {
            runCatching { sidecar.delete() }
            save(previous)
            return null
        }
        if (!runCatching { file.renameTo(target) }.getOrDefault(false)) {
            runCatching { sidecar.delete() }
            save(previous)
            return null
        }
        // The trash clock starts now, also for files adopted after a lost index.
        runCatching { target.setLastModified(nowMillis()) }
        removeFromMediaStore(file.absolutePath)
        entry
    }

    fun restore(ids: Collection<String>): RestoreResult = synchronized(lock) {
        val wanted = ids.toSet()
        val entries = load()
        val restored = hashSetOf<String>()
        val failed = hashSetOf<String>()
        val restoredPaths = ArrayList<String>()
        var restoredBytes = 0L
        for (entry in entries.filter { it.id in wanted }) {
            val source = File(entry.trashPath)
            if (entry.originalPath.isBlank()) {
                failed += entry.id
                continue
            }
            val target = freeTarget(File(entry.originalPath))
            val parent = target.parentFile
            val ok = source.isFile &&
                isAllowed(target) &&
                (parent == null || parent.isDirectory || parent.mkdirs()) &&
                runCatching { source.renameTo(target) }.getOrDefault(false)
            if (ok) {
                runCatching { target.setLastModified(entry.modifiedAtMillis) }
                runCatching { sidecarOf(source).delete() }
                restored += entry.id
                restoredBytes += entry.sizeBytes
                restoredPaths += target.absolutePath
                rescan(target.absolutePath)
            } else {
                failed += entry.id
            }
        }
        failed += wanted - entries.mapTo(hashSetOf(), TrashEntry::id)
        save(entries.filterNot { it.id in restored })
        RestoreResult(restored, failed, restoredBytes, restoredPaths)
    }

    /** Deletes entries older than the retention period and orphaned trash files. */
    fun purgeExpired(now: Long = nowMillis()): Long = synchronized(lock) {
        val entries = load()
        val (expired, kept) = entries.partition { it.expiresAtMillis() <= now }
        var freed = 0L
        val stillPresent = ArrayList(kept)
        for (entry in expired) {
            val file = File(entry.trashPath)
            when {
                !file.exists() -> Unit
                deleteTrashFile(file) -> freed += entry.sizeBytes
                else -> stillPresent += entry
            }
        }
        // Files without a record are never deleted blindly: they are adopted
        // back into the index (from their sidecar when present) and expire
        // like any other entry, visible in the Trash screen meanwhile.
        stillPresent += orphanEntries(stillPresent)
        save(stillPresent)
        freed
    }

    /** Permanently deletes the given entries (all when [ids] is null); returns bytes really removed. */
    fun emptyNow(ids: Collection<String>? = null): Long = synchronized(lock) {
        val entries = load()
        val wanted = ids?.toSet()
        var freed = 0L
        val remaining = ArrayList<TrashEntry>()
        for (entry in entries) {
            if (wanted != null && entry.id !in wanted) {
                remaining += entry
                continue
            }
            val file = File(entry.trashPath)
            when {
                !file.exists() -> Unit
                deleteTrashFile(file) -> freed += entry.sizeBytes
                else -> remaining += entry
            }
        }
        save(remaining)
        freed
    }

    fun entries(): List<TrashEntry> = synchronized(lock) { load() }

    fun trashBytes(): Long = entries().sumOf(TrashEntry::sizeBytes)

    private fun deleteTrashFile(file: File): Boolean {
        val parent = file.parentFile ?: return false
        if (parent.name != TRASH_DIRECTORY || !isAllowed(file)) return false
        val deleted = runCatching { file.delete() }.getOrDefault(false)
        if (deleted) runCatching { sidecarOf(file).delete() }
        return deleted
    }

    private fun trashDirectory(volumeRoot: File): File? {
        val directory = File(volumeRoot, TRASH_DIRECTORY)
        if (!directory.isDirectory && !directory.mkdirs()) return null
        runCatching {
            File(directory, NO_MEDIA).takeIf { !it.exists() }?.createNewFile()
            File(directory, README).takeIf { !it.exists() }?.writeText(README_TEXT)
        }
        return directory
    }

    private fun freeTarget(original: File): File {
        if (!original.exists()) return original
        val parent = original.parentFile
        val base = original.nameWithoutExtension
        val extension = original.extension.takeIf { it.isNotEmpty() }?.let { ".$it" }.orEmpty()
        var index = 1
        while (true) {
            val suffix = if (index == 1) " (restored)" else " (restored $index)"
            val candidate = File(parent, "$base$suffix$extension")
            if (!candidate.exists()) return candidate
            index++
        }
    }

    /**
     * Reads the index. A damaged index is kept as `.bak` and rebuilt from the
     * per-file sidecars and the files in the trash folders, so no trashed file
     * becomes unrestorable or is overwritten by the next save.
     */
    private fun load(): List<TrashEntry> {
        if (!indexFile.isFile) return emptyList()
        val text = runCatching { indexFile.readText() }.getOrNull()
        val decoded = text?.let { runCatching { TrashIndexCodec.decode(it) }.getOrNull() }
        if (decoded != null) return decoded
        runCatching { indexFile.copyTo(File(indexFile.parentFile, indexFile.name + ".bak"), overwrite = true) }
        val rebuilt = orphanEntries(emptyList())
        save(rebuilt)
        return rebuilt
    }

    private fun save(entries: List<TrashEntry>): Boolean = runCatching {
        indexFile.parentFile?.mkdirs()
        val temp = File(indexFile.parentFile, indexFile.name + ".tmp")
        temp.writeText(TrashIndexCodec.encode(entries))
        if (!temp.renameTo(indexFile)) {
            indexFile.delete()
            check(temp.renameTo(indexFile))
        }
        true
    }.getOrDefault(false)

    private fun sidecarOf(trashFile: File): File = File(trashFile.parentFile, trashFile.name + SIDECAR_SUFFIX)

    /** Entries for trash files that have no record in [known]. */
    private fun orphanEntries(known: List<TrashEntry>): List<TrashEntry> {
        val knownPaths = known.mapTo(hashSetOf()) { File(it.trashPath).absolutePath }
        val adopted = ArrayList<TrashEntry>()
        trashRoots().forEach { root ->
            File(root, TRASH_DIRECTORY).listFiles()?.forEach { file ->
                if (!file.isFile || file.name in SYSTEM_FILES || file.name.endsWith(SIDECAR_SUFFIX) ||
                    file.absolutePath in knownPaths
                ) return@forEach
                val fromSidecar = runCatching {
                    TrashIndexCodec.decode(sidecarOf(file).readText()).singleOrNull()
                }.getOrNull()
                adopted += fromSidecar?.copy(trashPath = file.absolutePath) ?: TrashEntry(
                    id = file.name,
                    originalPath = "",
                    trashPath = file.absolutePath,
                    sizeBytes = file.length(),
                    modifiedAtMillis = 0L,
                    // Unknown age: the full retention period starts now.
                    deletedAtMillis = nowMillis(),
                    group = "",
                )
            }
        }
        return adopted
    }

    companion object {
        const val TRASH_DIRECTORY = ".SmartCleanerTrash"
        private const val NO_MEDIA = ".nomedia"
        private const val README = "README.txt"
        private const val README_TEXT = "Smart Cleaner trash. Files here are removed automatically. Safe to delete.\n"
        private const val SIDECAR_SUFFIX = ".entry"
        private val SYSTEM_FILES = setOf(NO_MEDIA, README)
    }
}

/** Minimal JSON for the trash index (array of flat objects); no Android or third-party parser needed. */
internal object TrashIndexCodec {
    fun encode(entries: List<TrashEntry>): String = entries.joinToString(prefix = "[", postfix = "]", separator = ",") { e ->
        "{" + listOf(
            "\"id\":" + quote(e.id),
            "\"originalPath\":" + quote(e.originalPath),
            "\"trashPath\":" + quote(e.trashPath),
            "\"sizeBytes\":" + e.sizeBytes,
            "\"modifiedAtMillis\":" + e.modifiedAtMillis,
            "\"deletedAtMillis\":" + e.deletedAtMillis,
            "\"group\":" + quote(e.group),
        ).joinToString(",") + "}"
    }

    /** Throws on malformed input; callers treat that as an empty index. */
    fun decode(text: String): List<TrashEntry> {
        val parser = Parser(text)
        val result = ArrayList<TrashEntry>()
        parser.expect('[')
        if (parser.peek() == ']') {
            parser.expect(']')
            return result
        }
        while (true) {
            val fields = parser.readObject()
            result += TrashEntry(
                id = fields.getValue("id") as String,
                originalPath = fields.getValue("originalPath") as String,
                trashPath = fields.getValue("trashPath") as String,
                sizeBytes = fields.getValue("sizeBytes") as Long,
                modifiedAtMillis = fields.getValue("modifiedAtMillis") as Long,
                deletedAtMillis = fields.getValue("deletedAtMillis") as Long,
                group = fields["group"] as? String ?: "",
            )
            if (parser.peek() == ',') parser.expect(',') else break
        }
        parser.expect(']')
        return result
    }

    private fun quote(value: String): String = buildString {
        append('"')
        value.forEach { c ->
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c < ' ' -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
        }
        append('"')
    }

    private class Parser(private val text: String) {
        private var pos = 0

        fun peek(): Char {
            skipSpace()
            require(pos < text.length) { "unexpected end" }
            return text[pos]
        }

        fun expect(c: Char) {
            require(peek() == c) { "expected $c at $pos" }
            pos++
        }

        fun readObject(): Map<String, Any> {
            expect('{')
            val map = HashMap<String, Any>()
            if (peek() == '}') {
                pos++
                return map
            }
            while (true) {
                val key = readString()
                expect(':')
                map[key] = if (peek() == '"') readString() else readLong()
                if (peek() == ',') pos++ else break
            }
            expect('}')
            return map
        }

        private fun readString(): String {
            expect('"')
            val out = StringBuilder()
            while (true) {
                require(pos < text.length) { "unterminated string" }
                val c = text[pos++]
                when (c) {
                    '"' -> return out.toString()
                    '\\' -> {
                        val e = text[pos++]
                        when (e) {
                            'u' -> {
                                out.append(text.substring(pos, pos + 4).toInt(16).toChar())
                                pos += 4
                            }
                            'n' -> out.append('\n')
                            't' -> out.append('\t')
                            else -> out.append(e)
                        }
                    }
                    else -> out.append(c)
                }
            }
        }

        private fun readLong(): Long {
            skipSpace()
            val start = pos
            if (pos < text.length && text[pos] == '-') pos++
            while (pos < text.length && text[pos].isDigit()) pos++
            return text.substring(start, pos).toLong()
        }

        private fun skipSpace() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }
    }
}
