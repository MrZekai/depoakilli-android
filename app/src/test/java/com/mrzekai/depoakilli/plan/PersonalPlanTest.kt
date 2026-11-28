package com.mrzekai.depoakilli.plan

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalPlanTest {
    private val gb = PlanBuilder.GB
    private fun c(key: String, kind: StepKind, bytes: Long, pro: Boolean = false, age: Long = 0) =
        PlanCandidate(key, kind, bytes, 10, ageDays = age, proOnly = pro)

    private val candidates = listOf(
        c("SCREENSHOT", StepKind.ASK, 620_000_000),
        c("JUNK", StepKind.NO_LOSS, 2_200_000_000),
        c("VIDEOS", StepKind.SHRINK, 5_100_000_000, pro = true),
        c("SIMILAR", StepKind.SIMILAR, 4_100_000_000, pro = true),
        c("OLD_VIDEO", StepKind.ASK, 8_400_000_000, age = 400),
    )

    @Test
    fun `cold start - least painful order, no learning claims`() {
        val plan = PlanBuilder.build(candidates, 20 * gb, PreferenceSnapshot())
        assertEquals(listOf("JUNK", "VIDEOS", "SIMILAR", "OLD_VIDEO", "SCREENSHOT"), plan.steps.map { it.candidate.key })
        assertFalse(plan.learned)
        assertEquals(StepReason.OLD_FILES, plan.steps[3].reason)
        assertEquals(StepReason.YOUR_CALL, plan.steps[4].reason)
    }

    @Test
    fun `target cannot be reached - honest total`() {
        val plan = PlanBuilder.build(candidates, 50 * gb, PreferenceSnapshot())
        assertFalse(plan.reachesTarget)
        assertEquals(20_420_000_000L, plan.reachableBytes)
        assertEquals(11_220_000_000L, plan.freeReachableBytes)
        assertEquals(5_100_000_000L, plan.withoutDeletingBytes)
    }

    @Test
    fun `stops once the target is reached`() {
        val plan = PlanBuilder.build(candidates, 5 * gb, PreferenceSnapshot())
        assertEquals(listOf("JUNK", "VIDEOS"), plan.steps.map { it.candidate.key })
        assertTrue(plan.reachesTarget)
    }

    @Test
    fun `learning orders and explains but needs 20 decisions`() {
        var p = PreferenceSnapshot()
        repeat(9) { p = p.record("SCREENSHOT", delete = true) }
        repeat(1) { p = p.record("SCREENSHOT", delete = false) }
        repeat(9) { p = p.record("OLD_VIDEO", delete = false) }
        assertFalse(p.learned)
        assertNull(p.preselect("SCREENSHOT"))
        p = p.record("OLD_VIDEO", delete = true)
        assertTrue(p.learned)
        assertEquals(true, p.preselect("SCREENSHOT"))
        assertEquals(false, p.preselect("OLD_VIDEO"))
        val plan = PlanBuilder.build(candidates, 50 * gb, p)
        val keys = plan.steps.map { it.candidate.key }
        assertEquals("OLD_VIDEO", keys.last())
        assertTrue(keys.indexOf("SCREENSHOT") < keys.indexOf("OLD_VIDEO"))
        assertEquals(StepReason.YOU_DELETE_THESE, plan.steps.first { it.candidate.key == "SCREENSHOT" }.reason)
        assertEquals(StepReason.YOU_KEEP_THESE, plan.steps.last().reason)
    }

    @Test
    fun `learning never skips a step's own approval - a plan only orders`() {
        // The plan has no delete action: every step is a candidate the user opens.
        val plan = PlanBuilder.build(candidates, 50 * gb, PreferenceSnapshot())
        assertEquals(candidates.size, plan.steps.size)
    }

    @Test
    fun `reset forgets everything`() {
        var p = PreferenceSnapshot()
        repeat(25) { p = p.record("X", true) }
        assertTrue(p.learned)
        assertFalse(PreferenceSnapshot().learned)
    }

    @Test
    fun `below 80 percent target`() {
        assertEquals(10L * gb, PlanTarget.BelowPercent(80).bytesNeeded(100 * gb, 10 * gb))
        assertEquals(0L, PlanTarget.BelowPercent(80).bytesNeeded(100 * gb, 50 * gb))
        assertEquals(5 * gb, PlanTarget.Bytes(5 * gb).bytesNeeded(100 * gb, 1))
    }
}

class FileFilterTest {
    private val zone = ZoneOffset.UTC
    private val now = LocalDate.of(2026, 11, 28).atStartOfDay(zone).toInstant().toEpochMilli()
    private fun at(y: Int, m: Int) = LocalDate.of(y, m, 1).atStartOfDay(zone).toInstant().toEpochMilli()
    private fun f(name: String, path: String, mime: String, size: Long, time: Long) = FilterFile(name, path, mime, size, time)

    private val shot = f("Screenshot_1.png", "Pictures/Screenshots", "image/png", 300_000, at(2025, 5))
    private val camVideo = f("VID.mp4", "DCIM/Camera", "video/mp4", 800_000_000, at(2026, 3))
    private val waVideo = f("v.mp4", "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video", "video/mp4", 40_000_000, at(2026, 9))
    private val oldPdf = f("a.pdf", "Download", "application/pdf", 2_000_000, at(2024, 1))

    @Test
    fun `rows combine, chips in a row are alternatives`() {
        val videos = FileFilter(types = setOf(FilterType.VIDEO))
        assertTrue(videos.matches(camVideo, now, zone))
        assertTrue(videos.matches(waVideo, now, zone))
        assertFalse(videos.matches(shot, now, zone))
        val waOrCam = videos.copy(sources = setOf(FilterSource.WHATSAPP))
        assertFalse(waOrCam.matches(camVideo, now, zone))
        assertTrue(waOrCam.copy(sources = setOf(FilterSource.WHATSAPP, FilterSource.CAMERA)).matches(camVideo, now, zone))
        assertFalse(videos.copy(size = FilterSize.GB1).matches(camVideo, now, zone))
    }

    @Test
    fun `presets`() {
        assertTrue(FileFilter.LAST_YEAR_SCREENSHOTS.matches(shot, now, zone))
        assertTrue(FileFilter.WHATSAPP_BIG_VIDEOS.matches(waVideo, now, zone))
        assertFalse(FileFilter.WHATSAPP_BIG_VIDEOS.matches(camVideo, now, zone))
        assertTrue(FileFilter.OLD_DOWNLOADS.matches(oldPdf, now, zone))
        assertTrue(FileFilter(types = setOf(FilterType.DOCUMENT, FilterType.DOWNLOADED)).matches(oldPdf, now, zone))
    }

    @Test
    fun `dates`() {
        assertTrue(FileFilter(date = FilterDate.THIS_YEAR).matches(camVideo, now, zone))
        assertFalse(FileFilter(date = FilterDate.OLDER_6_MONTHS).matches(waVideo, now, zone))
        assertTrue(FileFilter(date = FilterDate.OLDER_1_YEAR).matches(shot, now, zone))
        assertTrue(FileFilter().isEmpty)
    }
}
