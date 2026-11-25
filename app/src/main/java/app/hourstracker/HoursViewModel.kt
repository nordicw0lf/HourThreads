package app.hourstracker

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal sealed interface BackupStatus {
    data object Working : BackupStatus
    data class Preview(val backup: HourThreadBackup) : BackupStatus
    data object Exported : BackupStatus
    data object Restored : BackupStatus
    data class Error(@param:StringRes @get:StringRes val message: Int) : BackupStatus
}

internal class HoursViewModel(
    private val repository: HoursRepository,
    private val backupFiles: BackupFiles,
) : ViewModel() {
    val state = repository.state.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _saving = MutableStateFlow(false)
    private val _saveFailed = MutableStateFlow(false)
    private val _backupStatus = MutableStateFlow<BackupStatus?>(null)
    private val _deletedShift = MutableStateFlow<Shift?>(null)
    val saving = _saving.asStateFlow()
    val saveFailed = _saveFailed.asStateFlow()
    val backupStatus = _backupStatus.asStateFlow()
    val deletedShift = _deletedShift.asStateFlow()

    fun save(onSaved: () -> Unit = {}, action: suspend HoursRepository.() -> Unit) {
        if (_saving.value) return
        _saving.value = true
        _saveFailed.value = false
        viewModelScope.launch {
            try {
                repository.action()
                onSaved()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                android.util.Log.e("HoursTracker", "Could not save changes", failure)
                _saveFailed.value = true
            } finally {
                _saving.value = false
            }
        }
    }

    fun dismissError() {
        _saveFailed.value = false
    }

    fun deleteShift(id: Long) = save {
        deleteCompletedShift(id)?.let { _deletedShift.value = it }
    }

    fun undoShiftDeletion(shift: Shift) {
        if (_deletedShift.value != shift) return
        save(onSaved = { dismissShiftUndo(shift) }) { restoreDeletedShift(shift) }
    }

    fun dismissShiftUndo(shift: Shift) {
        if (_deletedShift.value == shift) _deletedShift.value = null
    }

    fun exportBackup(uri: Uri, languageTags: String) = runBackup(R.string.backup_export_error) {
        backupFiles.write(uri, repository.makeBackup(languageTags))
        BackupStatus.Exported
    }

    fun previewBackup(uri: Uri) = runBackup(R.string.backup_read_error) {
        BackupStatus.Preview(backupFiles.read(uri))
    }

    fun restoreBackup() {
        val preview = _backupStatus.value as? BackupStatus.Preview ?: return
        runBackup(R.string.backup_restore_error) {
            repository.restoreBackup(preview.backup)
            _deletedShift.value = null
            BackupStatus.Restored
        }
    }

    fun acknowledgeRestoredLanguage(tags: String) {
        viewModelScope.launch {
            try {
                repository.acknowledgeRestoredLanguage(tags)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _saveFailed.value = true
            }
        }
    }

    fun dismissBackup() {
        if (_backupStatus.value != BackupStatus.Working) _backupStatus.value = null
    }

    private fun runBackup(@StringRes errorMessage: Int, action: suspend () -> BackupStatus) {
        if (_saving.value) return
        _saving.value = true
        _backupStatus.value = BackupStatus.Working
        viewModelScope.launch {
            try {
                _backupStatus.value = action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: InvalidBackup) {
                _backupStatus.value = BackupStatus.Error(R.string.backup_invalid)
            } catch (_: UnsupportedBackupVersion) {
                _backupStatus.value = BackupStatus.Error(R.string.backup_version)
            } catch (_: BackupTooLarge) {
                _backupStatus.value = BackupStatus.Error(R.string.backup_size)
            } catch (_: Exception) {
                _backupStatus.value = BackupStatus.Error(errorMessage)
            } finally {
                _saving.value = false
            }
        }
    }
}
