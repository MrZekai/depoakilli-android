package com.mrzekai.depoakilli.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoragePathRulesTest {
    @Test
    fun `protected Android private directories are never candidates`() {
        assertTrue(StoragePathRules.isProtectedAppPrivatePath("Android/data/com.example.app/cache"))
        assertTrue(StoragePathRules.isProtectedAppPrivatePath("Android/obb/com.example.game/main.obb"))
        assertTrue(StoragePathRules.isProtectedAppPrivatePath("Android/data"))
        assertTrue(StoragePathRules.isProtectedAppPrivatePath("ANDROID/DATA/x"))
        assertFalse(StoragePathRules.isProtectedAppPrivatePath("Android/media/com.whatsapp/WhatsApp/Media"))
        assertFalse(StoragePathRules.isProtectedAppPrivatePath("Download/archive.zip"))
    }

    @Test
    fun `hidden folder marker files are recognised in any case`() {
        assertTrue(StoragePathRules.isSystemMarkerFile(".nomedia"))
        assertTrue(StoragePathRules.isSystemMarkerFile(".NOMEDIA"))
        assertTrue(StoragePathRules.isSystemMarkerFile(".hidden"))
        assertTrue(StoragePathRules.isSystemMarkerFile(".database_uuid"))
        assertTrue(StoragePathRules.isSystemMarkerFile(".private-notes"))
        assertFalse(StoragePathRules.isSystemMarkerFile("nomedia.jpg"))
        assertTrue(StoragePathRules.isSystemMarkerFile(".nomedia.jpg"))
        assertFalse(StoragePathRules.isSystemMarkerFile("IMG_0001.jpg"))
    }

    @Test fun `WhatsApp backups and encrypted databases are protected`() {
        assertTrue(StoragePathRules.isProtectedUserDataPath("Android/media/com.whatsapp/WhatsApp/Databases", "msgstore.db.crypt14"))
        assertTrue(StoragePathRules.isProtectedUserDataPath("WhatsApp/Backups", "backup.zip"))
        assertTrue(StoragePathRules.isProtectedUserDataPath("Download", "anything.crypt15"))
        assertTrue(StoragePathRules.isProtectedUserDataPath("Download", "msgstore.db"))
        assertFalse(StoragePathRules.isProtectedUserDataPath("Pictures/Backups", "photo.jpg"))
    }

    @Test fun `only recognized hidden media folders are allowed`() {
        assertTrue(StoragePathRules.isHiddenDirectoryAllowed(".thumbnails"))
        assertTrue(StoragePathRules.isHiddenDirectoryAllowed(".STATUSES"))
        assertFalse(StoragePathRules.isHiddenDirectoryAllowed(".vault"))
    }
}
