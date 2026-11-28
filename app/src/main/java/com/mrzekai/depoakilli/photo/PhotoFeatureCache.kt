package com.mrzekai.depoakilli.photo

import android.content.Context
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * Measured facts per photo, keyed by MediaStore id + size + modified time, so
 * a second scan reads only new photos. Numbers only (hash, sharpness,
 * brightness, a quantized embedding); never a picture or a face. On this
 * device only.
 */
class PhotoFeatureCache(context: Context) {
    private val file = File(context.applicationContext.filesDir, "photo_features.bin")

    class Entry(
        val dHash: Long,
        val sharpness: Double,
        val luma: Float,
        var embedding: ByteArray?,
        var eyesOpen: Float = NO_VALUE,
        /** Faz 3B-2 photo type, see [PhotoReviewRules.encodeType]; -1 not looked at yet. */
        var typeCode: Int = -1,
    )

    private val entries: LinkedHashMap<String, Entry> = load()
    private var dirty = false

    fun key(item: PhotoItem) = "${item.id}|${item.sizeBytes}|${item.modifiedMillis}"

    @Synchronized
    fun get(item: PhotoItem): Entry? = entries[key(item)]

    @Synchronized
    fun put(item: PhotoItem, entry: Entry) {
        entries.remove(key(item))
        entries[key(item)] = entry
        dirty = true
    }

    @Synchronized
    fun markDirty() {
        dirty = true
    }

    @Synchronized
    fun save() {
        if (!dirty) return
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            DataOutputStream(BufferedOutputStream(tmp.outputStream())).use { out ->
                out.writeInt(VERSION)
                val keep = entries.entries.toList().takeLast(MAX_ENTRIES)
                out.writeInt(keep.size)
                keep.forEach { (k, e) ->
                    out.writeUTF(k)
                    out.writeLong(e.dHash)
                    out.writeDouble(e.sharpness)
                    out.writeFloat(e.luma)
                    out.writeFloat(e.eyesOpen)
                    out.writeByte(e.typeCode)
                    val emb = e.embedding
                    out.writeShort(emb?.size ?: 0)
                    if (emb != null) out.write(emb)
                }
            }
            tmp.renameTo(file)
            dirty = false
        }
    }

    private fun load(): LinkedHashMap<String, Entry> = runCatching {
        val map = LinkedHashMap<String, Entry>()
        if (!file.isFile) return@runCatching map
        DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
            if (input.readInt() != VERSION) return@runCatching map
            repeat(input.readInt()) {
                val k = input.readUTF()
                val hash = input.readLong()
                val sharp = input.readDouble()
                val luma = input.readFloat()
                val eyes = input.readFloat()
                val type = input.readByte().toInt()
                val n = input.readShort().toInt()
                val emb = if (n > 0) ByteArray(n).also { input.readFully(it) } else null
                map[k] = Entry(hash, sharp, luma, emb, eyes, type)
            }
        }
        map
    }.getOrDefault(LinkedHashMap())

    /** Forget everything (Settings > reset). */
    @Synchronized
    fun clear() {
        entries.clear()
        file.delete()
        dirty = false
    }

    companion object {
        private const val VERSION = 2
        private const val MAX_ENTRIES = 30_000
        const val NO_VALUE = -1f

        /** Looked at, no face. */
        const val NO_FACE = -2f
    }
}
