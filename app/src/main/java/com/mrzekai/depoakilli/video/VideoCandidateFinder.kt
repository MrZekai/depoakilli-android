package com.mrzekai.depoakilli.video

import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.MediaStore
import com.mrzekai.depoakilli.data.ExcludedFoldersStore
import com.mrzekai.depoakilli.data.KeptItemsStore
import com.mrzekai.depoakilli.data.TrashStore
import java.io.File
import org.json.JSONObject

/**
 * Finds the videos worth shrinking: MediaStore for every video of 100 MB or
 * more, then headers only (cached by path + size + modified time, so a second
 * pass reads only new videos). Nothing is decoded or changed.
 */
class VideoCandidateFinder(private val context: Context) {
    private val cache = VideoMetaCache(context)

    data class Result(
        val candidates: List<Candidate>,
        /** All big videos looked at, for the "N skipped" honesty line. */
        val looked: Int,
        val hevcAvailable: Boolean,
    ) {
        val totalSavingBytes: Long get() = candidates.sumOf(Candidate::estimatedSavingBytes)
    }

    fun find(
        pendingOriginals: Set<String> = emptySet(),
        shouldStop: () -> Boolean = { false },
    ): Result {
        if (!Environment.isExternalStorageManager()) return Result(emptyList(), 0, false)
        val hevc = VideoProbe.hevcEncoderAvailable()
        val kept = runCatching { KeptItemsStore.create(context).keys() }.getOrDefault(emptySet())
        val excluded = runCatching { ExcludedFoldersStore.create(context).folders() }.getOrDefault(emptyList())
        val storage = context.getSystemService(StorageManager::class.java)
        val candidates = mutableListOf<Candidate>()
        var looked = 0
        val projection = arrayOf(
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.RELATIVE_PATH,
        )
        runCatching {
            context.contentResolver.query(
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
                projection,
                "${MediaStore.Video.Media.SIZE} >= ?",
                arrayOf(VideoRules.MIN_VIDEO_BYTES.toString()),
                "${MediaStore.Video.Media.SIZE} DESC",
            )?.use { cursor ->
                val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val pathIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.RELATIVE_PATH)
                while (cursor.moveToNext() && !shouldStop()) {
                    val file = File(cursor.getString(dataIndex) ?: continue)
                    if (!file.isFile) continue
                    looked++
                    val relative = cursor.getString(pathIndex).orEmpty().trim('/')
                    val volumeRoot = runCatching { storage?.getStorageVolume(file)?.directory?.absolutePath }.getOrNull()
                        ?: Environment.getExternalStorageDirectory().absolutePath
                    val meta = cache.get(file) ?: VideoProbe.read(file, relative, cursor.getString(mimeIndex))?.also { cache.put(it) }
                    meta ?: continue
                    val sibling = File(file.parentFile, VideoRules.outputName(file.name))
                    val videoContext = VideoContext(
                        inTrash = relative.split('/').any { it.equals(TrashStore.TRASH_DIRECTORY, ignoreCase = true) },
                        kept = KeptItemsStore.keyOf(volumeRoot, relative, file.name, file.length(), file.lastModified()) in kept,
                        excludedFolder = excluded.any { it.contains(volumeRoot, relative) },
                        alreadyShrunk = sibling.exists() || file.absolutePath in pendingOriginals || cache.wasShrunk(file.absolutePath),
                    )
                    VideoRules.candidate(meta, videoContext, hevc)?.let(candidates::add)
                }
            }
        }
        cache.save()
        return Result(candidates.sortedByDescending(Candidate::estimatedSavingBytes), looked, hevc)
    }

    /** After an approval or a "keep the original", the video is never offered again. */
    fun markShrunk(originalPath: String) {
        cache.markShrunk(originalPath)
        cache.save()
    }
}

/** Header facts per video, keyed by path + size + modified time; on this device only. */
internal class VideoMetaCache(context: Context) {
    private val file = File(context.applicationContext.filesDir, "video_meta_cache.json")
    private val entries: MutableMap<String, JSONObject> = load()
    private val shrunk: MutableSet<String> = loadShrunk()
    private var dirty = false

