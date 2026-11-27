package com.mrzekai.depoakilli.watch

import java.time.LocalDateTime
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchRulesTest {
    private val gb = 1_000_000_000L
    private val day = 86_400_000L
    private val total = 128 * gb

    private fun sample(dayEpoch: Long, usedGb: Long, videos: Long = 0, photos: Long = 0, whatsApp: Long = 0) = WatchSample(
        dayEpoch = dayEpoch,
        measuredAtMillis = dayEpoch * day + 12 * 3_600_000L,
        totalBytes = total,
        availableBytes = total - usedGb * gb,
        videoBytes = videos,
        photoBytes = photos,
        whatsAppBytes = whatsApp,
    )

    private fun input(
        hour: Int = 12,
        isPro: Boolean = true,
        threshold: WatchTopic? = null,
        growth: WatchGrowth? = null,
        history: List<SentNotice> = emptyList(),
        settings: WatchSettings = WatchSettings(enabled = true),
        lastWeeklyWeek: Int = 202647,
        now: Long = 20_000 * day,
    ) = WatchDecisionInput(
        nowMillis = now,
        localHour = hour,
        localWeek = 202648,
        localDayEpoch = now / day,
        isPro = isPro,
        settings = settings,
        threshold = threshold,
        growth = growth,
        newVideoBytes = 0L,
        history = history,
        lastWeeklyWeek = lastWeeklyWeek,
    )

    private fun growth(total: Long, whatsApp: Long = 0L) = WatchGrowth(
        days = 7,
        totalBytes = total,
        byKind = mapOf(WatchKind.WHATSAPP to whatsApp, WatchKind.OTHER to total - whatsApp),
    )

    @Test
    fun `threshold fires only on the way up and re-arms below 85`() {
        var state = ThresholdState()
        var fired: WatchTopic?
        WatchRules.threshold(state, 89).also { state = it.first; fired = it.second }
        assertNull(fired)
        WatchRules.threshold(state, 90).also { state = it.first; fired = it.second }
        assertEquals(WatchTopic.THRESHOLD_90, fired)
        WatchRules.threshold(state, 92).also { state = it.first; fired = it.second }
        assertNull(fired) // still above: no repeat
        WatchRules.threshold(state, 96).also { state = it.first; fired = it.second }
        assertEquals(WatchTopic.THRESHOLD_95, fired)
        WatchRules.threshold(state, 88).also { state = it.first; fired = it.second }
        assertNull(fired) // 85..89: not re-armed yet
        assertTrue(state.fired90)
        WatchRules.threshold(state, 84).also { state = it.first; fired = it.second }
        assertEquals(ThresholdState(), state)
        assertEquals(WatchTopic.THRESHOLD_90, WatchRules.threshold(state, 91).second)
    }

    @Test
    fun `turning the watch on above a level does not fire it at once`() {
        val state = WatchRules.initialThreshold(93)
        assertNull(WatchRules.threshold(state, 94).second)
        assertEquals(WatchTopic.THRESHOLD_95, WatchRules.threshold(state, 95).second)
        assertNull(WatchRules.threshold(WatchRules.initialThreshold(97), 99).second)
    }

    @Test
    fun `weekly growth needs a sample 6 to 8 days back and never guesses`() {
        assertNull(WatchRules.weeklyGrowth(listOf(sample(100, 50), sample(105, 52))))
        assertNull(WatchRules.weeklyGrowth(listOf(sample(100, 50), sample(109, 52))))
        val g = WatchRules.weeklyGrowth(listOf(sample(100, 50), sample(101, 51), sample(108, 53)))!!
        assertEquals(7, g.days) // the one closest to 7 days back (101)
        assertEquals(2 * gb, g.totalBytes)
        assertEquals(6, WatchRules.weeklyGrowth(listOf(sample(100, 50), sample(106, 51)))!!.days)
    }

    @Test
    fun `kinds are disjoint and add up to the headline`() {
        val base = sample(100, 50, videos = 10 * gb, photos = 5 * gb, whatsApp = 2 * gb)
        val latest = sample(107, 54, videos = 12 * gb, photos = 5 * gb + 400_000_000L, whatsApp = 2 * gb + 900_000_000L)
        val g = WatchRules.growth(base, latest)
        assertEquals(g.totalBytes, g.byKind.values.sum())
        assertEquals(2 * gb, g.bytes(WatchKind.VIDEOS))
        assertEquals(4 * gb - 2 * gb - 400_000_000L - 900_000_000L, g.bytes(WatchKind.OTHER))
        assertEquals(WatchKind.VIDEOS, g.topKind)
    }

    @Test
    fun `a week that freed space is never called added`() {
        val g = WatchRules.growth(sample(100, 60), sample(107, 55))
        assertTrue(g.decreased)
        assertTrue(WatchRules.candidates(input(growth = g, lastWeeklyWeek = 0)).none { it == WatchTopic.WEEKLY })
    }

    @Test
    fun `WhatsApp by path first then videos then photos and Trash or work files in none`() {
        assertEquals(WatchKind.WHATSAPP, WatchKinds.kindOf("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video/", "a.mp4", WatchKinds.MEDIA_TYPE_VIDEO))
        assertEquals(WatchKind.WHATSAPP, WatchKinds.kindOf("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/", "a.jpg", WatchKinds.MEDIA_TYPE_IMAGE))
        assertEquals(WatchKind.VIDEOS, WatchKinds.kindOf("DCIM/Camera/", "a.mp4", WatchKinds.MEDIA_TYPE_VIDEO))
        assertEquals(WatchKind.PHOTOS, WatchKinds.kindOf("DCIM/Camera/", "a.jpg", WatchKinds.MEDIA_TYPE_IMAGE))
        assertNull(WatchKinds.kindOf(".SmartCleanerTrash/", "a.mp4", WatchKinds.MEDIA_TYPE_VIDEO))
        assertNull(WatchKinds.kindOf("DCIM/Camera/", ".a.sc.tmp", WatchKinds.MEDIA_TYPE_VIDEO))
        assertNull(WatchKinds.kindOf("Download/", "a.pdf", 0))
    }

    @Test
    fun `at most one a day and three a week`() {
        val now = 20_000 * day
        val today = listOf(SentNotice(WatchTopic.WEEKLY, now - 3_600_000L))
        assertEquals(WatchDecision.Nothing, WatchRules.decide(input(threshold = WatchTopic.THRESHOLD_95, history = today, now = now)))
        val week = listOf(
            SentNotice(WatchTopic.WEEKLY, now - 2 * day),
            SentNotice(WatchTopic.WHATSAPP, now - 3 * day),
            SentNotice(WatchTopic.THRESHOLD_90, now - 4 * day),
        )
        assertEquals(WatchDecision.Nothing, WatchRules.decide(input(threshold = WatchTopic.THRESHOLD_95, history = week, now = now)))
        assertEquals(
            WatchDecision.Send(WatchTopic.THRESHOLD_95),
            WatchRules.decide(input(threshold = WatchTopic.THRESHOLD_95, history = week.take(2), now = now)),
        )
    }

    @Test
    fun `quiet hours hold the notice for the morning`() {
        assertEquals(WatchDecision.Defer(WatchTopic.THRESHOLD_90), WatchRules.decide(input(hour = 22, threshold = WatchTopic.THRESHOLD_90)))
        assertEquals(WatchDecision.Defer(WatchTopic.THRESHOLD_90), WatchRules.decide(input(hour = 8, threshold = WatchTopic.THRESHOLD_90)))
        assertEquals(WatchDecision.Send(WatchTopic.THRESHOLD_90), WatchRules.decide(input(hour = 9, threshold = WatchTopic.THRESHOLD_90)))
        assertEquals(WatchDecision.Send(WatchTopic.THRESHOLD_90), WatchRules.decide(input(hour = 20, threshold = WatchTopic.THRESHOLD_90)))
    }

    @Test
    fun `priority is threshold, WhatsApp, then weekly`() {
        val g = growth(3 * gb, whatsApp = 1_300_000_000L)
        assertEquals(
            listOf(WatchTopic.THRESHOLD_90, WatchTopic.WHATSAPP, WatchTopic.WEEKLY),
            WatchRules.candidates(input(threshold = WatchTopic.THRESHOLD_90, growth = g)),
        )
        assertEquals(WatchDecision.Send(WatchTopic.WHATSAPP), WatchRules.decide(input(growth = g)))
    }

    @Test
    fun `the same topic is not repeated within 7 days`() {
        val now = 20_000 * day
        val g = growth(3 * gb, whatsApp = 1_300_000_000L)
        val history = listOf(SentNotice(WatchTopic.WHATSAPP, now - 6 * day))
        assertEquals(WatchDecision.Send(WatchTopic.WEEKLY), WatchRules.decide(input(growth = g, history = history, now = now)))
        val older = listOf(SentNotice(WatchTopic.WHATSAPP, now - 8 * day))
        assertEquals(WatchDecision.Send(WatchTopic.WHATSAPP), WatchRules.decide(input(growth = g, history = older, now = now)))
    }

    @Test
    fun `weekly summary once a week and not below 500 MB`() {
        assertTrue(WatchTopic.WEEKLY in WatchRules.candidates(input(growth = growth(600_000_000L))))
        assertFalse(WatchTopic.WEEKLY in WatchRules.candidates(input(growth = growth(400_000_000L))))
        assertFalse(WatchTopic.WEEKLY in WatchRules.candidates(input(growth = growth(gb), lastWeeklyWeek = 202648)))
    }

    @Test
    fun `when Pro ends, Pro notices stop quietly and the free threshold stays`() {
        val g = growth(3 * gb, whatsApp = 2 * gb)
        assertEquals(listOf(WatchTopic.THRESHOLD_95), WatchRules.candidates(input(isPro = false, threshold = WatchTopic.THRESHOLD_95, growth = g)))
        assertEquals(WatchDecision.Nothing, WatchRules.decide(input(isPro = false, growth = g)))
    }

    @Test
    fun `the watch off or a switch off sends nothing`() {
        assertEquals(WatchDecision.Nothing, WatchRules.decide(input(threshold = WatchTopic.THRESHOLD_95, settings = WatchSettings(enabled = false))))
        assertEquals(
            WatchDecision.Nothing,
            WatchRules.decide(input(threshold = WatchTopic.THRESHOLD_95, settings = WatchSettings(enabled = true, thresholdAlerts = false))),
        )
    }

    @Test
    fun `samples keep 35 days, one per day`() {
        val list = (0L..40L).fold(emptyList<WatchSample>()) { acc, d -> WatchRules.add(acc, sample(1000 + d, 50)) }
        assertEquals(35, list.size)
        assertEquals(1006L, list.first().dayEpoch)
        val replaced = WatchRules.add(list, sample(1040, 60))
        assertEquals(35, replaced.size)
        assertEquals(60 * gb, replaced.last().usedBytes)
    }

    @Test
    fun `stale after 3 days without a look`() {
        val now = 20_000 * day
        assertFalse(WatchRules.isStale(now - 2 * day, now))
        assertTrue(WatchRules.isStale(now - 3 * day, now))
        assertEquals(4L, WatchRules.daysSince(now - 4 * day - 1, now))
        assertFalse(WatchRules.isStale(0L, now))
    }

    @Test
    fun `the daily job lands in local daytime and the morning job between 9 and 10`() {
        val random = Random(7)
        listOf(LocalDateTime.of(2026, 11, 27, 2, 30), LocalDateTime.of(2026, 11, 27, 13, 0), LocalDateTime.of(2026, 11, 27, 23, 10)).forEach { now ->
            repeat(50) {
                val at = now.plusNanos(WatchScheduler.delayToNextDaytime(now, random) * 1_000_000L)
                assertTrue(at.hour in 10..17)
                assertTrue(at.isAfter(now))
                val morning = now.plusNanos(WatchScheduler.delayToNextMorning(now, random) * 1_000_000L)
                assertEquals(9, morning.hour)
                assertTrue(morning.isAfter(now))
            }
        }
    }
}
