package com.mrzekai.depoakilli.video

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Faz 3A-2 "make room without deleting": shrinking big videos. Everything a
 * decision rests on is here, pure and unit-tested; the Android side only reads
 * metadata, runs Media3 Transformer and moves files.
 *
 * Nothing in this package deletes an original: an original only goes to the
 * app's Trash (7 days) after the user has watched the smaller copy and said yes.
 */

/** What the app knows about one video, read from its headers (never decoded). */
data class VideoMeta(
    val path: String,
    val name: String,
    /** Folder relative to its storage volume ("DCIM/Camera"), for the exclusion rules. */
    val relativeFolder: String,
    val sizeBytes: Long,
    val modifiedMillis: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val frameRate: Float,
    val videoMime: String,
    /** "video/mp4", "video/quicktime", … from the container, not the extension. */
    val containerMime: String,
    val videoTracks: Int,
    val audioTracks: Int,
    /** Tracks that are neither video nor audio: camera motion, subtitles, timecode, embedded stills. */
    val otherTracks: Int,
    val audioBitrate: Int = 0,
    val hdr: Boolean = false,
    val tenBit: Boolean = false,
    val dolbyVision: Boolean = false,
    /** Recorded faster than it plays (slow motion), from the capture frame rate. */
    val slowMotion: Boolean = false,
    val spherical: Boolean = false,
    val drm: Boolean = false,
    /** Date and place recorded in the file, kept on the smaller copy. */
    val creationTimeMillis: Long? = null,
    val location: String? = null,
) {
    val hasAudio: Boolean get() = audioTracks > 0
    val shortSide: Int get() = min(width, height)
    val longSide: Int get() = max(width, height)

    /** Bytes of the audio track (copied as it is), from its bitrate. */
    val audioBytes: Long
        get() = if (audioBitrate <= 0 || durationMs <= 0L) 0L else audioBitrate.toLong() * durationMs / 8_000L

    /** Average video bitrate of the source in bits per second (container overhead counts as video). */
    val sourceVideoBitrate: Long
        get() = if (durationMs <= 0L) 0L else ((sizeBytes - audioBytes).coerceAtLeast(0L) * 8_000L) / durationMs
}

/** Why a video is not offered for shrinking; the list shows nothing for these. */
enum class SkipReason {
    TOO_SMALL,
    TOO_SHORT,
    CONTAINER,
    NO_VIDEO,
    EXTRA_TRACKS,
    DRM,
    HDR,
    HIGH_FRAME_RATE,
    SLOW_MOTION,
    SPHERICAL,
    IN_TRASH,
    KEPT,
    EXCLUDED_FOLDER,
    ALREADY_SHRUNK,
    LOW_GAIN,
}

/** Where a video stands for the user's choices (from the app's stores). */
data class VideoContext(
    val inTrash: Boolean = false,
    val kept: Boolean = false,
    val excludedFolder: Boolean = false,
    /** This is a shrunk copy, or its shrunk copy already exists / waits for approval. */
    val alreadyShrunk: Boolean = false,
)

data class Candidate(
    val meta: VideoMeta,
    val targetVideoBitrate: Long,
    /** Lower-bound saving, rounded down; shown as "about". */
    val estimatedSavingBytes: Long,
) {
    val estimatedOutputBytes: Long get() = (meta.sizeBytes - estimatedSavingBytes).coerceAtLeast(0L)
}

object VideoRules {
    const val MIN_VIDEO_BYTES = 100_000_000L
    const val MIN_DURATION_MS = 10_000L
    const val MAX_FRAME_RATE = 60.5f
    const val MIN_GAIN_RATIO = 0.40
    const val MAX_TARGET_TO_SOURCE = 0.60
    const val ESTIMATE_MARGIN = 0.9

    /** H.265 targets for a phone's hardware encoder (cautious on purpose). */
    const val TARGET_720P = 2_500_000L
    const val TARGET_1080P = 6_000_000L
    const val TARGET_1440P = 10_000_000L
    const val TARGET_4K = 18_000_000L
    const val HIGH_FPS_FACTOR = 1.4
    const val H264_FACTOR = 1.6

    /** Shrunk copies are named "<name>_sc.mp4"; while written, ".<name>.sc.tmp". */
    const val OUTPUT_SUFFIX = "_sc.mp4"
    const val TEMP_SUFFIX = ".sc.tmp"

    private val CONTAINERS = setOf("video/mp4", "video/quicktime", "video/mpeg4", "video/x-m4v")

    /**
     * Target video bitrate: by the short side (720p and below / 1080p / 1440p
     * / 4K), ×1.4 above 30 fps, ×1.6 without an H.265 encoder (H.264 instead).
     */
    fun targetBitrate(meta: VideoMeta, hevcAvailable: Boolean): Long {
        val base = when {
            meta.shortSide <= 720 -> TARGET_720P
            meta.shortSide <= 1080 -> TARGET_1080P
            meta.shortSide <= 1440 -> TARGET_1440P
            else -> TARGET_4K
        }
        var target = base.toDouble()
        if (meta.frameRate > 30.5f) target *= HIGH_FPS_FACTOR
        if (!hevcAvailable) target *= H264_FACTOR
        return target.toLong()
    }

