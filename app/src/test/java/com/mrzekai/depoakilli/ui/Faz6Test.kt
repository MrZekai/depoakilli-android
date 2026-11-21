package com.mrzekai.depoakilli.ui

import com.mrzekai.depoakilli.model.AppPlan
import com.mrzekai.depoakilli.model.InstalledAppEntry
import com.mrzekai.depoakilli.model.PlanFigures
import com.mrzekai.depoakilli.model.buildPlan
import com.mrzekai.depoakilli.ui.smart.unusedAppsForSmartReview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Faz 6: apps in the plan (E1) and "Free now" first on the result (E2). */
class Faz6Test {
    private val day = 24L * 60L * 60L * 1000L
    private val mb = 1024L * 1024L
    private val now = System.currentTimeMillis()

    private fun app(
        pkg: String,
        appMb: Long,
        dataMb: Long = 0L,
        lastUsedDaysAgo: Long = 1L,
        system: Boolean = false,
    ) = InstalledAppEntry(
        packageName = pkg,
        label = pkg,
        appBytes = appMb * mb,
        dataBytes = dataMb * mb,
        lastUsedMillis = if (lastUsedDaysAgo < 0) 0L else now - lastUsedDaysAgo * day,
        systemApp = system,
    )

    // ---- E1 ----

    @Test
    fun `rows keep big user apps only, biggest first, at most five`() {
        val apps = listOf(
            app("small", 400),
            app("sys", 3000, system = true),
            app("own", 2000),
            app("a", 600),
            app("b", 1500),
            app("c", 900),
            app("d", 700),
            app("e", 800),
            app("f", 1100),
        )
        val rows = AppPlan.rows(apps, hasUsageAccess = true, ownPackage = "own", nowMillis = now)!!
        assertEquals(listOf("b", "f", "c", "e", "d"), rows.map { it.app.packageName })
    }

    @Test
    fun `without usage access there are no rows`() {
        assertNull(AppPlan.rows(listOf(app("a", 2000)), hasUsageAccess = false, ownPackage = "own", nowMillis = now))
    }

    @Test
    fun `unused for 30 days uninstalls, data heavy and big open settings`() {
        assertEquals(AppPlan.Action.UNINSTALL, AppPlan.actionFor(app("u", 600, lastUsedDaysAgo = 30), now))
        assertEquals(AppPlan.Action.SETTINGS_DATA_HEAVY, AppPlan.actionFor(app("d", 200, dataMb = 900), now))
        assertEquals(AppPlan.Action.SETTINGS, AppPlan.actionFor(app("b", 900, dataMb = 100), now))
        // Never-seen usage is not "unused": no uninstall offer without a real date.
        assertEquals(AppPlan.Action.SETTINGS, AppPlan.actionFor(app("n", 900, lastUsedDaysAgo = -1), now))
    }

    @Test
    fun `used apps never change the totals`() {
        val plan = buildPlan(emptyList())
        val unusedOld = app("old", 700, lastUsedDaysAgo = 120)
        val usedBig = app("big", 4000, lastUsedDaysAgo = 1)
        val withoutBig = PlanFigures.of(plan, unusedAppsForSmartReview(listOf(unusedOld), true))
        val withBig = PlanFigures.of(plan, unusedAppsForSmartReview(listOf(unusedOld, usedBig), true))
        assertEquals(withoutBig, withBig)
        assertEquals(withoutBig.homeMaxBytes, withBig.homeMaxBytes)

        val rows = AppPlan.rows(listOf(unusedOld, usedBig), true, "own", now)
        val unused = unusedAppsForSmartReview(listOf(unusedOld, usedBig), true)
        // "Apps hold X more" counts only the app still in use.
        assertEquals(usedBig.totalBytes, AppPlan.moreBytes(rows, unused))
        assertEquals(0L, AppPlan.moreBytes(null, unused))
    }

    // ---- E2 ----

    private fun result(
        trashed: Long = 0L,
        deleted: Long = 0L,
        before: Long = 0L,
        after: Long = 0L,
    ) = CleanupResult(
        deletedBytes = deleted,
        deletedCount = 1,
        failedCount = 0,
        beforeAvailableBytes = before,
        afterAvailableBytes = after,
        kind = CleanupResultKind.FILES,
        trashedBytes = trashed,
        trashEntryIds = if (trashed > 0L) listOf("t1") else emptyList(),
    )

    @Test
    fun `nothing trashed shows only Done`() {
        val figures = ResultFigures.of(result(deleted = 5 * mb))
        assertTrue(!figures.canEmptyTrash)
    }

    @Test
    fun `after emptying the trash the giant figure is the deleted bytes`() {
        val figures = ResultFigures.of(result(deleted = 300 * mb, before = 1000 * mb, after = 1300 * mb))
        assertEquals(ResultFigures.Headline.FREED, figures.headline)
        assertEquals(1000 * mb to 1300 * mb, figures.freeSpaceChange)
    }

    @Test
    fun `free space pill hides when it did not grow or was not measured`() {
        assertNull(ResultFigures.of(result(deleted = 300 * mb, before = 1000 * mb, after = 1000 * mb)).freeSpaceChange)
        assertNull(ResultFigures.of(result(deleted = 300 * mb, before = 1000 * mb, after = 900 * mb)).freeSpaceChange)
        assertNull(ResultFigures.of(result(deleted = 300 * mb)).freeSpaceChange)
        assertNotNull(ResultFigures.of(result(deleted = 1L, before = 1L, after = 2L)).freeSpaceChange)
    }

    @Test
    fun `while files wait in trash Free now comes first`() {
        val figures = ResultFigures.of(result(trashed = 200 * mb))
        assertEquals(ResultFigures.Headline.TRASHED, figures.headline)
        assertTrue(figures.canEmptyTrash)
        assertNull(figures.freeSpaceChange)
    }

    // ---- Store QA: big button never breaks a word ----

    @Test
    fun `giant button label shrinks until every word fits`() {
        // Pretend each character is 0.6 em wide and the line holds 150 px.
        val fits = { word: String, sp: Float -> word.length * sp * 0.6f <= 150f }
        val sp = com.mrzekai.depoakilli.ui.common.giantLabelSize("AUFRÄUMEN", 28f, fits)
        assertTrue(9 * sp * 0.6f <= 150f)
        assertEquals(28f, com.mrzekai.depoakilli.ui.common.giantLabelSize("CLEAN UP", 28f, fits))
    }
}