    private fun key(path: String, size: Long, modified: Long) = "$path|$size|$modified"

    fun get(file: File): VideoMeta? = entries[key(file.absolutePath, file.length(), file.lastModified())]?.let(::decode)

    fun put(meta: VideoMeta) {
        entries[key(meta.path, meta.sizeBytes, meta.modifiedMillis)] = encode(meta)
        dirty = true
    }

    fun wasShrunk(path: String): Boolean = path in shrunk

    fun markShrunk(path: String) {
        if (shrunk.add(path)) dirty = true
    }

    @Synchronized
    fun save() {
        if (!dirty) return
        runCatching {
            val root = JSONObject()
            val all = JSONObject()
            // Keeps the cache small: the newest 2000 videos.
            entries.entries.toList().takeLast(MAX_ENTRIES).forEach { (k, v) -> all.put(k, v) }
            root.put("entries", all)
            root.put("shrunk", org.json.JSONArray(shrunk.toList().takeLast(MAX_ENTRIES)))
            file.writeText(root.toString())
            dirty = false
        }
    }

    private fun load(): MutableMap<String, JSONObject> = runCatching {
        val root = JSONObject(file.readText())
        val all = root.optJSONObject("entries") ?: JSONObject()
        val map = LinkedHashMap<String, JSONObject>()
        all.keys().forEach { k -> all.optJSONObject(k)?.let { map[k] = it } }
        map
    }.getOrDefault(LinkedHashMap())

    private fun loadShrunk(): MutableSet<String> = runCatching {
        val array = JSONObject(file.readText()).optJSONArray("shrunk") ?: return@runCatching LinkedHashSet<String>()
        (0 until array.length()).mapTo(LinkedHashSet()) { array.getString(it) }
    }.getOrDefault(LinkedHashSet())

    private fun encode(m: VideoMeta) = JSONObject()
        .put("path", m.path).put("name", m.name).put("folder", m.relativeFolder)
        .put("size", m.sizeBytes).put("mod", m.modifiedMillis).put("dur", m.durationMs)
        .put("w", m.width).put("h", m.height).put("rot", m.rotationDegrees).put("fps", m.frameRate.toDouble())
        .put("vmime", m.videoMime).put("cmime", m.containerMime)
        .put("vt", m.videoTracks).put("at", m.audioTracks).put("ot", m.otherTracks).put("ab", m.audioBitrate)
        .put("hdr", m.hdr).put("tenBit", m.tenBit).put("dv", m.dolbyVision).put("slow", m.slowMotion)
        .put("sph", m.spherical).put("drm", m.drm)
        .put("created", m.creationTimeMillis ?: -1L).put("loc", m.location ?: "")

    private fun decode(o: JSONObject) = VideoMeta(
        path = o.optString("path"), name = o.optString("name"), relativeFolder = o.optString("folder"),
        sizeBytes = o.optLong("size"), modifiedMillis = o.optLong("mod"), durationMs = o.optLong("dur"),
        width = o.optInt("w"), height = o.optInt("h"), rotationDegrees = o.optInt("rot"), frameRate = o.optDouble("fps").toFloat(),
        videoMime = o.optString("vmime"), containerMime = o.optString("cmime"),
        videoTracks = o.optInt("vt"), audioTracks = o.optInt("at"), otherTracks = o.optInt("ot"), audioBitrate = o.optInt("ab"),
        hdr = o.optBoolean("hdr"), tenBit = o.optBoolean("tenBit"), dolbyVision = o.optBoolean("dv"), slowMotion = o.optBoolean("slow"),
        spherical = o.optBoolean("sph"), drm = o.optBoolean("drm"),
        creationTimeMillis = o.optLong("created", -1L).takeIf { it > 0L },
        location = o.optString("loc").takeIf { it.isNotBlank() },
    )

    private companion object {
        const val MAX_ENTRIES = 2000
    }
}
