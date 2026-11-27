package com.mrzekai.depoakilli.video

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.container.Mp4LocationData
import androidx.media3.container.Mp4TimestampData
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.InAppMp4Muxer
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Shrinks one video with Media3 Transformer, writing next to it:
 * ".<name>.sc.tmp" while it works, "<name>_sc.mp4" once the copy passed every
 * check. The original is only read. The copy keeps resolution, frame rate,
 * rotation, date and place; the sound is copied as it is.
 *
 * Re-encoding is forced by the requested bitrate: in Media3 1.10.1
 * `DefaultEncoderFactory.videoNeedsEncoding()` is true whenever the video
 * encoder settings differ from the default, so an H.265 source is encoded
 * again instead of being copied sample by sample. The audio encoder settings
 * stay default and no audio MIME type is set, so the audio is copied.
 */
@OptIn(UnstableApi::class)
class VideoCompressor(private val context: Context) {

    sealed interface Outcome {
        data class Shrunk(val output: File, val outputBytes: Long) : Outcome

        data class Failed(val reason: VerifyFailure?) : Outcome

        data object Cancelled : Outcome
    }

    suspend fun compress(
        candidate: Candidate,
        hevc: Boolean,
        onProgress: (Int) -> Unit,
    ): Outcome {
        val original = File(candidate.meta.path)
        val folder = original.parentFile ?: return Outcome.Failed(null)
        val temp = File(folder, VideoRules.tempName(original.name))
        val output = File(folder, VideoRules.outputName(original.name))
        OutputFiles.deleteOutput(temp)
        if (output.exists()) return Outcome.Failed(null)

        val result = try {
            runTransformer(candidate, hevc, original, temp, onProgress)
        } catch (cancelled: CancellationException) {
            // Stopped: the unfinished file goes, the original is untouched.
            withContext(NonCancellable + Dispatchers.IO) { OutputFiles.deleteOutput(temp) }
            throw cancelled
        }
        if (result != null) {
            OutputFiles.deleteOutput(temp)
            return result
        }
        val facts = withContext(Dispatchers.IO) { VideoProbe.outputFacts(temp) }
        val failure = if (facts == null) {
            VerifyFailure.UNREADABLE
        } else {
            OutputVerifier.failure(
                OutputCheck(
                    originalDurationMs = candidate.meta.durationMs,
                    outputDurationMs = facts.durationMs,
                    originalHasAudio = candidate.meta.hasAudio,
                    outputHasAudio = facts.hasAudio,
                    originalWidth = candidate.meta.width,
                    originalHeight = candidate.meta.height,
                    outputWidth = facts.width,
                    outputHeight = facts.height,
                    originalRotation = candidate.meta.rotationDegrees,
                    outputRotation = facts.rotation,
                    originalBytes = candidate.meta.sizeBytes,
                    outputBytes = temp.length(),
                    readsToEnd = facts.readsToEnd,
                ),
            )
        }
        if (failure != null) {
            OutputFiles.deleteOutput(temp)
            return Outcome.Failed(failure)
        }
        return withContext(Dispatchers.IO) {
            if (!temp.renameTo(output)) {
                OutputFiles.deleteOutput(temp)
                return@withContext Outcome.Failed(null)
            }
            // Same date as the original, so the gallery keeps its order.
            runCatching { output.setLastModified(original.lastModified()) }
            MediaScannerConnection.scanFile(context, arrayOf(output.absolutePath), arrayOf(MimeTypes.VIDEO_MP4), null)
            Outcome.Shrunk(output, output.length())
        }
    }

