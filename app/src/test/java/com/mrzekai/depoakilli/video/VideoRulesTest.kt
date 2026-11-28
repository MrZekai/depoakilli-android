package com.mrzekai.depoakilli.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoRulesTest {
    private val mb = 1_000_000L
    private val gb = 1_000_000_000L

    /** A 1080p 30 fps phone video: 8 min 5 s, 1.4 GB (≈ 23 Mbps), AAC 128 kbps. */
    private fun video(
        size: Long = 1_400 * mb,
        durationMs: Long = 485_000L,
        width: Int = 1920,
        height: Int = 1080,
        fps: Float = 30f,
        name: String = "trip.mp4",
        container: String = "video/mp4",
        videoTracks: Int = 1,
        audioTracks: Int = 1,
        otherTracks: Int = 0,
        audioBitrate: Int = 128_000,
        hdr: Boolean = false,
        tenBit: Boolean = false,
        dolby: Boolean = false,
        slowMotion: Boolean = false,
        spherical: Boolean = false,
        drm: Boolean = false,
        rotation: Int = 0,
    ) = VideoMeta(
        path = "/storage/emulated/0/DCIM/Camera/$name",
        name = name,
        relativeFolder = "DCIM/Camera",
        sizeBytes = size,
        modifiedMillis = 0L,
        durationMs = durationMs,
        width = width,
        height = height,
        rotationDegrees = rotation,
        frameRate = fps,
        videoMime = "video/avc",
        containerMime = container,
        videoTracks = videoTracks,
        audioTracks = audioTracks,
        otherTracks = otherTracks,
        audioBitrate = audioBitrate,
        hdr = hdr,
        tenBit = tenBit,
        dolbyVision = dolby,
        slowMotion = slowMotion,
        spherical = spherical,
        drm = drm,
    )

    private fun skip(meta: VideoMeta, context: VideoContext = VideoContext(), hevc: Boolean = true) =
        VideoRules.skipReason(meta, context, hevc)

    // --- target bitrate table ---

    @Test
    fun `target bitrate follows the short side, fps and the encoder`() {
        assertEquals(2_500_000L, VideoRules.targetBitrate(video(width = 1280, height = 720), true))
        assertEquals(2_500_000L, VideoRules.targetBitrate(video(width = 640, height = 480), true))
        assertEquals(6_000_000L, VideoRules.targetBitrate(video(), true))
        assertEquals(6_000_000L, VideoRules.targetBitrate(video(width = 1080, height = 1920), true))
        assertEquals(10_000_000L, VideoRules.targetBitrate(video(width = 2560, height = 1440), true))
        assertEquals(18_000_000L, VideoRules.targetBitrate(video(width = 3840, height = 2160), true))
        assertEquals(8_400_000L, VideoRules.targetBitrate(video(fps = 60f), true))
        assertEquals(9_600_000L, VideoRules.targetBitrate(video(), false))
        assertEquals(40_320_000L, VideoRules.targetBitrate(video(width = 3840, height = 2160, fps = 60f), false))
    }

    // --- estimate ---

    @Test
    fun `estimate is the lower bound with audio copied, rounded down`() {
        val meta = video()
        // video out = 6 Mbps × 485 s ÷ 8 = 363.75 MB; audio = 128 kbps × 485 s ÷ 8 = 7.76 MB
        val expected = ((1_400 * mb - 363_750_000L - 7_760_000L) * 0.9).toLong()
        assertEquals(expected, VideoRules.estimateSaving(meta, 6_000_000L))
        assertEquals(925_641_000L, expected)
        assertEquals(0L, VideoRules.estimateSaving(video(size = 300 * mb), 6_000_000L))
    }

    @Test
    fun `a normal phone video is a candidate`() {
        val candidate = VideoRules.candidate(video(), VideoContext(), hevcAvailable = true)
        assertNotNull(candidate)
        assertEquals(6_000_000L, candidate!!.targetVideoBitrate)
        assertEquals(1_400 * mb - candidate.estimatedSavingBytes, candidate.estimatedOutputBytes)
    }

    @Test
    fun `the codec choice travels with the candidate`() {
        val hevc = VideoRules.candidate(video(), VideoContext(), hevcAvailable = true)!!
        val avc = VideoRules.candidate(video(), VideoContext(), hevcAvailable = false)!!
        assertEquals(true, hevc.hevc)
        assertEquals(false, avc.hevc)
        assertEquals(9_600_000L, avc.targetVideoBitrate)
        assert(avc.estimatedSavingBytes < hevc.estimatedSavingBytes)
    }

    // --- one test per exclusion ---

    @Test fun `under 100 MB is out`() = assertEquals(SkipReason.TOO_SMALL, skip(video(size = 99 * mb)))

    @Test fun `under 10 seconds is out`() = assertEquals(SkipReason.TOO_SHORT, skip(video(durationMs = 9_999L)))

    @Test fun `other containers are out`() {
        assertEquals(SkipReason.CONTAINER, skip(video(container = "video/x-matroska")))
        assertNull(skip(video(container = "video/quicktime", name = "a.mov")))
    }

    @Test fun `no video or two video tracks are out`() {
        assertEquals(SkipReason.NO_VIDEO, skip(video(videoTracks = 0)))
        assertEquals(SkipReason.NO_VIDEO, skip(video(videoTracks = 2)))
    }

    @Test fun `a second audio track is out`() = assertEquals(SkipReason.EXTRA_TRACKS, skip(video(audioTracks = 2)))

    @Test fun `camera motion, subtitles or an embedded still are out`() =
        assertEquals(SkipReason.EXTRA_TRACKS, skip(video(otherTracks = 1)))

    @Test fun `a video without sound is fine`() = assertNull(skip(video(audioTracks = 0, audioBitrate = 0)))

    @Test fun `DRM is out`() = assertEquals(SkipReason.DRM, skip(video(drm = true)))

    @Test fun `HDR, 10-bit and Dolby Vision are out`() {
        assertEquals(SkipReason.HDR, skip(video(hdr = true)))
        assertEquals(SkipReason.HDR, skip(video(tenBit = true)))
        assertEquals(SkipReason.HDR, skip(video(dolby = true)))
    }

    @Test fun `above 60 fps is out`() {
        assertEquals(SkipReason.HIGH_FRAME_RATE, skip(video(fps = 120f)))
        assertNull(skip(video(fps = 60f, size = 2 * gb)))
        assertNull(skip(video(fps = 59.94f, size = 2 * gb)))
    }

    @Test fun `slow motion is out`() = assertEquals(SkipReason.SLOW_MOTION, skip(video(slowMotion = true)))

    @Test fun `360 videos are out`() = assertEquals(SkipReason.SPHERICAL, skip(video(spherical = true)))

    @Test fun `Trash, kept and excluded folders are out`() {
        assertEquals(SkipReason.IN_TRASH, skip(video(), VideoContext(inTrash = true)))
        assertEquals(SkipReason.KEPT, skip(video(), VideoContext(kept = true)))
        assertEquals(SkipReason.EXCLUDED_FOLDER, skip(video(), VideoContext(excludedFolder = true)))
    }

    @Test fun `a shrunk copy, or a video already shrunk, is never offered again`() {
        assertEquals(SkipReason.ALREADY_SHRUNK, skip(video(name = "trip_sc.mp4")))
        assertEquals(SkipReason.ALREADY_SHRUNK, skip(video(), VideoContext(alreadyShrunk = true)))
        assertEquals(SkipReason.ALREADY_SHRUNK, skip(video(name = ".trip.sc.tmp")))
    }

    @Test fun `too little gain is out (target above 60 percent of the source)`() {
        // 1080p at ~9 Mbps: target 6 Mbps is 66 % of the source.
        assertEquals(SkipReason.LOW_GAIN, skip(video(size = 545 * mb)))
        // Same file, no H.265 encoder: 9.6 Mbps target is above the source.
        assertEquals(SkipReason.LOW_GAIN, skip(video(size = 545 * mb), hevc = false))
        // ~20 Mbps source: 6 Mbps is 30 % → fine.
        assertNull(skip(video(size = 1_220 * mb)))
    }

    @Test fun `names for the copy and the temp file`() {
        assertEquals("trip_sc.mp4", VideoRules.outputName("trip.mov"))
        assertEquals(".trip.sc.tmp", VideoRules.tempName("trip.mov"))
        assertEquals("noext_sc.mp4", VideoRules.outputName("noext"))
        assertTrue(VideoRules.isOutputName("TRIP_SC.MP4"))
        assertFalse(VideoRules.isOutputName("disc.mp4"))
    }

    // --- verification ---

    private fun check(
        duration: Long = 485_000L,
        audio: Boolean = true,
        w: Int = 1920,
        h: Int = 1080,
        rot: Int = 0,
        out: Long = 500 * mb,
        reads: Boolean = true,
        origRot: Int = 0,
    ) = OutputCheck(485_000L, duration, true, audio, 1920, 1080, w, h, origRot, rot, 1_400 * mb, out, reads)

    @Test fun `a good copy passes`() = assertNull(OutputVerifier.failure(check()))

    @Test fun `duration may differ by one second at most`() {
        assertNull(OutputVerifier.failure(check(duration = 484_000L)))
        assertEquals(VerifyFailure.DURATION, OutputVerifier.failure(check(duration = 483_900L)))
    }

    @Test fun `lost audio fails`() = assertEquals(VerifyFailure.AUDIO_LOST, OutputVerifier.failure(check(audio = false)))

    @Test fun `other resolution fails`() = assertEquals(VerifyFailure.RESOLUTION, OutputVerifier.failure(check(w = 1280, h = 720)))

    @Test fun `a turned picture fails, the same picture stored rotated passes`() {
        assertEquals(VerifyFailure.ROTATION, OutputVerifier.failure(check(rot = 180)))
        assertEquals(VerifyFailure.RESOLUTION, OutputVerifier.failure(check(rot = 90)))
        // Portrait phone video: stored 1920×1080 + 90°, copy stored 1080×1920 + 0°: shown the same.
        assertNull(OutputVerifier.failure(check(w = 1080, h = 1920, rot = 0, origRot = 90)))
        assertNull(OutputVerifier.failure(check(rot = 90, origRot = 90)))
    }

    @Test fun `the copy must be at most 70 percent of the original`() {
        assertNull(OutputVerifier.failure(check(out = 980 * mb)))
        assertEquals(VerifyFailure.NOT_SMALLER, OutputVerifier.failure(check(out = 981 * mb)))
    }

    @Test fun `an unreadable copy fails first`() = assertEquals(VerifyFailure.UNREADABLE, OutputVerifier.failure(check(reads = false, audio = false)))

    // --- queue ---

    private fun cand(size: Long, saving: Long, durationMs: Long = 485_000L) =
        Candidate(video(size = size, durationMs = durationMs), 6_000_000L, saving)

    @Test fun `copies must fit in free space minus 1 GB, the rest waits`() {
        val a = cand(2 * gb, 1_200 * mb) // copy 800 MB
        val b = cand(1_500 * mb, 900 * mb) // copy 600 MB
        val c = cand(1 * gb, 500 * mb) // copy 500 MB
        val plan = VideoQueue.plan(listOf(a, b, c), freeBytes = 2_500 * mb)
        assertEquals(listOf(a, b), plan.now)
        assertEquals(listOf(c), plan.nextRound)
        assertFalse(plan.noRoom)
    }

    @Test fun `no room at all when not even the smallest copy fits`() {
        val plan = VideoQueue.plan(listOf(cand(2 * gb, 1_200 * mb), cand(1 * gb, 500 * mb)), freeBytes = 1_499 * mb)
        assertTrue(plan.noRoom)
        assertTrue(plan.now.isEmpty())
        assertEquals(2, plan.nextRound.size)
        assertFalse(VideoQueue.plan(listOf(cand(1 * gb, 500 * mb)), freeBytes = 1_500 * mb).noRoom)
    }

    @Test fun `a round stays under five hours of video`() {
        val twoHours = 2L * 60 * 60 * 1000
        val list = List(4) { cand(5 * gb, 3 * gb, twoHours) }
        val plan = VideoQueue.plan(list, freeBytes = 100 * gb)
        assertEquals(2, plan.now.size)
        assertEquals(2, plan.nextRound.size)
        // A single video longer than a round still runs on its own.
        val long = VideoQueue.plan(listOf(cand(5 * gb, 3 * gb, 6L * 60 * 60 * 1000)), freeBytes = 100 * gb)
        assertEquals(1, long.now.size)
    }

    @Test fun `battery and heat stop the next video`() {
        assertEquals(DeviceBlock.LOW_BATTERY, VideoQueue.block(19, charging = false, thermalSevereOrWorse = false))
        assertNull(VideoQueue.block(19, charging = true, thermalSevereOrWorse = false))
        assertNull(VideoQueue.block(20, charging = false, thermalSevereOrWorse = false))
        assertEquals(DeviceBlock.TOO_HOT, VideoQueue.block(90, charging = true, thermalSevereOrWorse = true))
    }

    @Test fun `remaining time only after the first video's real speed`() {
        assertNull(VideoQueue.remainingMs(null, 600_000L))
        assertEquals(300_000L, VideoQueue.remainingMs(0.5, 600_000L))
    }

    // --- pending approval ---

    @Test fun `copies not approved in 7 days expire, nothing else`() {
        val day = 24L * 60 * 60 * 1000
        val now = 100 * day
        val old = PendingOutput("/a.mp4", "/a_sc.mp4", now - 7 * day, 1 * gb, 300 * mb, 600 * mb)
        val young = old.copy(originalPath = "/b.mp4", outputPath = "/b_sc.mp4", createdMillis = now - 6 * day)
        assertEquals(listOf(old), PendingRules.expired(listOf(old, young), now))
        assertEquals(700 * mb, old.savedBytes)
    }

    @Test fun `approval needs the small copy played, bulk needs one approval by hand`() {
        assertFalse(PendingRules.canApprove(playedSmallCopy = false))
        assertTrue(PendingRules.canApprove(playedSmallCopy = true))
        assertFalse(PendingRules.canApplyToRest(approvedByHand = 0, remaining = 6))
        assertTrue(PendingRules.canApplyToRest(approvedByHand = 1, remaining = 6))
        assertFalse(PendingRules.canApplyToRest(approvedByHand = 1, remaining = 0))
    }

    @Test fun `estimate accuracy`() {
        assertEquals(1.1, PendingRules.accuracy(1_100 * mb, 1 * gb)!!, 1e-9)
        assertNull(PendingRules.accuracy(1, 0))
    }
}
