package org.slurmdroid.features.slurm.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.slurmdroid.core.Result
import org.slurmdroid.features.slurm.data.SlurmRepository
import javax.inject.Inject

@HiltViewModel
class LogViewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SlurmRepository,
) : ViewModel() {

    val fileName: String = checkNotNull(savedStateHandle["logFile"])

    sealed class UiState {
        object Loading : UiState()
        data class Success(val content: String) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { load() }

    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            _uiState.value = when (val r = repository.readLogFile(fileName)) {
                is Result.Success -> UiState.Success(r.data)
                is Result.AuthError -> UiState.Error("Authentication error")
                is Result.ConnectionError -> UiState.Error("Connection lost: ${r.message}")
                else -> UiState.Error("Failed to read log file")
            }
        }
    }
}
