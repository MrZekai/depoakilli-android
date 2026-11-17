package com.mrzekai.depoakilli.data

import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.model.AiAssessment
import com.mrzekai.depoakilli.model.CleanCategory
import com.mrzekai.depoakilli.model.CleanupGroup
import com.mrzekai.depoakilli.model.IndexedFile
import java.util.Locale
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCleaningEngineTest {
    private val now = 2_000_000_000_000L
    private val engine = AiCleaningEngine { now }
    private val mb = 1_000_000L

    private fun group(file: IndexedFile, e: AiCleaningEngine = engine): CleanupGroup? = e.assess(file)?.group

    // --- thresholds: threshold - 1 and threshold for each age/size rule ---

    @Test
    fun `whatsapp status needs two days`() {
        val path = "Android/media/com.whatsapp/WhatsApp/Media/.Statuses/"
        assertNull(group(file("s.jpg", "image/jpeg", 1, path)))
        assertEquals(CleanupGroup.WHATSAPP_STATUS, group(file("s.jpg", "image/jpeg", 2, path)))
    }

    @Test
    fun `whatsapp sent needs thirty days`() {
        val path = "WhatsApp/Media/WhatsApp Images/Sent/"
        assertNull(group(file("a.jpg", "image/jpeg", 29, path)))
        assertEquals(CleanupGroup.WHATSAPP_SENT, group(file("a.jpg", "image/jpeg", 30, path)))
    }

    @Test
    fun `whatsapp video needs twenty five megabytes`() {
        val path = "WhatsApp/Media/WhatsApp Video/"
        assertNull(group(file("v.mp4", "video/mp4", 1, path, 25 * mb - 1)))
        assertEquals(CleanupGroup.WHATSAPP_VIDEO, group(file("v.mp4", "video/mp4", 1, path, 25 * mb)))
    }

    @Test
    fun `whatsapp voice notes need one hundred eighty days`() {
        val path = "WhatsApp/Media/WhatsApp Voice Notes/202401/"
        assertNull(group(file("PTT-1.opus", "audio/ogg", 179, path)))
        assertEquals(CleanupGroup.WHATSAPP_VOICE, group(file("PTT-1.opus", "audio/ogg", 180, path)))
        assertEquals(
            CleanupGroup.WHATSAPP_VOICE,
            group(file("ptt-2.opus", "audio/ogg", 200, "WhatsApp/Media/WhatsApp Audio/")),
        )
    }

    @Test
    fun `android trash files are offered for review and old pending writes are junk`() {
        val trashed = requireNotNull(engine.assess(file(".trashed-1767225600-IMG_1.jpg", "image/jpeg", 3, "DCIM/Camera/")))
        assertEquals(CleanupGroup.SYSTEM_TRASH, trashed.group)
        assertFalse(trashed.recommended)
        assertEquals(com.mrzekai.depoakilli.model.DeleteMode.PERMANENT, CleanupGroup.SYSTEM_TRASH.deleteMode)
        assertNull(group(file(".pending-1767225600-a.jpg", "image/jpeg", 6, "DCIM/Camera/")))
        assertEquals(CleanupGroup.TEMP_FILES, group(file(".pending-1767225600-a.jpg", "image/jpeg", 7, "DCIM/Camera/")))
    }

    @Test
    fun `raw copies are review only and name their partner`() {
        val raw = file("IMG_1.dng", "image/x-adobe-dng", 30, "DCIM/Camera/", 25 * mb)
        assertNull(engine.assess(raw))
        val assessment = engine.rawCopyAssessment(raw, "IMG_1.jpg")
        assertEquals(CleanupGroup.RAW_COPY, assessment.group)
        assertFalse(assessment.recommended)
        assertEquals(listOf<Any>("IMG_1.jpg"), assessment.reasonArgs)
    }

    @Test
    fun `older mid-size videos are offered after large old videos`() {
        val cam = "DCIM/Camera/"
        assertEquals(CleanupGroup.OLD_VIDEO, group(file("v.mp4", "video/mp4", 200, cam, 40 * mb)))
        assertEquals(CleanupGroup.LARGE_VIDEO, group(file("v.mp4", "video/mp4", 100, cam, 150 * mb)))
        assertNull(group(file("v.mp4", "video/mp4", 179, cam, 40 * mb)))
        assertNull(group(file("v.mp4", "video/mp4", 200, cam, 20 * mb - 1)))
        assertEquals(CleanupGroup.OLD_VIDEO, group(file("v.mp4", "video/mp4", 180, cam, 20 * mb)))
        assertFalse(CleanupGroup.OLD_VIDEO.auto)
    }

    @Test
    fun `older whatsapp media groups use their age thresholds`() {
        val wa = "Android/media/com.whatsapp/WhatsApp/Media/"
        assertNull(group(file("v.mp4", "video/mp4", 89, wa + "WhatsApp Video/", 5 * mb)))
        assertEquals(CleanupGroup.WHATSAPP_OLD_VIDEO, group(file("v.mp4", "video/mp4", 90, wa + "WhatsApp Video/", 5 * mb)))
        assertEquals(CleanupGroup.WHATSAPP_OLD_VIDEO, group(file("v.mp4", "video/mp4", 100, wa + "WhatsApp Video/", 5 * mb)))
        assertNull(group(file("p.jpg", "image/jpeg", 179, wa + "WhatsApp Images/")))
        assertEquals(CleanupGroup.WHATSAPP_OLD_IMAGE, group(file("p.jpg", "image/jpeg", 180, wa + "WhatsApp Images/")))
        assertNull(group(file("d.pdf", "application/pdf", 89, wa + "WhatsApp Documents/")))
        assertEquals(CleanupGroup.WHATSAPP_DOCUMENT, group(file("d.pdf", "application/pdf", 90, wa + "WhatsApp Documents/")))
        assertNull(group(file("a.mp3", "audio/mpeg", 179, wa + "WhatsApp Audio/")))
        assertEquals(CleanupGroup.WHATSAPP_OLD_AUDIO, group(file("a.mp3", "audio/mpeg", 180, wa + "WhatsApp Audio/")))
        // Priority: an old video in Sent stays a sent item.
        assertEquals(CleanupGroup.WHATSAPP_SENT, group(file("v.mp4", "video/mp4", 200, wa + "WhatsApp Video/Sent/", 5 * mb)))
        // Stickers, profile photos and chat data are never offered.
        assertNull(group(file("s.webp", "image/webp", 900, wa + "WhatsApp Stickers/")))
        assertNull(group(file("p.jpg", "image/jpeg", 900, wa + "WhatsApp Profile Photos/")))
        assertNull(group(file("msgstore.db.crypt14", "application/octet-stream", 900, "Android/media/com.whatsapp/WhatsApp/Databases/")))
        assertNull(group(file("backup.crypt14", "application/octet-stream", 900, "Android/media/com.whatsapp/WhatsApp/Backups/")))
        for (g in listOf(CleanupGroup.WHATSAPP_OLD_VIDEO, CleanupGroup.WHATSAPP_OLD_IMAGE, CleanupGroup.WHATSAPP_OLD_AUDIO, CleanupGroup.WHATSAPP_DOCUMENT)) {
            assertFalse(g.auto)
        }
        assertFalse(CleanupGroup.WHATSAPP_DOCUMENT.groupSelectable)
    }

    @Test
    fun `other whatsapp media is never a candidate and nothing in whatsapp is auto`() {
        assertNull(group(file("photo.jpg", "image/jpeg", 179, "WhatsApp/Media/WhatsApp Images/")))
        val all = listOf(
            file("s.jpg", "image/jpeg", 5, "WhatsApp/Media/.Statuses/"),
            file("a.jpg", "image/jpeg", 50, "WhatsApp/Media/WhatsApp Images/Sent/"),
            file("v.mp4", "video/mp4", 1, "WhatsApp/Media/WhatsApp Video/", 500 * mb),
            file("big.tmp", "application/octet-stream", 50, "WhatsApp/Media/"),
            file("old.apk", APK, 50, "WhatsApp/Media/WhatsApp Documents/"),
        )
        all.forEach { candidate -> assertFalse(engine.assess(candidate)?.recommended ?: false) }
    }

    @Test
    fun `thumbnails only in the standard media folders`() {
        for (path in listOf("DCIM/.thumbnails/", "Pictures/.thumbnails/", "Movies/.thumbnails/", "Music/.thumbnails/")) {
            val result = requireNotNull(engine.assess(file("t.jpg", "image/jpeg", 0, path)))
            assertEquals(CleanupGroup.THUMBNAILS, result.group)
            assertTrue(result.recommended)
        }
        assertNull(group(file("holiday.jpg", "image/jpeg", 45, "Pictures/Thumbnails/")))
        assertNull(group(file("t.jpg", "image/jpeg", 45, "Documents/.thumbnails/")))
    }

    @Test
    fun `unfinished downloads need fourteen days in downloads`() {
        assertNull(group(file("a.crdownload", OCTET, 13)))
        for (ext in listOf("crdownload", "part", "download")) {
            val result = requireNotNull(engine.assess(file("a.$ext", OCTET, 14)))
            assertEquals(CleanupGroup.PARTIAL_DOWNLOADS, result.group)
            assertTrue(result.recommended)
        }
        assertNull(group(file("a.part", OCTET, 40, "Documents/")))
    }

    @Test
    fun `temporary files need seven days and stay out of android media`() {
        assertNull(group(file("x.tmp", OCTET, 6, "Documents/")))
        assertEquals(CleanupGroup.TEMP_FILES, group(file("x.tmp", OCTET, 7, "Documents/")))
        assertEquals(CleanupGroup.TEMP_FILES, group(file("x.temp", OCTET, 7, "Documents/")))
        assertFalse(engine.assess(file("x.tmp", OCTET, 50, "Android/media/com.other/"))?.recommended ?: false)
        assertNull(group(file("x.cache", OCTET, 50, "Documents/")))
    }

    @Test
    fun `files in temp folders are no longer junk by folder name`() {
        for (folder in listOf("Temp", "tmp", "temporary")) {
            assertNull(engine.assess(file("lease.pdf", "application/pdf", 120, "Documents/$folder/")))
        }
    }

    @Test
    fun `screen recordings need thirty days`() {
        val path = "DCIM/Screen recordings/"
        assertNull(group(file("rec.mp4", "video/mp4", 29, path)))
        assertEquals(CleanupGroup.SCREEN_RECORDING, group(file("rec.mp4", "video/mp4", 30, path)))
        assertEquals(CleanupGroup.SCREEN_RECORDING, group(file("Screenrecorder-1.mp4", "video/mp4", 40, "DCIM/Screenshots/")))
        assertEquals(CleanupGroup.SCREEN_RECORDING, group(file("screen-2024.mp4", "video/mp4", 40, "Movies/")))
    }

    @Test
    fun `screenshots need thirty days and recordings are not screenshots`() {
        val path = "Pictures/Screenshots/"
        assertNull(group(file("Screenshot_1.png", "image/png", 29, path)))
        assertEquals(CleanupGroup.SCREENSHOT, group(file("Screenshot_1.png", "image/png", 30, path)))
        val recording = file("Screenrecorder-2026.mp4", "video/mp4", 40, "DCIM/Screenshots/")
        assertNull(engine.assessFocusedScreenshot(recording))
        assertTrue(engine.assess(recording)?.category != CleanCategory.SCREENSHOT)
    }

    @Test
    fun `large videos need one hundred megabytes and ninety days`() {
        assertNull(group(file("clip.mp4", "video/mp4", 90, "Movies/", 100 * mb - 1)))
        assertEquals(CleanupGroup.LARGE_VIDEO, group(file("clip.mp4", "video/mp4", 90, "Movies/", 100 * mb)))
        assertEquals(CleanupGroup.LARGE_OTHER, group(file("clip.mp4", "video/mp4", 89, "Movies/", 100 * mb)))
    }

    @Test
    fun `downloads need ninety days and documents are separated`() {
        assertNull(group(file("a.zip", "application/zip", 89)))
        assertEquals(CleanupGroup.DOWNLOAD_FILE, group(file("a.zip", "application/zip", 90)))
        for (name in listOf("lease.pdf", "cv.docx", "photo.jpg", "scan.heic", "book.epub")) {
            val result = requireNotNull(engine.assess(file(name, OCTET, 90)))
            assertEquals(CleanupGroup.DOWNLOAD_DOCUMENT, result.group)
            assertFalse(result.recommended)
        }
        assertFalse(CleanupGroup.DOWNLOAD_DOCUMENT.groupSelectable)
    }

    @Test
    fun `other large files need one hundred megabytes`() {
        assertNull(group(file("backup.zip", "application/zip", 1, "Backups/", 100 * mb - 1)))
        assertEquals(CleanupGroup.LARGE_OTHER, group(file("backup.zip", "application/zip", 1, "Backups/", 100 * mb)))
    }

    // --- installers ---

    @Test
    fun `four installer cases`() {
        val apk = file("app.apk", APK, 1, "Download/").copy(uri = "file:///storage/emulated/0/Download/app.apk")
        fun engineWith(info: ApkInfo?, installed: Long?) = AiCleaningEngine(
            apkInfoReader = { info },
            installedVersions = { installed },
            nowMillis = { now },
        )
        val info = ApkInfo("com.example", 10)
        val sameVersion = requireNotNull(engineWith(info, 10).assess(apk))
        assertEquals(CleanupGroup.APK_INSTALLED, sameVersion.group)
        assertTrue(sameVersion.recommended)
        assertEquals(CleanupGroup.APK_INSTALLED, group(apk, engineWith(info, 11)))
        assertEquals(CleanupGroup.APK_OTHER, group(apk, engineWith(info, 9)))
        assertEquals(CleanupGroup.APK_OTHER, group(apk, engineWith(info, null)))
        assertEquals(CleanupGroup.APK_OTHER, group(apk, engineWith(null, 10)))
        assertFalse(requireNotNull(engineWith(null, null).assess(apk)).recommended)
    }

    @Test
    fun `older versions of the same installer are marked`() {
        val e = AiCleaningEngine(
            apkInfoReader = { path -> ApkInfo("com.example", path.substringAfterLast('v').substringBefore('.').toLong()) },
            installedVersions = { null },
            nowMillis = { now },
        )
        val items = (1..3).map { version ->
            val f = file("app-v$version.apk", APK, 1).copy(uri = "file:///sdcard/Download/app-v$version.apk")
            f.toCleanable(requireNotNull(e.assess(f)))
        }
        val marked = e.markOlderApkVersions(items).associate { it.name to it.assessment.group }
        assertEquals(CleanupGroup.APK_OLD_VERSION, marked["app-v1.apk"])
        assertEquals(CleanupGroup.APK_OLD_VERSION, marked["app-v2.apk"])
        assertEquals(CleanupGroup.APK_OTHER, marked["app-v3.apk"])
    }

    // --- priority order ---

    @Test
    fun `rules apply in priority order`() {
        assertEquals(CleanupGroup.LARGE_VIDEO, group(file("old.mp4", "video/mp4", 200, "Download/", 300 * mb)))
        assertEquals(
            CleanupGroup.WHATSAPP_VIDEO,
            group(file("old.mp4", "video/mp4", 200, "WhatsApp/Media/WhatsApp Video/", 300 * mb)),
        )
        assertEquals(CleanupGroup.PARTIAL_DOWNLOADS, group(file("big.part", OCTET, 30, "Download/", 300 * mb)))
        assertEquals(CleanupGroup.SCREENSHOT, group(file("Screenshot_1.png", "image/png", 200, "Download/")))
    }

    @Test
    fun `protected user data is never assessed`() {
        assertNull(engine.assess(file("msgstore.db.crypt14", OCTET, 400, "Android/media/com.whatsapp/WhatsApp/Databases/", 300 * mb)))
        assertNull(engine.assessDeep(file("backup.crypt15", OCTET, 400, "Download/", 300 * mb)))
    }

    @Test
    fun `camera photos documents and dcim items are never auto`() {
        assertNull(engine.assess(file("IMG_1001.jpg", "image/jpeg", 2, "DCIM/Camera/")))
        val cameraVideo = requireNotNull(engine.assess(file("VID_1.mp4", "video/mp4", 400, "DCIM/Camera/", 900 * mb)))
        assertFalse(cameraVideo.recommended)
        val doc = requireNotNull(engine.assess(file("tax.pdf", "application/pdf", 400)))
        assertFalse(doc.recommended)
    }

    @Test
    fun `age comes from the date in the name before the modification time`() {
        // Copied to a new phone yesterday, but filmed in 2023: the name decides.
        val copied = file("VID_20230611_120000.mp4", "video/mp4", 1, "DCIM/Camera/", 150 * mb)
        assertEquals(CleanupGroup.LARGE_VIDEO, group(copied))
        assertTrue(engine.ageDays(copied) > 365)
        // No date in the name: the modification time is used.
        assertEquals(1L, engine.ageDays(file("clip.mp4", "video/mp4", 1, "DCIM/Camera/")))
    }

    @Test
    fun `auto groups ignore the date in the name`() {
        // A temporary file named with an old date but modified yesterday stays out.
        assertNull(group(file("cache_20200101.tmp", "application/octet-stream", 1, "Documents/")))
        assertEquals(CleanupGroup.TEMP_FILES, group(file("cache_20200101.tmp", "application/octet-stream", 7, "Documents/")))
    }

    @Test
    fun `download path matching is stable across Turkish I variants and locales`() {
        val previous = Locale.getDefault()
        try {
            for (locale in listOf(Locale.forLanguageTag("tr-TR"), Locale.ENGLISH)) {
                Locale.setDefault(locale)
                assertTrue(StoragePathRules.isDownloadPath("İndirilenler/"))
                assertTrue(StoragePathRules.isDownloadPath("INDIRILENLER/"))
                assertFalse(StoragePathRules.isDownloadPath("Pictures/"))
                assertEquals(CleanCategory.OLD_DOWNLOAD, engine.assess(file("old.zip", "application/zip", 120, "İndirilenler/"))?.category)
            }
        } finally {
            Locale.setDefault(previous)
        }
    }

    // --- tool-screen policies kept from earlier versions ---

    @Test
    fun `focused screenshots include recent and old items but never preselect`() {
        for (candidate in listOf(
            file("Screenshot_1.png", "image/png", 1, "DCIM/Screenshots/"),
            file("Screenshot_31.png", "image/png", 31, "Pictures/Screenshots/"),
        )) {
            val result = requireNotNull(engine.assessFocusedScreenshot(candidate))
            assertEquals(CleanCategory.SCREENSHOT, result.category)
            assertFalse(result.recommended)
        }
        assertNull(engine.assessFocusedScreenshot(file("IMG_2001.jpg", "image/jpeg", 1, "DCIM/Camera/")))
    }

    @Test
    fun `deep clean surfaces recent screenshots without preselecting`() {
        val candidate = file("Screenshot_recent.png", "image/png", 10, "Pictures/Screenshots/")
        assertNull(engine.assess(candidate))
        val deep = requireNotNull(engine.assessDeep(candidate))
        assertEquals(CleanupGroup.SCREENSHOT, deep.group)
        assertFalse(deep.recommended)
    }

    @Test
    fun `personal folder with whatsapp in its name is not whatsapp media`() {
        val result = engine.assess(file("sent-video.mp4", "video/mp4", 45, "Documents/My WhatsApp Backup/Sent/"))
        assertNull(result)
    }

    @Test
    fun `duplicate assessments map to their groups`() {
        assertEquals(CleanupGroup.DUPLICATE_SAFE, engine.duplicateAssessment(true).group)
        assertTrue(engine.duplicateAssessment(true).recommended)
        assertEquals(CleanupGroup.DUPLICATE_PERSONAL, engine.duplicateAssessment(false).group)
        assertFalse(engine.duplicateAssessment(false).recommended)
        val sampled: AiAssessment = engine.sampledDuplicateAssessment()
        assertEquals(CleanupGroup.DUPLICATE_UNVERIFIED, sampled.group)
        assertFalse(sampled.recommended)
        assertEquals(R.string.reason_sampled_duplicate, sampled.reasonRes)
        assertFalse(CleanupGroup.DUPLICATE_UNVERIFIED.groupSelectable)
    }

    @Test
    fun `auto groups match the plan specification`() {
        val auto = CleanupGroup.entries.filter(CleanupGroup::auto).toSet()
        assertEquals(
            setOf(
                CleanupGroup.APK_INSTALLED,
                CleanupGroup.DUPLICATE_SAFE,
                CleanupGroup.THUMBNAILS,
                CleanupGroup.TEMP_FILES,
                CleanupGroup.PARTIAL_DOWNLOADS,
            ),
            auto,
        )
    }

    private fun file(
        name: String,
        mime: String,
        ageDays: Long,
        path: String = "Download/",
        size: Long = 2L * mb,
    ) = IndexedFile(
        uri = "content://test/$name",
        name = name,
        sizeBytes = size,
        mimeType = mime,
        modifiedAtMillis = now - TimeUnit.DAYS.toMillis(ageDays),
        relativePath = path,
    )

    private companion object {
        const val APK = "application/vnd.android.package-archive"
        const val OCTET = "application/octet-stream"
    }
}
