package com.mrzekai.depoakilli.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrzekai.depoakilli.R
import com.mrzekai.depoakilli.data.DeviceRepository
import com.mrzekai.depoakilli.model.ByteFormatter
import com.mrzekai.depoakilli.model.CleanCategory
import com.mrzekai.depoakilli.model.MemorySnapshot
import com.mrzekai.depoakilli.model.ScanSummary
import com.mrzekai.depoakilli.model.StorageSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class CleanerUiState(
    val storage: StorageSnapshot = StorageSnapshot(),
    val memory: MemorySnapshot = MemorySnapshot(),
    val ownCacheBytes: Long = 0L,
    val summary: ScanSummary = ScanSummary(),
    val scanning: Boolean = false,
    val optimizingMemory: Boolean = false,
    val lastScanCompleted: Boolean = false,
    val message: String? = null,
)

class CleanerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DeviceRepository(application)
    private val _state = MutableStateFlow(CleanerUiState())
    private var pendingDeletionBytes = 0L

    val state: StateFlow<CleanerUiState> = _state.asStateFlow()

    init {
        refreshDeviceState()
    }

    fun refreshDeviceState() {
        _state.update {
            it.copy(
                storage = repository.storageSnapshot(),
                memory = repository.memorySnapshot(),
                ownCacheBytes = repository.ownCacheSize(),
            )
        }
    }

    fun scan(limitedAccess: Boolean) {
        if (_state.value.scanning) return
        _state.update { it.copy(scanning = true, message = null) }
        viewModelScope.launch {
            runCatching { repository.scan(limitedAccess) }
                .onSuccess { summary ->
                    _state.update {
                        it.copy(
                            summary = summary,
                            scanning = false,
                            lastScanCompleted = true,
                            storage = repository.storageSnapshot(),
                            memory = repository.memorySnapshot(),
                        )
                    }
                }
                .onFailure {
                    _state.update { current ->
                        current.copy(
                            scanning = false,
                            message = getApplication<Application>().getString(R.string.message_scan_failed),
                        )
                    }
                }
        }
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
        pendingDeletionBytes = selected.sumOf { it.sizeBytes }
        viewModelScope.launch {
            val cacheSelected = selected.any { it.uri == DeviceRepository.APP_CACHE_URI }
            if (cacheSelected) repository.clearOwnCache()
            val plan = repository.createDeleteRequest(selected)
            when (plan) {
                is DeviceRepository.DeletePlan.Completed -> {
                    completeCleanup(true)
                    onCleanupCompleted()
                }
                DeviceRepository.DeletePlan.NoMediaFiles -> {
                    completeCleanup(true)
                    onCleanupCompleted()
                }
                is DeviceRepository.DeletePlan.RequiresConsent -> onPlanReady(plan)
            }
        }
    }

    fun completeCleanup(approved: Boolean) {
        if (!approved) {
            pendingDeletionBytes = 0L
            _state.update {
                it.copy(message = getApplication<Application>().getString(R.string.message_cleanup_cancelled))
            }
            return
        }
        val selectedIds = _state.value.summary.selectedItems.mapTo(hashSetOf()) { it.id }
        _state.update { state ->
            state.copy(
                summary = state.summary.copy(items = state.summary.items.filterNot { it.id in selectedIds }),
                storage = repository.storageSnapshot(),
                memory = repository.memorySnapshot(),
                ownCacheBytes = repository.ownCacheSize(),
                message = getApplication<Application>().getString(R.string.message_cleanup_complete),
            )
        }
        pendingDeletionBytes = 0L
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    fun optimizeMemory(releaseHeavyResources: () -> Unit) {
        if (_state.value.scanning || _state.value.optimizingMemory) return
        val before = repository.memorySnapshot()
        pendingDeletionBytes = 0L
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
