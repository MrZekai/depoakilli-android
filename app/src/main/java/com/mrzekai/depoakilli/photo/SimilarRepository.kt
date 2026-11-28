package com.mrzekai.depoakilli.photo

import android.content.Context
import com.mrzekai.depoakilli.data.DeviceRepository
import com.mrzekai.depoakilli.model.CleanupGroup
import org.json.JSONArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface SimilarState {
    data object Idle : SimilarState
    data class Looking(val done: Int, val total: Int) : SimilarState
    data class Ready(val result: SimilarFinder.Result) : SimilarState
}

/** What the similar-photo feature remembers on this device (numbers and ids only). */
class SimilarStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("similar_photos", Context.MODE_PRIVATE)

    fun dismissed(): Set<String> = readSet(K_DISMISSED)

    fun dismiss(key: String) = writeSet(K_DISMISSED, (dismissed() + key).toList().takeLast(2_000).toSet())

    val groupsViewed: Int get() = prefs.getInt(K_VIEWED, 0)

    fun markViewed() = prefs.edit().putInt(K_VIEWED, groupsViewed + 1).apply()

    /**
     * Frames the last in-app look offered (MediaStore id -> bytes), for the
     * Watch: no model runs in the background.
     */
    fun deletable(): Map<String, Long> = readSet(K_DELETABLE).associate { e ->
        e.substringBefore('|') to (e.substringAfter('|').toLongOrNull() ?: 0L)
    }

    fun setDeletable(frames: Map<String, Long>) =
        writeSet(K_DELETABLE, frames.entries.take(5_000).mapTo(LinkedHashSet()) { "${it.key}|${it.value}" })

    private fun readSet(key: String): Set<String> = runCatching {
        val a = JSONArray(prefs.getString(key, "[]"))
        (0 until a.length()).mapTo(LinkedHashSet()) { a.getString(it) }
    }.getOrDefault(emptySet())

    private fun writeSet(key: String, values: Set<String>) = prefs.edit().putString(key, JSONArray(values.toList()).toString()).apply()

    fun reset() = prefs.edit().clear().apply()

    private companion object {
        const val K_DISMISSED = "dismissed"
        const val K_VIEWED = "viewed"
        const val K_DELETABLE = "deletable"
    }
}

/**
 * Faz 3B-1 state for the screens. Looks only while the app is in front
 * ([pause] / [resume] from the activity lifecycle); no foreground service.
 * Moving to the Trash happens only from the user's tap.
 */
class SimilarRepository(private val appContext: Context) {
    private companion object {
        const val TAG = "SmartCleanerPhotos"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val finder = SimilarFinder(appContext)
    val store = SimilarStore(appContext)
    private val _state = MutableStateFlow<SimilarState>(SimilarState.Idle)
    val state: StateFlow<SimilarState> = _state.asStateFlow()

    private var job: Job? = null
    @Volatile private var stopRequested = false
    @Volatile private var pausedAll: Boolean? = null
    private var lastAll = false

    fun start(all: Boolean = false) {
        if (job?.isActive == true) return
        stopRequested = false
        lastAll = all
        if (_state.value !is SimilarState.Ready || all) _state.value = SimilarState.Looking(0, 1)
        job = scope.launch {
            val started = android.os.SystemClock.elapsedRealtime()
            val result = finder.find(
                all = all,
                dismissed = store.dismissed(),
                onProgress = { p -> if (_state.value !is SimilarState.Ready || all) _state.value = SimilarState.Looking(p.done, p.total) },
                shouldStop = { stopRequested },
            )
            if (result.stopped) {
                pausedAll = all
                return@launch
            }
            pausedAll = null
            // Counts and time only (no names) so QA can read the run from logcat.
            android.util.Log.w(
                TAG,
                "similar: looked=${result.photosLooked} groups=${result.groups.size} frames=${result.trashCount} " +
                    "bytes=${result.trashBytes} ms=${android.os.SystemClock.elapsedRealtime() - started} model=${!result.modelMissing}",
            )
            if (com.mrzekai.depoakilli.BuildConfig.DEBUG) {
                result.groups.forEach { g -> android.util.Log.w(TAG, "similar group: " + g.members.joinToString(",") { it.name }) }
            }
            publish(result)
        }
    }

    /** The app went to the background: stop between photos; the cache keeps the work. */
    fun pause() {
        if (job?.isActive == true) {
            stopRequested = true
            pausedAll = lastAll
        }
    }

    /** Back in front: continue a scan that [pause] stopped. */
    fun resume() {
        val all = pausedAll ?: return
        pausedAll = null
        start(all)
    }

    private fun publish(result: SimilarFinder.Result) {
        _state.value = SimilarState.Ready(result)
        store.setDeletable(result.groups.flatMap { g -> g.others.map { it.id.toString() to it.sizeBytes } }.toMap())
    }

    fun markViewed() = store.markViewed()

    /** "Not similar": this group is never offered again. */
    fun dismiss(group: SimilarGroup) {
        store.dismiss(group.key)
        drop(group)
    }

    /** Moves the chosen frames to the app's Trash (7 days, restorable). Returns the moved bytes. */
    suspend fun moveToTrash(group: SimilarGroup, chosen: List<PhotoItem>): Long = withContext(Dispatchers.IO) {
        val entries = DeviceRepository(appContext).moveToTrash(chosen.map(PhotoItem::path), CleanupGroup.SIMILAR_PHOTO)
        if (entries.isNotEmpty()) drop(group)
        entries.sumOf { it.sizeBytes }
    }

    private fun drop(group: SimilarGroup) {
        val ready = _state.value as? SimilarState.Ready ?: return
        publish(ready.result.copy(groups = ready.result.groups.filter { it.key != group.key }))
    }

    fun reset() {
        store.reset()
        finder.clearCache()
        _state.value = SimilarState.Idle
    }
}