    /**
     * About how much shrinking saves: (size − duration × target ÷ 8 − audio) × 0.9,
     * rounded down. The audio is copied, so it stays the same size.
     */
    fun estimateSaving(meta: VideoMeta, targetBitrate: Long): Long {
        val videoOut = targetBitrate * meta.durationMs / 8_000L
        val saving = meta.sizeBytes - videoOut - meta.audioBytes
        return floor(saving.coerceAtLeast(0L) * ESTIMATE_MARGIN).toLong()
    }

    /** Null when [meta] is a candidate; otherwise the first rule it fails. */
    fun skipReason(meta: VideoMeta, context: VideoContext, hevcAvailable: Boolean): SkipReason? = when {
        context.inTrash -> SkipReason.IN_TRASH
        context.kept -> SkipReason.KEPT
        context.excludedFolder -> SkipReason.EXCLUDED_FOLDER
        context.alreadyShrunk || isOutputName(meta.name) -> SkipReason.ALREADY_SHRUNK
        meta.sizeBytes < MIN_VIDEO_BYTES -> SkipReason.TOO_SMALL
        meta.durationMs < MIN_DURATION_MS -> SkipReason.TOO_SHORT
        meta.containerMime.lowercase() !in CONTAINERS -> SkipReason.CONTAINER
        meta.videoTracks != 1 -> SkipReason.NO_VIDEO
        // A second audio track, camera motion, subtitles or an embedded still would be lost.
        meta.audioTracks > 1 || meta.otherTracks > 0 -> SkipReason.EXTRA_TRACKS
        meta.drm -> SkipReason.DRM
        meta.hdr || meta.tenBit || meta.dolbyVision -> SkipReason.HDR
        meta.frameRate > MAX_FRAME_RATE -> SkipReason.HIGH_FRAME_RATE
        meta.slowMotion -> SkipReason.SLOW_MOTION
        meta.spherical -> SkipReason.SPHERICAL
        else -> {
            val target = targetBitrate(meta, hevcAvailable)
            val source = meta.sourceVideoBitrate
            if (source <= 0L || target > source * MAX_TARGET_TO_SOURCE || 1.0 - target.toDouble() / source < MIN_GAIN_RATIO) {
                SkipReason.LOW_GAIN
            } else {
                null
            }
        }
    }

    fun candidate(meta: VideoMeta, context: VideoContext, hevcAvailable: Boolean): Candidate? {
        if (skipReason(meta, context, hevcAvailable) != null) return null
        val target = targetBitrate(meta, hevcAvailable)
        val saving = estimateSaving(meta, target)
        return if (saving > 0L) Candidate(meta, target, saving) else null
    }

    fun isOutputName(name: String): Boolean =
        name.endsWith(OUTPUT_SUFFIX, ignoreCase = true) || name.endsWith(TEMP_SUFFIX, ignoreCase = true)

    /** "trip.mov" → "trip_sc.mp4" (always an MP4 container). */
    fun outputName(originalName: String): String = originalName.substringBeforeLast('.', originalName) + OUTPUT_SUFFIX

    /** "trip.mov" → ".trip.sc.tmp" (hidden while it is written). */
    fun tempName(originalName: String): String = "." + originalName.substringBeforeLast('.', originalName) + TEMP_SUFFIX
}

// ------------------------------------------------------------------ verification

/** What the smaller copy must keep, checked before the user ever sees it. */
data class OutputCheck(
    val originalDurationMs: Long,
    val outputDurationMs: Long,
    val originalHasAudio: Boolean,
    val outputHasAudio: Boolean,
    val originalWidth: Int,
    val originalHeight: Int,
    val outputWidth: Int,
    val outputHeight: Int,
    val originalRotation: Int,
    val outputRotation: Int,
    val originalBytes: Long,
    val outputBytes: Long,
    /** Every sample of the output could be read from start to end. */
    val readsToEnd: Boolean,
)

enum class VerifyFailure { DURATION, AUDIO_LOST, RESOLUTION, ROTATION, NOT_SMALLER, UNREADABLE }

object OutputVerifier {
    const val MAX_DURATION_DIFF_MS = 1_000L
    const val MAX_OUTPUT_RATIO = 0.70

    /** Null when the copy passes; otherwise the first failure (the copy is then deleted, the original untouched). */
    fun failure(check: OutputCheck): VerifyFailure? {
        val originalShown = shown(check.originalWidth, check.originalHeight, check.originalRotation)
        val outputShown = shown(check.outputWidth, check.outputHeight, check.outputRotation)
        // Same stored pixels must carry the same turn; a muxer that rotated the
        // pixels themselves shows the same picture, which the shown size checks.
        val sameStorage = check.originalWidth == check.outputWidth && check.originalHeight == check.outputHeight
        return when {
            !check.readsToEnd -> VerifyFailure.UNREADABLE
            kotlin.math.abs(check.originalDurationMs - check.outputDurationMs) > MAX_DURATION_DIFF_MS -> VerifyFailure.DURATION
            check.originalHasAudio && !check.outputHasAudio -> VerifyFailure.AUDIO_LOST
            originalShown != outputShown -> VerifyFailure.RESOLUTION
            sameStorage && normalized(check.originalRotation) != normalized(check.outputRotation) -> VerifyFailure.ROTATION
            check.outputBytes * 100 > check.originalBytes * Math.round(MAX_OUTPUT_RATIO * 100) -> VerifyFailure.NOT_SMALLER
            else -> null
        }
    }

