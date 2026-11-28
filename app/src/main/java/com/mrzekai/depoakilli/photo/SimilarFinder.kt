package com.mrzekai.depoakilli.photo

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.util.Size
import com.mrzekai.depoakilli.data.ExcludedFoldersStore
import com.mrzekai.depoakilli.data.KeptItemsStore
import com.mrzekai.depoakilli.data.TrashStore
import java.io.File

/**
 * Faz 3B-1: finds similar photos on this phone.
 *
 * 1. MediaStore, newest first (the first run looks at [SimilarRules.FIRST_RUN_PHOTOS]).
 * 2. Per photo a small thumbnail only (ContentResolver.loadThumbnail; the file
 *    itself is not opened): 9x8 dHash, sharpness, brightness. Cached.
 * 3. Candidate pairs (same folder within a minute, or close hashes) are
 *    embedded with the bundled model; transitive groups above the cosine.
 *
 * Nothing is moved here. Stops between photos when [shouldStop] says so; the
 * cache keeps the work, so the next run continues where this one stopped.
 */
/** A photo offered one by one (Faz 3B-2): a bad shot or a typed photo. */
data class ReviewPhoto(
    val item: PhotoItem,
    val badKind: PhotoReviewRules.BadKind? = null,
    val type: PhotoReviewRules.PhotoType? = null,
    /** Looks like an important document (passport, money): a warning line. */
    val important: Boolean = false,
)

class SimilarFinder(private val context: Context) {
    private val cache = PhotoFeatureCache(context)

    data class Progress(val done: Int, val total: Int)

    data class Result(
        val groups: List<SimilarGroup>,
        val photosLooked: Int,
        /** Photos on the phone that this run did not look at (older than the first batch). */
        val photosLeft: Int,
        val stopped: Boolean,
        /** The model could not load: nothing is grouped rather than guessed. */
        val modelMissing: Boolean = false,
        /** Faz 3B-2: blurry / dark shots outside the groups, one by one only. */
        val badShots: List<ReviewPhoto> = emptyList(),
        /** Faz 3B-2: document and meme photos (30 days or older), one by one only. */
        val typed: List<ReviewPhoto> = emptyList(),
        /** Frames of the groups whose eyes look closed (a note on the tile). */
        val eyesClosed: Set<Long> = emptySet(),
    ) {
        val trashBytes: Long get() = groups.sumOf(SimilarGroup::trashBytes)
        val trashCount: Int get() = groups.sumOf { it.others.size }
    }

