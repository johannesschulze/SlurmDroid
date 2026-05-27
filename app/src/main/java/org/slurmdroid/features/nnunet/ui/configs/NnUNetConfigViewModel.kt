package org.slurmdroid.features.nnunet.ui.configs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.slurmdroid.features.nnunet.data.NnUNetRepository
import javax.inject.Inject

@HiltViewModel
class NnUNetConfigViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: NnUNetRepository,
) : ViewModel() {
    val datasetName: String = checkNotNull(savedStateHandle["datasetName"])

    private val _configs = MutableStateFlow<List<String>>(emptyList())
    val configs: StateFlow<List<String>> = _configs.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            var datasets = repository.datasets.value
            if (datasets.isEmpty()) {
                repository.poll()
                datasets = repository.datasets.value
            }
            val dataset = datasets.find { it.name == datasetName }
            _configs.value = if (dataset != null) repository.listConfigs(dataset) else emptyList()
            _isLoading.value = false
        }
    }
}
