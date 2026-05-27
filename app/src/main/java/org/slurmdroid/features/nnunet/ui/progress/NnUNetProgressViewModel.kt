package org.slurmdroid.features.nnunet.ui.progress

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.slurmdroid.features.nnunet.data.NnUNetRepository
import org.slurmdroid.features.nnunet.domain.FoldProgress
import javax.inject.Inject

data class NnUNetProgressUiState(
    val isLoading: Boolean = true,
    val folds: List<FoldProgress> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class NnUNetProgressViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: NnUNetRepository,
) : ViewModel() {
    val datasetName: String = checkNotNull(savedStateHandle["datasetName"])
    val configName: String = checkNotNull(savedStateHandle["configName"])

    private val _uiState = MutableStateFlow(NnUNetProgressUiState())
    val uiState: StateFlow<NnUNetProgressUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun refresh() = load()

    private fun load() {
        viewModelScope.launch {
            _uiState.value = NnUNetProgressUiState(isLoading = true)

            var datasets = repository.datasets.value
            if (datasets.isEmpty()) {
                repository.poll()
                datasets = repository.datasets.value
            }

            val dataset = datasets.find { it.name == datasetName }
            if (dataset == null) {
                _uiState.value = NnUNetProgressUiState(
                    isLoading = false,
                    error = "Dataset \"$datasetName\" not found.\nMake sure the server is reachable.",
                )
                return@launch
            }

            val folds = repository.loadProgress(dataset, configName)
            _uiState.value = NnUNetProgressUiState(
                isLoading = false,
                folds = folds,
                error = if (folds.isEmpty()) "No training logs found for this configuration." else null,
            )
        }
    }
}
