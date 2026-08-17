package com.mrzekai.depoakilli.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.data.DeviceRepository
import com.mrzekai.depoakilli.model.AppCacheSnapshot
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.model.CleanCategory
import com.mrzekai.depoakilli.model.CleanableItem
import com.mrzekai.depoakilli.model.MemorySnapshot
import com.mrzekai.depoakilli.model.ScanFocus
import com.mrzekai.depoakilli.model.ScanSummary
import com.mrzekai.depoakilli.model.StorageSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class CleanerUiState(
    val storage: StorageSnapshot = StorageSnapshot(),
    val memory: MemorySnapshot = MemorySnapshot(),
    val ownCacheBytes: Long = 0L,
    val appCache: AppCacheSnapshot = AppCacheSnapshot(),
    val summary: ScanSummary = ScanSummary(),
    val scanning: Boolean = false,
    val scanningAppCaches: Boolean = false,
    val optimizingMemory: Boolean = false,
    val lastScanCompleted: Boolean = false,
    val scanFocus: ScanFocus = ScanFocus.SMART,
    val hasWhatsAppAccess: Boolean = false,
    val message: String? = null,
)

class CleanerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DeviceRepository(application)
    private val _state = MutableStateFlow(CleanerUiState())
    private var pendingDeletionItems: List<CleanableItem> = emptyList()
    private var appCacheRefreshJob: Job? = null

    val state: StateFlow<CleanerUiState> = _state.asStateFlow()

    init {
        refreshDeviceState()
        refreshAppCaches()
    }

    fun refreshDeviceState() {
        _state.update {
            it.copy(
                storage = repository.storageSnapshot(),
                memory = repository.memorySnapshot(),
                ownCacheBytes = repository.ownCacheSize(),
                hasWhatsAppAccess = repository.hasWhatsAppTreeAccess(),
            )
        }
    }

    fun refreshAppCaches() {
        if (appCacheRefreshJob?.isActive == true || _state.value.scanning) return
        _state.update { it.copy(scanningAppCaches = true) }
        appCacheRefreshJob = viewModelScope.launch {
            runCatching { repository.appCacheSnapshot() }
                .onSuccess { snapshot ->
                    _state.update {
                        it.copy(appCache = snapshot, scanningAppCaches = false)
                    }
                }
                .onFailure {
                    _state.update { it.copy(scanningAppCaches = false) }
                }
        }
    }

    fun scan(
        limitedAccess: Boolean,
        focus: ScanFocus = ScanFocus.SMART,
    ) {
        if (_state.value.scanning) return
        appCacheRefreshJob?.cancel()
        _state.update {
            it.copy(
                scanning = true,
                scanningAppCaches = focus == ScanFocus.SMART,
                scanFocus = focus,
                message = null,
            )
        }
        viewModelScope.launch {
            runCatching {
                val summary = repository.scan(limitedAccess, focus)
                val appCache = if (focus == ScanFocus.SMART) {
                    runCatching { repository.appCacheSnapshot() }
                        .getOrDefault(_state.value.appCache)
                } else {
                    _state.value.appCache
                }
                summary to appCache
            }
                .onSuccess { (summary, appCache) ->
                    _state.update {
                        it.copy(
                            summary = summary,
                            appCache = appCache,
                            scanning = false,
                            scanningAppCaches = false,
                            lastScanCompleted = true,
                            storage = repository.storageSnapshot(),
                            memory = repository.memorySnapshot(),
                            hasWhatsAppAccess = repository.hasWhatsAppTreeAccess(),
                        )
                    }
                }
                .onFailure {
                    _state.update { current ->
                        current.copy(
                            scanning = false,
                            scanningAppCaches = false,
                            message = getApplication<Application>().getString(R.string.message_scan_failed),
                        )
                    }
                }
        }
    }

    fun connectWhatsAppFolder(uri: Uri): Boolean {
        val accepted = repository.saveWhatsAppTree(uri)
        _state.update {
            it.copy(
                hasWhatsAppAccess = accepted && repository.hasWhatsAppTreeAccess(),
                message = if (accepted) null else {
                    getApplication<Application>().getString(R.string.message_whatsapp_folder_invalid)
                },
            )
        }
        return accepted
    }

    fun toggleItem(id: String) {
        _state.update { state ->
            state.copy(
                summary = state.summary.copy(
                    items = state.summary.items.map { item ->
                        if (item.id == id) item.copy(selected = !item.selected) else item
                    },
                ),
            )
        }
    }

    fun toggleCategory(category: CleanCategory) {
        _state.update { state ->
            val categoryItems = state.summary.items.filter { it.assessment.category == category }
            val select = categoryItems.any { !it.selected }
            state.copy(
                summary = state.summary.copy(
                    items = state.summary.items.map { item ->
                        if (item.assessment.category == category) item.copy(selected = select) else item
                    },
                ),
            )
        }
    }

    fun prepareCleanup(
        onPlanReady: (DeviceRepository.DeletePlan) -> Unit,
        onCleanupCompleted: () -> Unit,
    ) {
        val selected = _state.value.summary.selectedItems
        if (selected.isEmpty()) {
            _state.update {
                it.copy(message = getApplication<Application>().getString(R.string.message_select_item))
            }
            return
        }
        pendingDeletionItems = selected
        viewModelScope.launch {
            val cacheSelected = selected.any { it.uri == DeviceRepository.APP_CACHE_URI }
            if (cacheSelected) repository.clearOwnCache()
            val plan = repository.createDeleteRequest(selected)
            when (plan) {
                is DeviceRepository.DeletePlan.Completed -> {
                    val documentResult = repository.deleteDocumentItems(selected)
                    finishCleanup(selected, documentResult)
                    onCleanupCompleted()
                }
                DeviceRepository.DeletePlan.NoMediaFiles -> {
                    val documentResult = repository.deleteDocumentItems(selected)
                    finishCleanup(selected, documentResult)
                    onCleanupCompleted()
                }
                is DeviceRepository.DeletePlan.RequiresConsent -> onPlanReady(plan)
            }
        }
    }

    fun completeCleanup(approved: Boolean) {
        if (!approved) {
            pendingDeletionItems = emptyList()
            _state.update {
                it.copy(message = getApplication<Application>().getString(R.string.message_cleanup_cancelled))
            }
            return
        }
        val items = pendingDeletionItems
        viewModelScope.launch {
            val documentResult = repository.deleteDocumentItems(items)
            finishCleanup(items, documentResult)
        }
    }

    private fun finishCleanup(
        items: List<CleanableItem>,
        documentResult: DeviceRepository.DocumentDeleteResult = DeviceRepository.DocumentDeleteResult(
            attemptedIds = emptySet(),
            deletedIds = emptySet(),
        ),
    ) {
        val allIds = items.mapTo(hashSetOf()) { it.id }
        val nonDocumentIds = allIds - documentResult.attemptedIds
        val removedIds = nonDocumentIds + documentResult.deletedIds
        val failedDocumentCount = documentResult.attemptedIds.size - documentResult.deletedIds.size
        _state.update { state ->
            state.copy(
                summary = state.summary.copy(items = state.summary.items.filterNot { it.id in removedIds }),
                storage = repository.storageSnapshot(),
                memory = repository.memorySnapshot(),
                ownCacheBytes = repository.ownCacheSize(),
                message = getApplication<Application>().getString(
                    if (failedDocumentCount == 0) {
                        R.string.message_cleanup_complete
                    } else {
                        R.string.message_cleanup_partial
                    },
                ),
            )
        }
        pendingDeletionItems = emptyList()
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    fun optimizeMemory(releaseHeavyResources: () -> Unit) {
        if (_state.value.scanning || _state.value.optimizingMemory) return
        val before = repository.memorySnapshot()
        pendingDeletionItems = emptyList()
        _state.update {
            it.copy(
                summary = ScanSummary(),
                lastScanCompleted = false,
                optimizingMemory = true,
                message = null,
            )
        }
        runCatching(releaseHeavyResources)

        viewModelScope.launch {
            delay(MEMORY_MEASUREMENT_DELAY_MILLIS)
            val after = repository.memorySnapshot()
            val releasedAppMemory = (before.appUsedBytes - after.appUsedBytes).coerceAtLeast(0L)
            val message = if (releasedAppMemory > 0L) {
                getApplication<Application>().getString(
                    R.string.message_memory_optimized,
                    ByteFormatter.format(releasedAppMemory),
                    ByteFormatter.format(after.availableBytes),
                )
            } else {
                getApplication<Application>().getString(
                    R.string.message_memory_optimized_stable,
                    ByteFormatter.format(after.availableBytes),
                )
            }
            _state.update {
                it.copy(
                    memory = after,
                    optimizingMemory = false,
                    message = message,
                )
            }
        }
    }

    fun clearAppCache() {
        viewModelScope.launch {
            val clearedBytes = repository.clearOwnCache().coerceAtLeast(0L)
            val message = if (clearedBytes > 0L) {
                getApplication<Application>().getString(
                    R.string.message_cache_cleared,
                    ByteFormatter.format(clearedBytes),
                )
            } else {
                getApplication<Application>().getString(R.string.message_cache_already_empty)
            }
            _state.update {
                it.copy(
                    summary = it.summary.copy(
                        items = it.summary.items.filterNot { item ->
                            item.uri == DeviceRepository.APP_CACHE_URI
                        },
                    ),
                    ownCacheBytes = repository.ownCacheSize(),
                    storage = repository.storageSnapshot(),
                    message = message,
                )
            }
        }
    }

    private companion object {
        const val MEMORY_MEASUREMENT_DELAY_MILLIS = 700L
    }
}
