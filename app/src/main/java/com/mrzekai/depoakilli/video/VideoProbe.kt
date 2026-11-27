package com.mrzekai.depoakilli.video

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Reads a video's headers with Android's own extractor and retriever. No frame
 * is decoded; nothing is written.
 */
object VideoProbe {
    fun read(file: File, relativeFolder: String, containerMime: String?): VideoMeta? = runCatching {
        val extractor = MediaExtractor()
        val retriever = MediaMetadataRetriever()
        try {
            extractor.setDataSource(file.absolutePath)
            retriever.setDataSource(file.absolutePath)
            var videoTracks = 0
            var audioTracks = 0
            var otherTracks = 0
            var videoFormat: MediaFormat? = null
            var audioBitrate = 0
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                when {
                    mime.startsWith("video/") -> {
                        videoTracks++
                        if (videoFormat == null) videoFormat = format
                    }
                    mime.startsWith("audio/") -> {
                        audioTracks++
                        if (audioBitrate == 0) audioBitrate = format.intOrZero(MediaFormat.KEY_BIT_RATE)
                    }
                    else -> otherTracks++
                }
            }
            val video = videoFormat
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: video?.longOrZero(MediaFormat.KEY_DURATION)?.div(1000L) ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                ?: video?.intOrZero(MediaFormat.KEY_WIDTH) ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                ?: video?.intOrZero(MediaFormat.KEY_HEIGHT) ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val frameRate = frameRate(video, retriever, durationMs)
            val captureFps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull()
            val videoMime = video?.getString(MediaFormat.KEY_MIME).orEmpty()
            val transfer = video?.intOrZero(MediaFormat.KEY_COLOR_TRANSFER) ?: 0
            val retrieverTransfer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COLOR_TRANSFER)?.toIntOrNull() ?: 0
            val hdr = transfer == MediaFormat.COLOR_TRANSFER_ST2084 || transfer == MediaFormat.COLOR_TRANSFER_HLG ||
                retrieverTransfer == MediaFormat.COLOR_TRANSFER_ST2084 || retrieverTransfer == MediaFormat.COLOR_TRANSFER_HLG
            val profile = video?.intOrZero(MediaFormat.KEY_PROFILE) ?: 0
            val tenBit = isTenBit(videoMime, profile)
            VideoMeta(
                path = file.absolutePath,
                name = file.name,
                relativeFolder = relativeFolder,
                sizeBytes = file.length(),
                modifiedMillis = file.lastModified(),
                durationMs = durationMs,
                width = width,
                height = height,
                rotationDegrees = rotation,
                frameRate = frameRate,
                videoMime = videoMime,
                containerMime = (containerMime ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)).orEmpty(),
                videoTracks = videoTracks,
                audioTracks = audioTracks,
                otherTracks = otherTracks,
                audioBitrate = audioBitrate,
                hdr = hdr,
                tenBit = tenBit,
                dolbyVision = videoMime.equals(MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION, ignoreCase = true),
                slowMotion = captureFps != null && frameRate > 0f && captureFps > frameRate * 1.5f,
                // Android does not report 360° metadata; a 2:1 picture of 4K width or more is treated as 360°.
                spherical = maxOf(width, height) >= 3840 && maxOf(width, height) == 2 * minOf(width, height),
                drm = extractor.psshInfo?.isNotEmpty() == true || extractor.drmInitData != null,
                creationTimeMillis = parseDate(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)),
                location = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION),
            )
        } finally {
            runCatching { extractor.release() }
            runCatching { retriever.release() }
        }
    }.getOrNull()

    /**
     * The facts the copy is checked against; [OutputCheck.readsToEnd] reads every
     * sample of every track to the end.
     */
    fun outputFacts(file: File): OutputFacts? = runCatching {
        val retriever = MediaMetadataRetriever()
        val facts = try {
            retriever.setDataSource(file.absolutePath)
            OutputFacts(
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes",
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
                rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0,
                readsToEnd = false,
            )
        } finally {
            runCatching { retriever.release() }
        }
        facts.copy(readsToEnd = readsToEnd(file))
    }.getOrNull()

    data class OutputFacts(
        val durationMs: Long,
        val hasAudio: Boolean,
        val width: Int,
        val height: Int,
        val rotation: Int,
        val readsToEnd: Boolean,
    )

    private fun readsToEnd(file: File): Boolean = runCatching {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            if (extractor.trackCount == 0) return@runCatching false
            for (index in 0 until extractor.trackCount) extractor.selectTrack(index)
            val buffer = ByteBuffer.allocate(4 * 1024 * 1024)
            var samples = 0
            while (true) {
                val read = extractor.readSampleData(buffer, 0)
                if (read < 0) break
                samples++
                if (!extractor.advance()) break
            }
            samples > 0
        } finally {
            runCatching { extractor.release() }
        }
    }.getOrDefault(false)

    /** Whether the phone has an H.265 encoder (else H.264 with higher targets). */
    fun hevcEncoderAvailable(): Boolean = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            info.isEncoder && info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, ignoreCase = true) }
        }
    }.getOrDefault(false)

    private fun frameRate(video: MediaFormat?, retriever: MediaMetadataRetriever, durationMs: Long): Float {
        val fromFormat = video?.let {
            runCatching { it.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat() }.getOrNull()
                ?: runCatching { it.getFloat(MediaFormat.KEY_FRAME_RATE) }.getOrNull()
        }
        if (fromFormat != null && fromFormat > 0f) return fromFormat
        val frames = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toLongOrNull() ?: 0L
        return if (frames > 0L && durationMs > 0L) frames * 1000f / durationMs else 0f
    }

    private fun isTenBit(mime: String, profile: Int): Boolean = when (mime.lowercase()) {
        MediaFormat.MIMETYPE_VIDEO_HEVC -> profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 ||
            profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10 ||
            profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus
        MediaFormat.MIMETYPE_VIDEO_AVC -> profile == MediaCodecInfo.CodecProfileLevel.AVCProfileHigh10
        MediaFormat.MIMETYPE_VIDEO_VP9 -> profile == MediaCodecInfo.CodecProfileLevel.VP9Profile2 ||
            profile == MediaCodecInfo.CodecProfileLevel.VP9Profile3 ||
            profile == MediaCodecInfo.CodecProfileLevel.VP9Profile2HDR ||
            profile == MediaCodecInfo.CodecProfileLevel.VP9Profile3HDR
        MediaFormat.MIMETYPE_VIDEO_AV1 -> profile == MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10 ||
            profile == MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10 ||
            profile == MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10Plus
        else -> false
    }

    /** MediaMetadataRetriever dates look like "20250302T143005.000Z". */
    internal fun parseDate(raw: String?): Long? {
        if (raw.isNullOrBlank() || raw.startsWith("1904") || raw.startsWith("1970")) return null
        val patterns = listOf("yyyyMMdd'T'HHmmss.SSS'Z'", "yyyyMMdd'T'HHmmss'Z'", "yyyyMMdd'T'HHmmss")
        for (pattern in patterns) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(raw)?.time
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }

    private fun MediaFormat.intOrZero(key: String): Int = if (containsKey(key)) runCatching { getInteger(key) }.getOrDefault(0) else 0

    private fun MediaFormat.longOrZero(key: String): Long = if (containsKey(key)) runCatching { getLong(key) }.getOrDefault(0L) else 0L
}
