package com.mrzekai.depoakilli.ui

import android.content.Context
import com.mrzekai.depoakilli.model.AppCacheSnapshot
import com.mrzekai.depoakilli.model.StorageFileType
import com.mrzekai.depoakilli.model.StorageSnapshot
import com.mrzekai.depoakilli.model.StorageTypeStat
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

internal data class StorageChangeAppCache(
    val packageName: String,
    val label: String,
    val bytes: Long,
)

internal data class StorageChangePoint(
    val analyzedAtMillis: Long = 0L,
    val usedBytes: Long = 0L,
    val availableBytes: Long = 0L,
    val storageTypeBytes: Map<StorageFileType, Long> = emptyMap(),
    val appCaches: List<StorageChangeAppCache> = emptyList(),
    val appCacheMeasuredAtMillis: Long = 0L,
)

internal data class StorageTypeChange(
    val type: StorageFileType,
    val currentBytes: Long,
    val deltaBytes: Long,
)

internal data class AppCacheChange(
    val packageName: String,
    val label: String,
    val currentBytes: Long,
    val deltaBytes: Long,
)

internal data class StorageChangeReport(
    val previous: StorageChangePoint? = null,
    val current: StorageChangePoint? = null,
) {
    val hasBaseline: Boolean
        get() = current?.analyzedAtMillis?.let { it > 0L } == true

    val hasComparison: Boolean
        get() = previous != null &&
            current != null &&
            previous.analyzedAtMillis > 0L &&
            current.analyzedAtMillis > previous.analyzedAtMillis

    val usedDeltaBytes: Long
        get() = if (hasComparison) {
            current!!.usedBytes - previous!!.usedBytes
        } else {
            0L
        }

    val storageTypeChanges: List<StorageTypeChange>
        get() {
            if (!hasComparison) return emptyList()
            val before = previous!!
            val after = current!!

            return StorageFileType.entries
                .map { type ->
                    val previousBytes = before.storageTypeBytes[type] ?: 0L
                    val currentBytes = after.storageTypeBytes[type] ?: 0L
                    StorageTypeChange(
                        type = type,
                        currentBytes = currentBytes,
                        deltaBytes = currentBytes - previousBytes,
                    )
                }
                .filter { it.deltaBytes != 0L }
                .sortedByDescending { absForSort(it.deltaBytes) }
        }

    val appCacheChanges: List<AppCacheChange>
        get() {
            if (!hasComparison) return emptyList()

            val beforeByPackage = previous!!.appCaches.associateBy(StorageChangeAppCache::packageName)

            return current!!.appCaches
                .mapNotNull { currentApp ->
                    val previousApp = beforeByPackage[currentApp.packageName] ?: return@mapNotNull null
                    val delta = currentApp.bytes - previousApp.bytes
                    if (delta == 0L) return@mapNotNull null

                    AppCacheChange(
                        packageName = currentApp.packageName,
                        label = currentApp.label,
                        currentBytes = currentApp.bytes,
                        deltaBytes = delta,
                    )
                }
                .sortedByDescending { absForSort(it.deltaBytes) }
        }

    private fun absForSort(value: Long): Long =
        if (value == Long.MIN_VALUE) Long.MAX_VALUE else abs(value)
}

