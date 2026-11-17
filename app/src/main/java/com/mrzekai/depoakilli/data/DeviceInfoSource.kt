package com.mrzekai.depoakilli.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.Environment
import android.os.StatFs
import com.mrzekai.depoakilli.model.DeviceInfoSnapshot
import com.mrzekai.depoakilli.model.MemorySnapshot
import com.mrzekai.depoakilli.model.StorageSnapshot
import java.io.File

/** Storage, memory and device facts for the dashboard (T3.5). */
internal class DeviceInfoSource(
    private val context: Context,
    private val storageRoots: () -> List<File>,
) {
    fun storageSnapshot(): StorageSnapshot {
        val snapshots = storageRoots().mapNotNull { root ->
            runCatching {
                val stats = StatFs(root.absolutePath)
                StorageSnapshot(
                    totalBytes = stats.totalBytes,
                    availableBytes = stats.availableBytes,
                )
            }.getOrNull()
        }

        if (snapshots.isNotEmpty()) {
            return StorageSnapshot(
                totalBytes = snapshots.sumOf(StorageSnapshot::totalBytes),
                availableBytes = snapshots.sumOf(StorageSnapshot::availableBytes),
            )
        }

        val fallback = StatFs(Environment.getDataDirectory().absolutePath)
        return StorageSnapshot(
            totalBytes = fallback.totalBytes,
            availableBytes = fallback.availableBytes,
        )
    }

    fun memorySnapshot(): MemorySnapshot {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        val processMemoryInfo = Debug.MemoryInfo()
        manager.getMemoryInfo(info)
        Debug.getMemoryInfo(processMemoryInfo)
        return MemorySnapshot(
            totalBytes = info.totalMem,
            availableBytes = info.availMem,
            appUsedBytes = processMemoryInfo.totalPss.toLong() * 1024L,
            lowMemory = info.lowMemory,
            lowMemoryThresholdBytes = info.threshold,
        )
    }

    @Suppress("DEPRECATION")
    fun deviceInfoSnapshot(): DeviceInfoSnapshot {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val batteryLevel = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val batteryScale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val batteryPercent = if (batteryLevel >= 0 && batteryScale > 0) {
            (batteryLevel * 100 / batteryScale).coerceIn(0, 100)
        } else {
            0
        }
        val batteryStatus = battery?.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN,
        ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val metrics = context.resources.displayMetrics
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0L),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        return DeviceInfoSnapshot(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            androidVersion = Build.VERSION.RELEASE.orEmpty(),
            sdkLevel = Build.VERSION.SDK_INT,
            cpuAbi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
            screenResolution = "${metrics.widthPixels} × ${metrics.heightPixels}",
            batteryPercent = batteryPercent,
            batteryTemperatureCelsius = (battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f,
            batteryCharging = batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
                batteryStatus == BatteryManager.BATTERY_STATUS_FULL,
            appVersion = packageInfo.versionName.orEmpty(),
        )
    }
}
