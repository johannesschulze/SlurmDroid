package org.slurmdroid.features.nnunet.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.slurmdroid.features.nnunet.data.NnUNetRepository
import org.slurmdroid.features.nnunet.domain.NnUNetDataset
import org.slurmdroid.features.nnunet.domain.NnUNetWorkflow
import javax.inject.Inject

data class NnUNetDetailUiState(
    val workflow: NnUNetWorkflow = NnUNetWorkflow(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class NnUNetDatasetDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: NnUNetRepository,
) : ViewModel() {

    val datasetName: String = checkNotNull(savedStateHandle["datasetName"])

    private val _uiState = MutableStateFlow(NnUNetDetailUiState())
    val uiState: StateFlow<NnUNetDetailUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh(pullToRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = if (pullToRefresh)
                _uiState.value.copy(isRefreshing = true, error = null)
            else
                NnUNetDetailUiState(isLoading = true)
            val existing = repository.datasets.value.find { it.name == datasetName }
            val dataset = when {
                existing != null && existing.resultsPath.isNotBlank() -> existing
                else -> {
                    val resultsDir = repository.resolvedResultsDir.value ?: ""
                    NnUNetDataset(
                        name = datasetName,
                        resultsPath = if (resultsDir.isNotBlank()) "$resultsDir/$datasetName" else "",
                    )
                }
            }
            try {
                val workflow = repository.loadDatasetWorkflow(dataset)
                _uiState.value = NnUNetDetailUiState(workflow = workflow, isLoading = false, isRefreshing = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = e.message ?: "Unknown error",
                )
            }
        }
    }
}