    fun find(
        all: Boolean,
        dismissed: Set<String>,
        /** Photos the user kept in a one-by-one review: not asked again. */
        reviewedKeep: Set<Long> = emptySet(),
        onProgress: (Progress) -> Unit = {},
        shouldStop: () -> Boolean = { false },
    ): Result {
        if (!Environment.isExternalStorageManager()) return Result(emptyList(), 0, 0, stopped = false)
        val (photos, totalOnPhone) = query(if (all) Int.MAX_VALUE else SimilarRules.FIRST_RUN_PHOTOS)
        val features = ArrayList<PhotoCacheView>(photos.size)
        val steps = photos.size * 2
        for ((i, item) in photos.withIndex()) {
            if (shouldStop()) {
                cache.save()
                return Result(emptyList(), i, totalOnPhone - i, stopped = true)
            }
            val entry = cache.get(item) ?: measure(item)?.also { cache.put(item, it) }
            features += PhotoCacheView(item, entry)
            if (i % 25 == 0) onProgress(Progress(i, steps))
        }
        val usable = features.filter { it.entry != null }
        val items = usable.map { it.item }
        val hashes = usable.map { it.entry!!.dHash }
        val pairs = SimilarRules.candidatePairs(items, hashes)
        val needed = pairs.flatMap { listOf(it.first, it.second) }.toSortedSet()
        val embedder = if (needed.any { usable[it].entry!!.embedding == null }) PhotoEmbedder.create(context) else null
        val missingModel = embedder == null && needed.any { usable[it].entry!!.embedding == null }
        try {
            for ((n, i) in needed.withIndex()) {
                if (shouldStop()) {
                    cache.save()
                    return Result(emptyList(), photos.size, totalOnPhone - photos.size, stopped = true)
                }
                val entry = usable[i].entry!!
                if (entry.embedding == null && embedder != null) {
                    thumbnail(usable[i].item, EMBED_SIZE)?.let { bmp ->
                        embedder.embed(bmp)?.let { entry.embedding = PhotoMath.quantize(it) }
                        bmp.recycle()
                    }
                    cache.markDirty()
                }
                if (n % 10 == 0) onProgress(Progress(photos.size + n * photos.size / needed.size.coerceAtLeast(1), steps))
            }
        } finally {
            embedder?.close()
            cache.save()
        }
        val embeddings = usable.map { v -> v.entry!!.embedding?.let(PhotoMath::dequantize) }
        val clusters = SimilarRules.cluster(items.size, pairs, embeddings)
        val now = System.currentTimeMillis()
        val models = if (BAD_SHOTS_ON || TYPES_ON) PhotoModels() else null
        try {
            // Faz 3B-2: eyes only inside similar groups (choosing the best frame).
            if (BAD_SHOTS_ON && models != null) {
                for (i in clusters.flatten()) {
                    if (shouldStop()) {
                        cache.save()
                        return Result(emptyList(), photos.size, totalOnPhone - photos.size, stopped = true)
                    }
                    val entry = usable[i].entry!!
                    if (entry.eyesOpen != PhotoFeatureCache.NO_VALUE) continue
                    thumbnail(usable[i].item, FACE_SIZE)?.let { bmp ->
                        val eyes = models.eyesOpen(bmp)
                        bmp.recycle()
                        // A model that could not run leaves the photo unmeasured (tried on a later run).
                        if (eyes == null || !eyes.isNaN()) {
                            entry.eyesOpen = eyes ?: PhotoFeatureCache.NO_FACE
                            cache.markDirty()
                        }
                    }
                }
            }
            // Faz 3B-2: types for photos of 30 days or more (cached).
            if (TYPES_ON && models != null) {
                for (v in usable) {
                    if (shouldStop()) {
                        cache.save()
                        return Result(emptyList(), photos.size, totalOnPhone - photos.size, stopped = true)
                    }
                    val entry = v.entry!!
                    if (entry.typeCode >= 0 || now - v.item.takenMillis < PhotoReviewRules.TYPE_MIN_AGE_MILLIS) continue
                    thumbnail(v.item, EMBED_SIZE)?.let { bmp ->
                        val labels = models.labels(bmp)
                        bmp.recycle()
                        if (labels != null) {
                            entry.typeCode = PhotoReviewRules.encodeType(
                                PhotoReviewRules.type(labels, v.item.takenMillis, now),
                                PhotoReviewRules.important(labels),
                            )
                            cache.markDirty()
                        }
                    }
                }
            }
        } finally {
            models?.close()
            cache.save()
        }
        val photoFeatures = usable.map { v ->
            val e = v.entry!!
            PhotoFeatures(e.dHash, null, e.sharpness, e.eyesOpen.takeIf { it >= 0f })
        }
        val groups = SimilarRules.groups(items, photoFeatures, clusters, dismissed)
        val inGroups = groups.flatMapTo(HashSet()) { g -> g.members.map(PhotoItem::id) }
        val badShots = if (!BAD_SHOTS_ON) emptyList() else usable.mapNotNull { v ->
            val e = v.entry!!
            if (v.item.id in inGroups || v.item.id in reviewedKeep) return@mapNotNull null
            PhotoReviewRules.badKind(e.sharpness, e.luma.toDouble(), v.item.takenMillis, now)?.let { ReviewPhoto(v.item, badKind = it) }
        }
        val typed = if (!TYPES_ON) emptyList() else usable.mapNotNull { v ->
            if (v.item.id in reviewedKeep) return@mapNotNull null
            val (type, important) = PhotoReviewRules.decodeType(v.entry!!.typeCode) ?: return@mapNotNull null
            type?.let { ReviewPhoto(v.item, type = it, important = important) }
        }
        val eyesClosed = usable.filter { PhotoReviewRules.eyesClosed(it.entry!!.eyesOpen.takeIf { e -> e >= 0f }) }.mapTo(HashSet()) { it.item.id }
        onProgress(Progress(steps, steps))
        return Result(
            groups,
            photos.size,
            (totalOnPhone - photos.size).coerceAtLeast(0),
            stopped = false,
            modelMissing = missingModel,
            badShots = badShots,
            typed = typed,
            eyesClosed = eyesClosed,
        )
    }

