package com.mrzekai.depoakilli.data

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import com.mrzekai.depoakilli.model.AppCacheEntry
import com.mrzekai.depoakilli.model.AppCacheSnapshot
import com.mrzekai.depoakilli.model.InstalledAppEntry
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Usage-access based app cache and installed-app statistics (T3.5). */
internal class AppStatsSource(
    private val context: Context,
    private val ownCacheSize: () -> Long,
) {
    @Suppress("DEPRECATION")
    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    suspend fun appCacheSnapshot(): AppCacheSnapshot = withContext(Dispatchers.IO) {
        if (!hasUsageAccess()) {
            return@withContext AppCacheSnapshot(supported = true, accessGranted = false)
        }
        val packageManager = context.packageManager
        val applications = launcherApplications(packageManager)
        val storageStats = context.getSystemService(StorageStatsManager::class.java)
        val user = Process.myUserHandle()
        val totalReportedCache = runCatching {
            storageStats.queryStatsForUser(StorageManager.UUID_DEFAULT, user).cacheBytes
        }.getOrDefault(0L)
        val entries = applications.mapNotNull { application ->
            coroutineContext.ensureActive()
            runCatching {
                val volume = application.storageUuid ?: StorageManager.UUID_DEFAULT
                val cacheBytes = storageStats.queryStatsForPackage(
                    volume,
                    application.packageName,
                    user,
                ).cacheBytes.coerceAtLeast(0L)
                if (cacheBytes == 0L) return@runCatching null
                AppCacheEntry(
                    packageName = application.packageName,
                    label = application.loadLabel(packageManager).toString().ifBlank { application.packageName },
                    cacheBytes = cacheBytes,
                )
            }.getOrNull()
        }
        AppCacheSnapshot(
            supported = true,
            accessGranted = true,
            entries = entries.sortedByDescending(AppCacheEntry::cacheBytes),
            scannedAppCount = applications.size,
            reportedOtherAppsCacheBytes = (totalReportedCache - ownCacheSize()).coerceAtLeast(0L),
        )
    }

    suspend fun installedAppsSnapshot(): List<InstalledAppEntry> = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        val applications = launcherApplications(packageManager)
        val usageAccessGranted = hasUsageAccess()
        val usageMap = if (usageAccessGranted) {
            val now = System.currentTimeMillis()
            val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            manager.queryUsageStats(
                UsageStatsManager.INTERVAL_YEARLY,
                now - TimeUnit.DAYS.toMillis(365),
                now,
            ).associateBy { it.packageName }
        } else {
            emptyMap()
        }
        val storageStats = context.getSystemService(StorageStatsManager::class.java)
        val user = Process.myUserHandle()
        applications.map { application ->
            coroutineContext.ensureActive()
            val stats = if (usageAccessGranted) {
                runCatching {
                    storageStats.queryStatsForPackage(
                        application.storageUuid ?: StorageManager.UUID_DEFAULT,
                        application.packageName,
                        user,
                    )
                }.getOrNull()
            } else {
                null
            }
            InstalledAppEntry(
                packageName = application.packageName,
                label = application.loadLabel(packageManager).toString().ifBlank { application.packageName },
                appBytes = stats?.appBytes ?: 0L,
                dataBytes = stats?.dataBytes ?: 0L,
                cacheBytes = stats?.cacheBytes ?: 0L,
                lastUsedMillis = usageMap[application.packageName]?.lastTimeUsed ?: 0L,
                systemApp = (
                    application.flags and
                        (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)
                    ) != 0,
            )
        }.sortedByDescending(InstalledAppEntry::totalBytes)
    }

    private fun launcherApplications(packageManager: PackageManager): List<ApplicationInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }
        return resolveInfos
            .mapNotNull { it.activityInfo?.applicationInfo }
            .filterNot { it.packageName == context.packageName }
            .distinctBy(ApplicationInfo::packageName)
    }
}