    /** Width × height as the picture is shown, after its rotation. */
    fun shown(width: Int, height: Int, rotation: Int): Pair<Int, Int> =
        if (normalized(rotation) == 90 || normalized(rotation) == 270) height to width else width to height

    private fun normalized(degrees: Int): Int = ((degrees % 360) + 360) % 360
}

// ------------------------------------------------------------------ queue

/** Whether the phone may start (or go on to) the next video. */
enum class DeviceBlock { LOW_BATTERY, TOO_HOT }

data class QueuePlan(
    val now: List<Candidate>,
    val nextRound: List<Candidate>,
    /** Not even the smallest copy fits next to the originals (free space below 1 GB + its size). */
    val noRoom: Boolean,
)

object VideoQueue {
    /** Kept free on the phone while originals and copies sit side by side. */
    const val RESERVE_BYTES = 1_000_000_000L
    const val MIN_BATTERY_PERCENT = 20

    /** A foreground service may run about 6 hours a day; a round stays under 5. */
    const val MAX_ROUND_MS = 5L * 60L * 60L * 1000L

    /**
     * Until the first video's real speed is known, encoding is assumed to take
     * as long as the video plays (a cautious guess used only to split rounds,
     * never shown as a promise).
     */
    const val ASSUMED_REALTIME_FACTOR = 1.0

    /**
     * The copies of one round must fit in free space − 1 GB: until the user
     * approves, original and copy both sit on the phone, and an approved original
     * then waits 7 days in the Trash. What does not fit goes to the next round.
     */
    fun plan(selected: List<Candidate>, freeBytes: Long): QueuePlan {
        if (selected.isEmpty()) return QueuePlan(emptyList(), emptyList(), noRoom = false)
        val smallest = selected.minOf(Candidate::estimatedOutputBytes)
        if (freeBytes < RESERVE_BYTES + smallest) return QueuePlan(emptyList(), selected, noRoom = true)
        val budget = freeBytes - RESERVE_BYTES
        val now = mutableListOf<Candidate>()
        val later = mutableListOf<Candidate>()
        var used = 0L
        var timeMs = 0L
        for (candidate in selected) {
            val out = candidate.estimatedOutputBytes
            val time = (candidate.meta.durationMs * ASSUMED_REALTIME_FACTOR).toLong()
            val fitsSpace = used + out <= budget
            val fitsTime = now.isEmpty() || timeMs + time <= MAX_ROUND_MS
            if (fitsSpace && fitsTime) {
                now += candidate
                used += out
                timeMs += time
            } else {
                later += candidate
            }
        }
        return QueuePlan(now, later, noRoom = now.isEmpty())
    }

    /** Before starting and before each next video. */
    fun block(batteryPercent: Int, charging: Boolean, thermalSevereOrWorse: Boolean): DeviceBlock? = when {
        thermalSevereOrWorse -> DeviceBlock.TOO_HOT
        !charging && batteryPercent < MIN_BATTERY_PERCENT -> DeviceBlock.LOW_BATTERY
        else -> null
    }

    /** Remaining time from the measured speed (encode ms per video ms); null before the first video ends. */
    fun remainingMs(measuredFactor: Double?, remainingVideoMs: Long): Long? =
        measuredFactor?.takeIf { it > 0.0 }?.let { (remainingVideoMs * it).toLong() }
}

// ------------------------------------------------------------------ waiting for approval

/** A shrunk copy waiting for the user: original and copy both on the phone. */
data class PendingOutput(
    val originalPath: String,
    val outputPath: String,
    val createdMillis: Long,
    val originalBytes: Long,
    val outputBytes: Long,
    val estimatedSavingBytes: Long,
) {
    val savedBytes: Long get() = (originalBytes - outputBytes).coerceAtLeast(0L)
}

object PendingRules {
    /** Copies not approved within 7 days are removed (the copies, never the originals). */
    const val EXPIRE_MILLIS = 7L * 24L * 60L * 60L * 1000L

    fun expired(pending: List<PendingOutput>, nowMillis: Long): List<PendingOutput> =
        pending.filter { nowMillis - it.createdMillis >= EXPIRE_MILLIS }

    /**
     * "Apply to the other N videos" works only after the first video was
     * approved by hand, and approving needs the small copy played at least once.
     */
    fun canApprove(playedSmallCopy: Boolean): Boolean = playedSmallCopy

    fun canApplyToRest(approvedByHand: Int, remaining: Int): Boolean = approvedByHand >= 1 && remaining > 0

    /** Real saving against the estimate; consistently below 0.9 is worth reporting. */
    fun accuracy(realSaving: Long, estimate: Long): Double? = if (estimate <= 0L) null else realSaving.toDouble() / estimate
}