    private class PhotoCacheView(val item: PhotoItem, val entry: PhotoFeatureCache.Entry?)

    private fun measure(item: PhotoItem): PhotoFeatureCache.Entry? {
        val thumb = thumbnail(item, MEASURE_SIZE) ?: return null
        return try {
            val w = thumb.width
            val h = thumb.height
            val pixels = IntArray(w * h)
            thumb.getPixels(pixels, 0, w, 0, 0, w, h)
            val gray = IntArray(pixels.size) { PhotoMath.luma(pixels[it]) }
            val tiny = Bitmap.createScaledBitmap(thumb, 9, 8, true)
            val tp = IntArray(72)
            tiny.getPixels(tp, 0, 9, 0, 0, 9, 8)
            if (tiny !== thumb) tiny.recycle()
            PhotoFeatureCache.Entry(
                dHash = SimilarRules.dHash(IntArray(72) { PhotoMath.luma(tp[it]) }),
                sharpness = PhotoMath.laplacianVariance(gray, w, h),
                luma = PhotoMath.meanLuma(gray).toFloat(),
                embedding = null,
            )
        } catch (_: Throwable) {
            null
        } finally {
            thumb.recycle()
        }
    }

    private fun thumbnail(item: PhotoItem, size: Int): Bitmap? = runCatching {
        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL), item.id)
        val bmp = context.contentResolver.loadThumbnail(uri, Size(size, size), null)
        if (bmp.config == Bitmap.Config.ARGB_8888) bmp else bmp.copy(Bitmap.Config.ARGB_8888, false).also { bmp.recycle() }
    }.getOrNull()

    /** Newest photos first, exclusions applied. Returns the photos and how many photos the phone has. */
    private fun query(limit: Int): Pair<List<PhotoItem>, Int> {
        val kept = runCatching { KeptItemsStore.create(context).keys() }.getOrDefault(emptySet())
        val excluded = runCatching { ExcludedFoldersStore.create(context).folders() }.getOrDefault(emptyList())
        val storage = context.getSystemService(StorageManager::class.java)
        val out = ArrayList<PhotoItem>()
        var total = 0
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
        )
        runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC",
            )?.use { c ->
                total = c.count
                val idI = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dataI = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                val relI = c.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
                val nameI = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeI = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val modI = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val takenI = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val wI = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val hI = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                while (c.moveToNext() && out.size < limit) {
                    val path = c.getString(dataI) ?: continue
                    val file = File(path)
                    val relative = c.getString(relI).orEmpty().trim('/')
                    val name = c.getString(nameI) ?: file.name
                    val modified = c.getLong(modI) * 1000L
                    val taken = c.getLong(takenI).takeIf { it > 0L } ?: modified
                    val item = PhotoItem(
                        id = c.getLong(idI),
                        path = path,
                        folder = relative,
                        name = name,
                        sizeBytes = c.getLong(sizeI),
                        modifiedMillis = modified,
                        takenMillis = taken,
                        width = c.getInt(wI),
                        height = c.getInt(hI),
                    )
                    val volumeRoot = runCatching { storage?.getStorageVolume(file)?.directory?.absolutePath }.getOrNull()
                        ?: Environment.getExternalStorageDirectory().absolutePath
                    val skip = SimilarRules.excluded(
                        item,
                        inTrash = relative.split('/').any { it.equals(TrashStore.TRASH_DIRECTORY, ignoreCase = true) },
                        kept = KeptItemsStore.keyOf(volumeRoot, relative, name, item.sizeBytes, file.lastModified()) in kept,
                        excludedFolder = excluded.any { it.contains(volumeRoot, relative) },
                    )
                    if (!skip) out += item
                }
            }
        }
        return out to total
    }

    fun clearCache() = cache.clear()

    companion object {
        private const val MEASURE_SIZE = 256
        private const val EMBED_SIZE = 256
        private const val FACE_SIZE = 640
        private val BAD_SHOTS_ON get() = com.mrzekai.depoakilli.pro.ProGainEstimator.BAD_SHOTS_AVAILABLE
        private val TYPES_ON get() = com.mrzekai.depoakilli.pro.ProGainEstimator.PHOTO_TYPES_AVAILABLE
    }
}
