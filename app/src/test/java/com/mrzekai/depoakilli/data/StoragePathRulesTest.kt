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
}
