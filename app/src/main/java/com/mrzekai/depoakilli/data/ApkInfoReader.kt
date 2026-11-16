package com.mrzekai.depoakilli.data

import android.content.pm.PackageManager

data class ApkInfo(val packageName: String, val versionCode: Long)

/** Reads the package name and version of an APK archive on disk. */
fun interface ApkInfoReader {
    fun read(path: String): ApkInfo?

    companion object {
        val NONE = ApkInfoReader { null }
    }
}

/** Installed version of a package, or null when it is not installed or not visible. */
fun interface InstalledVersionLookup {
    fun versionOf(packageName: String): Long?

    companion object {
        val NONE = InstalledVersionLookup { null }
    }
}

class AndroidApkInfoReader(private val packageManager: PackageManager) : ApkInfoReader {
    @Suppress("DEPRECATION")
    override fun read(path: String): ApkInfo? = runCatching {
        val info = packageManager.getPackageArchiveInfo(path, 0) ?: return@runCatching null
        ApkInfo(info.packageName ?: return@runCatching null, info.longVersionCode)
    }.getOrNull()
}

/**
 * Uses only the visibility the manifest already declares. A package that is not
 * visible is treated as "not installed", so its installer stays in Review.
 */
class AndroidInstalledVersionLookup(private val packageManager: PackageManager) : InstalledVersionLookup {
    @Suppress("DEPRECATION")
    override fun versionOf(packageName: String): Long? = try {
        packageManager.getPackageInfo(packageName, 0).longVersionCode
    } catch (notFound: PackageManager.NameNotFoundException) {
        null
    } catch (failure: RuntimeException) {
        null
    }
}
