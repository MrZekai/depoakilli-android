package com.mrzekai.depoakilli.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrzekai.depoakilli.data.DeviceRepository
import com.mrzekai.depoakilli.model.CleanCategory
import com.mrzekai.depoakilli.model.MemorySnapshot
import com.mrzekai.depoakilli.model.ScanSummary
import com.mrzekai.depoakilli.model.StorageSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CleanerUiState(
    val storage: StorageSnapshot = StorageSnapshot(),
    val memory: MemorySnapshot = MemorySnapshot(),
    val summary: ScanSummary = ScanSummary(),
    val scanning: Boolean = false,
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
                        current.copy(scanning = false, message = "Tarama tamamlanamadı. Erişim izinlerini kontrol et.")
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
            _state.update { it.copy(message = "Temizlenecek en az bir öğe seç.") }
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
            _state.update { it.copy(message = "Silme işlemi iptal edildi.") }
            return
        }
        val selectedIds = _state.value.summary.selectedItems.mapTo(hashSetOf()) { it.id }
        _state.update { state ->
            state.copy(
                summary = state.summary.copy(items = state.summary.items.filterNot { it.id in selectedIds }),
                storage = repository.storageSnapshot(),
                memory = repository.memorySnapshot(),
                message = "Temizlik tamamlandı.",
            )
        }
        pendingDeletionBytes = 0L
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    fun systemCacheIntent(): Intent = repository.systemCacheIntent()
}
