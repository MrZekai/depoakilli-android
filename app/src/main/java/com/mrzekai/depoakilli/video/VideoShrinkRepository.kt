package com.mrzekai.depoakilli.video

import android.content.Context
import com.mrzekai.depoakilli.data.DeviceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The video candidates as the screens and the paywall see them. */
sealed interface VideoFinderState {
    data object Idle : VideoFinderState

    data object Looking : VideoFinderState

    data class Ready(val result: VideoCandidateFinder.Result) : VideoFinderState
}

/**
 * Faz 3A-2 glue for the screens: finds candidates (headers only, cached), and
 * carries out the user's answer for each copy. "OK, remove the original"
 * moves the original to the app's Trash (7 days); "Keep the original" deletes
 * only the copy. Nothing happens without that answer.
 */
class VideoShrinkRepository(context: Context, val pending: PendingOutputStore) {
    private val appContext = context.applicationContext
    private val finder = VideoCandidateFinder(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var looking: Job? = null

    private val _state = MutableStateFlow<VideoFinderState>(VideoFinderState.Idle)
    val state: StateFlow<VideoFinderState> = _state.asStateFlow()

    /** The last finished look, for the paywall's measured estimate. */
    val lastResult: VideoCandidateFinder.Result?
        get() = (state.value as? VideoFinderState.Ready)?.result

    fun refresh() {
        if (looking?.isActive == true) return
        if (state.value !is VideoFinderState.Ready) _state.value = VideoFinderState.Looking
        looking = scope.launch {
            pending.expire()
            val result = runCatching { finder.find(pendingOriginals = pending.originals()) }.getOrNull()
            _state.value = if (result != null) VideoFinderState.Ready(result) else VideoFinderState.Idle
        }
    }

    /** Moves the original to the Trash; the copy stays. Returns the Trash entry id, or null if it could not be moved. */
    suspend fun approve(item: PendingOutput): String? = withContext(Dispatchers.IO) {
        val entry = DeviceRepository(appContext).moveShrunkOriginalToTrash(item.originalPath) ?: return@withContext null
        pending.remove(item.outputPath)
        finder.markShrunk(item.originalPath)
        entry.id
    }

    /** Keeps the original and deletes only the smaller copy; the video is not offered again. */
    suspend fun keepOriginal(item: PendingOutput) = withContext(Dispatchers.IO) {
        pending.discardCopy(item)
        finder.markShrunk(item.originalPath)
    }
}
