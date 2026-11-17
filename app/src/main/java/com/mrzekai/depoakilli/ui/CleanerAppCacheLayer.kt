package com.mrzekai.depoakilli.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.viewModelScope
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.data.DeviceRepository
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Layer of [CleanerViewModel] (T3.5): AppCache state and actions.
 * The layers form one inheritance chain so the public API stays on
 * CleanerViewModel while each concern lives in its own file.
 */
abstract class CleanerAppCacheLayer(
    application: Application,
    repository: DeviceRepository,
) : CleanerDeviceLayer(application, repository) {
    fun refreshAppCaches(force: Boolean = false) {
        if (appCacheRefreshJob?.isActive == true || _state.value.scanning) return
        val now = SystemClock.elapsedRealtime()
        if (!force && lastAppCacheRefreshAt != 0L && now - lastAppCacheRefreshAt < APP_CACHE_REFRESH_INTERVAL_MILLIS) {
            return
        }
        _state.update { it.copy(scanningAppCaches = true) }
        appCacheRefreshJob = viewModelScope.launch {
            runCatching { repository.appCacheSnapshot() }
                .onSuccess { snapshot ->
                    lastAppCacheRefreshAt = SystemClock.elapsedRealtime()

                    val storageChangeReport =
                        if (captureAppCacheForStorageChange) {
                            captureAppCacheForStorageChange = false
                            storageChangeStore.updateCurrentAppCaches(snapshot)
                        } else {
                            null
                        }

                    _state.update { current ->
                        current.copy(
                            appCache = snapshot,
                            scanningAppCaches = false,
                            hasUsageAccess = repository.hasUsageAccess(),
                            storageChange = storageChangeReport ?: current.storageChange,
                        )
                    }
                }
                .onFailure {
                    captureAppCacheForStorageChange = false
                    _state.update { it.copy(scanningAppCaches = false) }
                }
        }
    }

    fun beginDeepCacheCleanupMeasurement() {
        val current = _state.value
        pendingDeepCacheBeforeBytes = current.appCache.totalCacheBytes.coerceAtLeast(0L)
        pendingDeepCacheStorageBeforeBytes = current.storage.availableBytes.coerceAtLeast(0L)
    }

    fun onDeepCacheCleanupResult(approved: Boolean) {
        val beforeCacheBytes = pendingDeepCacheBeforeBytes
        val beforeAvailableBytes = pendingDeepCacheStorageBeforeBytes
        pendingDeepCacheBeforeBytes = 0L
        pendingDeepCacheStorageBeforeBytes = 0L

        if (!approved) {
            showMessage(R.string.message_cache_system_unavailable)
            refreshDeviceState(force = true)
            refreshAppCaches(force = true)
            return
        }

        appCacheRefreshJob?.cancel()
        _state.update { it.copy(scanningAppCaches = true, message = null) }

        viewModelScope.launch {
            val afterCache = runCatching { repository.appCacheSnapshot() }.getOrNull()
            val storageAfter = runCatching { ioStorageSnapshot() }
                .getOrElse { _state.value.storage }

            val measuredCacheReduction =
                if (beforeCacheBytes > 0L && afterCache != null) {
                    (beforeCacheBytes - afterCache.totalCacheBytes).coerceAtLeast(0L)
                } else {
                    0L
                }

            if (afterCache != null) {
                lastAppCacheRefreshAt = SystemClock.elapsedRealtime()
            }

            _state.update { current ->
                current.copy(
                    appCache = afterCache ?: current.appCache,
                    storage = storageAfter,
                    scanningAppCaches = false,
                    cleanupResult = if (measuredCacheReduction > 0L) CleanupResult(
                        deletedBytes = measuredCacheReduction,
                        deletedCount = 0,
                        failedCount = 0,
                        beforeAvailableBytes = beforeAvailableBytes,
                        afterAvailableBytes = storageAfter.availableBytes,
                        kind = CleanupResultKind.SYSTEM_CACHE,
                        operationSucceeded = true,
                    ) else null,
                    message = if (measuredCacheReduction == 0L) {
                        getApplication<Application>().getString(R.string.message_cache_system_unavailable)
                    } else null,
                )
            }
        }
    }

    fun beginIndividualAppCacheMeasurement(packageName: String) {
        val current = _state.value
        val app = current.appCache.entries.firstOrNull { it.packageName == packageName }

        pendingIndividualCachePackage = packageName
        pendingIndividualCacheLabel = app?.label ?: packageName
        pendingIndividualCacheBeforeBytes = app?.cacheBytes?.coerceAtLeast(0L) ?: 0L
        pendingIndividualCacheStorageBeforeBytes = current.storage.availableBytes.coerceAtLeast(0L)

        _state.update { it.copy(cleanupResult = null, message = null) }
    }

    fun cancelIndividualAppCacheMeasurement() {
        pendingIndividualCachePackage = null
        pendingIndividualCacheLabel = null
        pendingIndividualCacheBeforeBytes = 0L
        pendingIndividualCacheStorageBeforeBytes = 0L
    }

    fun onIndividualAppCacheSettingsReturned() {
        val packageName = pendingIndividualCachePackage
        val appLabel = pendingIndividualCacheLabel
        val beforeCacheBytes = pendingIndividualCacheBeforeBytes
        val beforeAvailableBytes = pendingIndividualCacheStorageBeforeBytes

        cancelIndividualAppCacheMeasurement()

        if (packageName.isNullOrBlank()) {
            refreshAppCaches(force = true)
            return
        }

        appCacheRefreshJob?.cancel()
        _state.update { it.copy(scanningAppCaches = true) }

        viewModelScope.launch {
            val afterCache = runCatching {
                repository.appCacheSnapshot()
            }.getOrNull()

            val storageAfter = runCatching {
                ioStorageSnapshot()
            }.getOrElse {
                _state.value.storage
            }

            val afterCacheBytes = afterCache
                ?.entries
                ?.firstOrNull { it.packageName == packageName }
                ?.cacheBytes
                ?.coerceAtLeast(0L)
                ?: 0L

            val measuredReduction =
                if (beforeCacheBytes > 0L && afterCache != null) {
                    (beforeCacheBytes - afterCacheBytes).coerceAtLeast(0L)
                } else {
                    0L
                }

            if (afterCache != null) {
                lastAppCacheRefreshAt = SystemClock.elapsedRealtime()
            }

            _state.update { current ->
                current.copy(
                    appCache = afterCache ?: current.appCache,
                    storage = storageAfter,
                    scanningAppCaches = false,
                    cleanupResult = if (measuredReduction > 0L) {
                        CleanupResult(
                            deletedBytes = measuredReduction,
                            deletedCount = 0,
                            failedCount = 0,
                            beforeAvailableBytes = beforeAvailableBytes,
                            afterAvailableBytes = storageAfter.availableBytes,
                            kind = CleanupResultKind.APP_CACHE,
                            subjectLabel = appLabel,
                        )
                    } else {
                        null
                    },
                    message = if (measuredReduction > 0L) {
                        null
                    } else {
                        getApplication<Application>().getString(
                            R.string.message_individual_cache_no_change,
                        )
                    },
                )
            }
        }
    }

    fun clearOwnAppCache() {
        viewModelScope.launch {
            val beforeAvailableBytes = ioAvailableBytes()
            val clearedBytes = repository.clearOwnCache().coerceAtLeast(0L)
            val storageAfter = ioStorageSnapshot()
            val ownCacheAfter = ioOwnCacheBytes()

            _state.update { current ->
                if (clearedBytes > 0L) {
                    current.copy(
                        ownCacheBytes = ownCacheAfter,
                        storage = storageAfter,
                        cleanupResult = CleanupResult(
                            deletedBytes = clearedBytes,
                            deletedCount = 0,
                            failedCount = 0,
                            beforeAvailableBytes = beforeAvailableBytes,
                            afterAvailableBytes = storageAfter.availableBytes,
                            kind = CleanupResultKind.APP_CACHE,
                            subjectLabel = getApplication<Application>().getString(R.string.app_name),
                        ),
                        message = null,
                    )
                } else {
                    current.copy(
                        ownCacheBytes = ownCacheAfter,
                        storage = storageAfter,
                        cleanupResult = null,
                        message = getApplication<Application>().getString(
                            R.string.message_cache_already_empty,
                        ),
                    )
                }
            }
        }
    }

}
