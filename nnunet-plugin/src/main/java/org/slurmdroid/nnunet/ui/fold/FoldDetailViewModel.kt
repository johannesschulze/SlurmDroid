package org.slurmdroid.nnunet.ui.fold

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.slurmdroid.nnunet.NnUNetPlugin
import org.slurmdroid.nnunet.domain.EpochMetrics
import org.slurmdroid.nnunet.domain.FoldProgress

class FoldDetailViewModel(
    private val plugin: NnUNetPlugin,
    val datasetName: String,
    val configName: String,
    val foldId: Int,
) : ViewModel() {
    val fold = plugin.workflows
        .map { workflows ->
            workflows[datasetName]?.trainingConfigs
                ?.find { it.configName == configName }
                ?.folds
                ?.find { it.foldId == foldId }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            plugin.workflows.value[datasetName]?.trainingConfigs
                ?.find { it.configName == configName }
                ?.folds
                ?.find { it.foldId == foldId },
        )

    private val _epochMetrics = MutableStateFlow<List<EpochMetrics>?>(null)
    val epochMetrics: StateFlow<List<EpochMetrics>?> = _epochMetrics.asStateFlow()

    val lastPollTime = plugin.lastPollTime
    val lastPollError = plugin.lastPollError

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _epochMetrics.value = plugin.fetchFoldMetrics(datasetName, configName, foldId)
        }
    }

    fun refreshMetrics() {
        viewModelScope.launch(Dispatchers.IO) {
            _epochMetrics.value = plugin.fetchFoldMetrics(datasetName, configName, foldId)
        }
    }
}
