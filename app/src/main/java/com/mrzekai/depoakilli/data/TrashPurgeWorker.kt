package com.mrzekai.depoakilli.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Once a day, permanently deletes trash entries older than the retention
 * period. Only files already moved to the trash by the user are touched.
 * No foreground service and no constraints; opening the app also purges.
 */
class TrashPurgeWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { DeviceRepository(applicationContext).purgeExpiredTrash() }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "smart_cleaner_trash_purge"

        fun schedule(context: Context) {
            runCatching {
                val request = PeriodicWorkRequestBuilder<TrashPurgeWorker>(1, TimeUnit.DAYS).build()
                WorkManager.getInstance(context)
                    .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            }
        }
    }
}
