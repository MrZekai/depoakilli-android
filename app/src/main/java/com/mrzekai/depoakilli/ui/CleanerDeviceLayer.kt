package com.mrzekai.depoakilli.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.viewModelScope
import com.mrzekai.depoakilli.data.DeviceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Layer of [CleanerViewModel] (T3.5): DeviceState state and actions.
 * The layers form one inheritance chain so the public API stays on
 * CleanerViewModel while each concern lives in its own file.
 */
abstract class CleanerDeviceLayer(
    application: Application,
    repository: DeviceRepository,
) : CleanerTrashLayer(application, repository) {
    fun refreshDeviceState(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && deviceRefreshJob?.isActive == true) return
        if (
            !force &&
            lastDeviceRefreshAt != 0L &&
            now - lastDeviceRefreshAt < DEVICE_REFRESH_INTERVAL_MILLIS
        ) {
            return
        }

        if (force) deviceRefreshJob?.cancel()

        deviceRefreshJob = viewModelScope.launch {
            runSuspendCatching {
                coroutineScope {
                    val storage = async(Dispatchers.Default) { repository.storageSnapshot() }
                    val memory = async(Dispatchers.Default) { repository.memorySnapshot() }
                    val ownCache = async(Dispatchers.IO) { repository.ownCacheSize() }
                    val allFiles = async(Dispatchers.Default) { repository.hasAllFilesAccess() }
                    val usage = async(Dispatchers.Default) { repository.hasUsageAccess() }
                    val whatsApp = async(Dispatchers.Default) { repository.hasWhatsAppAccess() }
                    val deviceInfo = async(Dispatchers.Default) { repository.deviceInfoSnapshot() }

                    DeviceRefreshSnapshot(
                        storage = storage.await(),
                        memory = memory.await(),
                        ownCacheBytes = ownCache.await(),
                        hasAllFilesAccess = allFiles.await(),
                        hasUsageAccess = usage.await(),
                        hasWhatsAppAccess = whatsApp.await(),
                        deviceInfo = deviceInfo.await(),
                    )
                }
            }.onSuccess { snapshot ->
                lastDeviceRefreshAt = SystemClock.elapsedRealtime()
                _state.update {
                    it.copy(
                        storage = snapshot.storage,
                        memory = snapshot.memory,
                        ownCacheBytes = snapshot.ownCacheBytes,
                        hasAllFilesAccess = snapshot.hasAllFilesAccess,
                        hasUsageAccess = snapshot.hasUsageAccess,
                        hasWhatsAppAccess = snapshot.hasWhatsAppAccess,
                        deviceInfo = snapshot.deviceInfo,
                    )
                }
            }
        }
    }

    fun refreshInstalledApps() {
        if (_state.value.loadingApps) return
        _state.update { it.copy(loadingApps = true) }
        installedAppsRefreshJob = viewModelScope.launch {
            runSuspendCatching { repository.installedAppsSnapshot() }
                .onSuccess { apps ->
                    val usageAccess = withContext(Dispatchers.Default) { repository.hasUsageAccess() }
                    _state.update {
                        it.copy(
                            installedApps = apps,
                            loadingApps = false,
                            hasUsageAccess = usageAccess,
                        )
                    }
                }
                .onFailure {
                    _state.update { it.copy(loadingApps = false) }
                }
        }
    }

}