    /** Null when the export completed; otherwise why it stopped. */
    private suspend fun runTransformer(
        candidate: Candidate,
        hevc: Boolean,
        original: File,
        temp: File,
        onProgress: (Int) -> Unit,
    ): Outcome? = withContext(Dispatchers.Main) {
        coroutineScope {
            var transformer: Transformer? = null
            val poller = launch {
                val holder = ProgressHolder()
                var lastLogged = -10
                while (true) {
                    delay(PROGRESS_POLL_MS)
                    val current = transformer ?: continue
                    if (current.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                        if (holder.progress / 10 != lastLogged / 10) {
                            lastLogged = holder.progress
                            Log.i(TAG, "progress ${holder.progress}%")
                        }
                        onProgress(holder.progress)
                    }
                }
            }
            val outcome = suspendCancellableCoroutine<Outcome?> { continuation ->
                val listener = object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        Log.i(TAG, "export done: ${exportResult.videoEncoderName} ${exportResult.averageVideoBitrate} b/s, audio ${exportResult.audioEncoderName ?: "copied"}")
                        if (continuation.isActive) continuation.resume(null)
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        Log.w(TAG, "export failed: ${exportException.errorCodeName}", exportException)
                        if (continuation.isActive) continuation.resume(Outcome.Failed(null))
                    }
                }
                val encoderFactory = DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(
                        VideoEncoderSettings.Builder().setBitrate(candidate.targetVideoBitrate.toInt()).build(),
                    )
                    .setEnableFallback(true)
                    .build()
                val built = Transformer.Builder(context)
                    .setVideoMimeType(if (hevc) MimeTypes.VIDEO_H265 else MimeTypes.VIDEO_H264)
                    .setEncoderFactory(encoderFactory)
                    .setMuxerFactory(InAppMp4Muxer.Factory(MetadataKeeper(candidate.meta)))
                    .addListener(listener)
                    .build()
                transformer = built
                Log.i(TAG, "export start: ${if (hevc) "H.265" else "H.264"} ${candidate.targetVideoBitrate} b/s, ${candidate.meta.width}x${candidate.meta.height}")
                continuation.invokeOnCancellation { runCatching { built.cancel() } }
                runCatching {
                    built.start(EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(original))).build(), temp.absolutePath)
                }.onFailure { if (continuation.isActive) continuation.resume(Outcome.Failed(null)) }
            }
            poller.cancel()
            outcome
        }
    }

    /** Keeps the original's date and place in the copy (Media3 passes on what it parsed; this fills the gaps). */
    private class MetadataKeeper(private val meta: VideoMeta) : InAppMp4Muxer.MetadataProvider {
        override fun updateMetadataEntries(metadataEntries: MutableSet<Metadata.Entry>) {
            meta.creationTimeMillis?.let { created ->
                metadataEntries.removeAll { it is Mp4TimestampData }
                val seconds = Mp4TimestampData.unixTimeToMp4TimeSeconds(created)
                metadataEntries.add(Mp4TimestampData(seconds, seconds))
            }
            if (metadataEntries.none { it is Mp4LocationData }) {
                parseIso6709(meta.location)?.let { (lat, lon) -> metadataEntries.add(Mp4LocationData(lat, lon)) }
            }
        }
    }

    companion object {
        private const val PROGRESS_POLL_MS = 500L
        private const val TAG = "SmartCleanerVideo"

        /** "+41.0082+028.9784/" → (41.0082, 28.9784). */
        internal fun parseIso6709(raw: String?): Pair<Float, Float>? {
            if (raw.isNullOrBlank()) return null
            val match = Regex("""^([+-]\d+(?:\.\d+)?)([+-]\d+(?:\.\d+)?)""").find(raw.trim()) ?: return null
            val lat = match.groupValues[1].toFloatOrNull() ?: return null
            val lon = match.groupValues[2].toFloatOrNull() ?: return null
            return if (lat in -90f..90f && lon in -180f..180f) lat to lon else null
        }
    }
}

/**
 * The only place in video/ that deletes a file, and only Smart Cleaner's own
 * copies: "<name>_sc.mp4" and ".<name>.sc.tmp". An original never passes.
 */
object OutputFiles {
    fun isOwnOutput(file: File): Boolean = VideoRules.isOutputName(file.name)

    fun deleteOutput(file: File): Boolean {
        if (!isOwnOutput(file)) return false
        if (!file.exists()) return true
        return runCatching { file.delete() }.getOrDefault(false)
    }
}