internal class StorageChangeStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(): StorageChangeReport = StorageChangeReport(
        previous = loadPoint(PREVIOUS_PREFIX),
        current = loadPoint(CURRENT_PREFIX),
    )

    fun recordFileSnapshot(
        storage: StorageSnapshot,
        storageTypes: List<StorageTypeStat>,
        analyzedAtMillis: Long,
    ): StorageChangeReport {
        if (analyzedAtMillis <= 0L || storage.totalBytes <= 0L) return load()

        val existingCurrent = loadPoint(CURRENT_PREFIX)

        if (
            existingCurrent != null &&
            existingCurrent.analyzedAtMillis > 0L &&
            analyzedAtMillis > existingCurrent.analyzedAtMillis
        ) {
            savePoint(PREVIOUS_PREFIX, existingCurrent)
        }

        val appCaches =
            if (existingCurrent?.analyzedAtMillis == analyzedAtMillis) existingCurrent.appCaches
            else emptyList()

        val appCacheMeasuredAtMillis =
            if (existingCurrent?.analyzedAtMillis == analyzedAtMillis) {
                existingCurrent.appCacheMeasuredAtMillis
            } else {
                0L
            }

        val point = StorageChangePoint(
            analyzedAtMillis = analyzedAtMillis,
            usedBytes = storage.usedBytes.coerceAtLeast(0L),
            availableBytes = storage.availableBytes.coerceAtLeast(0L),
            storageTypeBytes = storageTypes.associate { stat ->
                stat.type to stat.totalBytes.coerceAtLeast(0L)
            },
            appCaches = appCaches,
            appCacheMeasuredAtMillis = appCacheMeasuredAtMillis,
        )

        savePoint(CURRENT_PREFIX, point)
        return load()
    }

    fun updateCurrentAppCaches(
        snapshot: AppCacheSnapshot,
        measuredAtMillis: Long = System.currentTimeMillis(),
    ): StorageChangeReport {
        val current = loadPoint(CURRENT_PREFIX) ?: return load()

        val apps = if (snapshot.accessGranted) {
            snapshot.entries
                .asSequence()
                .filter { it.cacheBytes > 0L }
                .sortedByDescending { it.cacheBytes }
                .take(MAX_TRACKED_APP_CACHES)
                .map { entry ->
                    StorageChangeAppCache(
                        packageName = entry.packageName,
                        label = entry.label,
                        bytes = entry.cacheBytes.coerceAtLeast(0L),
                    )
                }
                .toList()
        } else {
            emptyList()
        }

        savePoint(
            CURRENT_PREFIX,
            current.copy(
                appCaches = apps,
                appCacheMeasuredAtMillis = measuredAtMillis.coerceAtLeast(0L),
            ),
        )

        return load()
    }

    private fun loadPoint(prefix: String): StorageChangePoint? {
        val analyzedAtMillis = preferences.getLong(key(prefix, KEY_ANALYZED_AT), 0L)
        if (analyzedAtMillis <= 0L) return null

        val typeBytes = StorageFileType.entries
            .associateWith { type ->
                preferences.getLong(
                    key(prefix, KEY_TYPE_PREFIX + type.name),
                    0L,
                ).coerceAtLeast(0L)
            }
            .filterValues { it > 0L }

        return StorageChangePoint(
            analyzedAtMillis = analyzedAtMillis,
            usedBytes = preferences.getLong(key(prefix, KEY_USED_BYTES), 0L).coerceAtLeast(0L),
            availableBytes = preferences.getLong(
                key(prefix, KEY_AVAILABLE_BYTES),
                0L,
            ).coerceAtLeast(0L),
            storageTypeBytes = typeBytes,
            appCaches = decodeApps(
                preferences.getString(key(prefix, KEY_APP_CACHES), null),
            ),
            appCacheMeasuredAtMillis = preferences.getLong(
                key(prefix, KEY_APP_CACHE_MEASURED_AT),
                0L,
            ).coerceAtLeast(0L),
        )
    }

    private fun savePoint(prefix: String, point: StorageChangePoint) {
        preferences.edit().apply {
            putLong(key(prefix, KEY_ANALYZED_AT), point.analyzedAtMillis.coerceAtLeast(0L))
            putLong(key(prefix, KEY_USED_BYTES), point.usedBytes.coerceAtLeast(0L))
            putLong(key(prefix, KEY_AVAILABLE_BYTES), point.availableBytes.coerceAtLeast(0L))
            putLong(
                key(prefix, KEY_APP_CACHE_MEASURED_AT),
                point.appCacheMeasuredAtMillis.coerceAtLeast(0L),
            )
            putString(key(prefix, KEY_APP_CACHES), encodeApps(point.appCaches))

            StorageFileType.entries.forEach { type ->
                putLong(
                    key(prefix, KEY_TYPE_PREFIX + type.name),
                    point.storageTypeBytes[type]?.coerceAtLeast(0L) ?: 0L,
                )
            }
        }.apply()
    }

    private fun encodeApps(apps: List<StorageChangeAppCache>): String {
        val array = JSONArray()
        apps.take(MAX_TRACKED_APP_CACHES).forEach { app ->
            array.put(
                JSONObject()
                    .put(JSON_PACKAGE, app.packageName)
                    .put(JSON_LABEL, app.label)
                    .put(JSON_BYTES, app.bytes.coerceAtLeast(0L)),
            )
        }
        return array.toString()
    }

    private fun decodeApps(raw: String?): List<StorageChangeAppCache> {
        if (raw.isNullOrBlank()) return emptyList()

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val packageName = item.optString(JSON_PACKAGE).trim()
                    if (packageName.isBlank()) continue

                    add(
                        StorageChangeAppCache(
                            packageName = packageName,
                            label = item.optString(JSON_LABEL, packageName),
                            bytes = item.optLong(JSON_BYTES, 0L).coerceAtLeast(0L),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun key(prefix: String, suffix: String): String = "${prefix}_$suffix"

    private companion object {
        const val PREFS_NAME = "storage_change_v1"
        const val CURRENT_PREFIX = "current"
        const val PREVIOUS_PREFIX = "previous"

        const val KEY_ANALYZED_AT = "analyzed_at"
        const val KEY_USED_BYTES = "used_bytes"
        const val KEY_AVAILABLE_BYTES = "available_bytes"
        const val KEY_TYPE_PREFIX = "type_"
        const val KEY_APP_CACHES = "app_caches_json"
        const val KEY_APP_CACHE_MEASURED_AT = "app_cache_measured_at"

        const val JSON_PACKAGE = "package"
        const val JSON_LABEL = "label"
        const val JSON_BYTES = "bytes"

        const val MAX_TRACKED_APP_CACHES = 50
    }
}
