package org.slurmdroid.nnunet.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.slurmdroid.nnunet.NnUNetPlugin
import org.slurmdroid.nnunet.domain.NnUNetWorkflow

class DatasetDetailViewModel(
    plugin: NnUNetPlugin,
    val datasetName: String,
) : ViewModel() {
    val workflow = plugin.workflows
        .map { it[datasetName] }
        .stateIn(viewModelScope, SharingStarted.Eagerly, plugin.workflows.value[datasetName])
    val lastPollTime = plugin.lastPollTime
    val lastPollError = plugin.lastPollError
}
