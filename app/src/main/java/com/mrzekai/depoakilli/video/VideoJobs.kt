package com.mrzekai.depoakilli.video

import android.content.Context
import android.media.MediaScannerConnection
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

/** What happened to one video of a round. */
enum class ShrinkOutcome { SHRUNK, TOO_LITTLE_GAIN, COULD_NOT }

data class ShrinkResult(val candidate: Candidate, val outcome: ShrinkOutcome, val outputBytes: Long = 0L) {
    val savedBytes: Long get() = if (outcome == ShrinkOutcome.SHRUNK) (candidate.meta.sizeBytes - outputBytes).coerceAtLeast(0L) else 0L
}

/** One round of shrinking, shared by the service, its notification and Z6–Z8. */
data class ShrinkJobState(
    val running: Boolean = false,
    val queue: List<Candidate> = emptyList(),
    /** 0-based index of the video being shrunk. */
    val index: Int = 0,
    /** Progress of the current video, 0–100. */
    val progress: Int = 0,
    val results: List<ShrinkResult> = emptyList(),
    val stoppedByUser: Boolean = false,
    val blocked: DeviceBlock? = null,
    /** Encoding time per video time, measured on the first video; null until then. */
    val speedFactor: Double? = null,
    /** Videos that did not fit this round (space or time); offered next. */
    val nextRound: List<Candidate> = emptyList(),
) {
    val total: Int get() = queue.size
    val current: Candidate? get() = queue.getOrNull(index)
    val savedSoFar: Long get() = results.sumOf(ShrinkResult::savedBytes)
    val finished: Boolean get() = !running && results.isNotEmpty()
    val remainingMs: Long?
        get() = VideoQueue.remainingMs(speedFactor, queue.drop(index).sumOf { it.meta.durationMs })
    val overallProgress: Float
        get() = if (total == 0) 0f else ((index + progress / 100f) / total).coerceIn(0f, 1f)
}

/** The running round, readable from any screen. */
object ShrinkJobs {
    private val _state = MutableStateFlow(ShrinkJobState())
    val state: StateFlow<ShrinkJobState> = _state.asStateFlow()

    internal fun update(change: (ShrinkJobState) -> ShrinkJobState) = _state.update(change)

    fun reset() {
        if (!_state.value.running) _state.value = ShrinkJobState()
    }
}

/**
 * Copies waiting for the user's approval, on this device only. A copy not
 * approved in 7 days is removed; the original is never touched by this store.
 */
class PendingOutputStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<List<PendingOutput>> = _state.asStateFlow()

    @Synchronized
    fun add(pending: PendingOutput) = write(read().filterNot { it.outputPath == pending.outputPath } + pending)

    @Synchronized
    fun remove(outputPath: String) = write(read().filterNot { it.outputPath == outputPath })

    fun originals(): Set<String> = _state.value.mapTo(HashSet(), PendingOutput::originalPath)

    /** Removes copies older than 7 days and copies whose files are gone; returns how many copies were removed. */
    @Synchronized
    fun expire(nowMillis: Long = System.currentTimeMillis()): Int {
        val all = read()
        val expired = PendingRules.expired(all, nowMillis)
        expired.forEach { discardCopy(it) }
        val gone = all.filter { !File(it.outputPath).exists() || !File(it.originalPath).exists() }
        write(all - expired.toSet() - gone.toSet())
        return expired.size
    }

    /** "Keep the original, delete the smaller one": only the copy goes. */
    fun discardCopy(pending: PendingOutput) {
        val output = File(pending.outputPath)
        if (OutputFiles.deleteOutput(output)) {
            MediaScannerConnection.scanFile(appContext, arrayOf(output.absolutePath), null, null)
        }
        remove(pending.outputPath)
    }

    private fun write(list: List<PendingOutput>) {
        val array = JSONArray()
        list.forEach {
            array.put(
                JSONObject()
                    .put("orig", it.originalPath)
                    .put("out", it.outputPath)
                    .put("at", it.createdMillis)
                    .put("origB", it.originalBytes)
                    .put("outB", it.outputBytes)
                    .put("est", it.estimatedSavingBytes),
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
        _state.value = list
    }

    private fun read(): List<PendingOutput> = runCatching {
        val array = JSONArray(prefs.getString(KEY, null) ?: return emptyList())
        (0 until array.length()).mapNotNull { index ->
            val o = array.optJSONObject(index) ?: return@mapNotNull null
            PendingOutput(
                originalPath = o.optString("orig"),
                outputPath = o.optString("out"),
                createdMillis = o.optLong("at"),
                originalBytes = o.optLong("origB"),
                outputBytes = o.optLong("outB"),
                estimatedSavingBytes = o.optLong("est"),
            )
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val PREFS = "video_shrink_pending"
        const val KEY = "pending"
    }
}
