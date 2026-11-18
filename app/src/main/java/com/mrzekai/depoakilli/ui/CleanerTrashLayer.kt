package com.mrzekai.depoakilli.ui

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.mrzekai.depoakilli.data.DeviceRepository
import com.mrzekai.depoakilli.data.RestoreResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Layer of [CleanerViewModel] (T3.5): Trash state and actions.
 * The layers form one inheritance chain so the public API stays on
 * CleanerViewModel while each concern lives in its own file.
 */
abstract class CleanerTrashLayer(
    application: Application,
    repository: DeviceRepository,
) : CleanerViewModelCore(application, repository) {
    /** Deletes trash entries older than the retention period, then refreshes the trash list. */
    fun purgeExpiredTrash() {
        viewModelScope.launch {
            runSuspendCatching { repository.purgeExpiredTrash() }
            refreshTrash()
        }
    }

    fun refreshTrash() {
        viewModelScope.launch {
            val entries = runSuspendCatching { withContext(Dispatchers.IO) { repository.trashEntries() } }
                .getOrDefault(emptyList())
            _state.update { it.copy(trashEntries = entries) }
        }
    }

    /** Puts the files of the shown cleanup back where they were. */
    fun undoCleanup() {
        val result = _state.value.cleanupResult ?: return
        if (result.trashEntryIds.isEmpty()) return
        restoreTrash(result.trashEntryIds) { restored ->
            _state.update { state ->
                val current = state.cleanupResult ?: return@update state
                val remaining = current.trashEntryIds.filterNot { it in restored.restoredIds }
                state.copy(
                    cleanupResult = current.copy(
                        trashEntryIds = remaining,
                        trashedBytes = (current.trashedBytes - restored.restoredBytes).coerceAtLeast(0L),
                        restoredCount = current.restoredCount + restored.restoredIds.size,
                    ),
                )
            }
        }
    }

    fun restoreTrash(ids: Collection<String>, onDone: (RestoreResult) -> Unit = {}) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val result = runSuspendCatching { repository.restoreFromTrash(ids) }.getOrNull() ?: return@launch
            val entries = withContext(Dispatchers.IO) { repository.trashEntries() }
            _state.update { it.copy(trashEntries = entries) }
            onDone(result)
        }
    }

    /** Permanently deletes trash entries (all when [ids] is null) and reports the measured result. */
    fun emptyTrash(ids: Collection<String>? = null, onDone: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val before = ioAvailableBytes()
            val freed = runSuspendCatching { repository.emptyTrash(ids) }.getOrDefault(0L)
            val entries = withContext(Dispatchers.IO) { repository.trashEntries() }
            val storageAfter = ioStorageSnapshot()
            _state.update { state ->
                val current = state.cleanupResult
                state.copy(
                    trashEntries = entries,
                    storage = storageAfter,
                    cleanupResult = current?.let { result ->
                        val gone = result.trashEntryIds.filterNot { id -> entries.any { it.id == id } }
                        if (gone.isEmpty()) {
                            result
                        } else {
                            result.copy(
                                trashEntryIds = result.trashEntryIds - gone.toSet(),
                                trashedBytes = (result.trashedBytes - freed).coerceAtLeast(0L),
                                deletedBytes = result.deletedBytes + freed,
                                afterAvailableBytes = storageAfter.availableBytes,
                                beforeAvailableBytes = if (result.beforeAvailableBytes > 0L) result.beforeAvailableBytes else before,
                            )
                        }
                    },
                )
            }
            onDone(freed)
        }
    }

    fun dismissTrashFallback() {
        _state.update { it.copy(trashFallback = null) }
    }

    internal fun offerTrashFallback(source: TrashFallback.Source, ids: Set<String>, folderMissing: Boolean) {
        if (ids.isEmpty()) return
        _state.update { it.copy(trashFallback = TrashFallback(source, ids, folderMissing)) }
    }

}
