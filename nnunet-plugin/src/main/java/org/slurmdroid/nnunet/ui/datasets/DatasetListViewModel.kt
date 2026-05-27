package org.slurmdroid.nnunet.ui.datasets

import androidx.lifecycle.ViewModel
import org.slurmdroid.nnunet.NnUNetPlugin

class DatasetListViewModel(plugin: NnUNetPlugin) : ViewModel() {
    val datasets = plugin.datasets
    val anyDirConfigured = plugin.anyDirConfigured
}
