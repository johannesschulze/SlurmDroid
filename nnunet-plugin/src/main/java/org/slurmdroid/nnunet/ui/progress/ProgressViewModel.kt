package org.slurmdroid.nnunet.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.slurmdroid.nnunet.NnUNetPlugin
import org.slurmdroid.nnunet.domain.FoldProgress

class ProgressViewModel(
    plugin: NnUNetPlugin,
    val datasetName: String,
    val configName: String,
) : ViewModel() {
    val folds = plugin.workflows
        .map { workflows ->
            workflows[datasetName]?.trainingConfigs
                ?.find { it.configName == configName }
                ?.folds
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            plugin.workflows.value[datasetName]?.trainingConfigs
                ?.find { it.configName == configName }?.folds,
        )
}
