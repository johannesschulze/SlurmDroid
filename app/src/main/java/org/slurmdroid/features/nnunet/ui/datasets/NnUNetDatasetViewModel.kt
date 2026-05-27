package org.slurmdroid.features.nnunet.ui.datasets

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import org.slurmdroid.features.nnunet.data.NnUNetRepository
import javax.inject.Inject

@HiltViewModel
class NnUNetDatasetViewModel @Inject constructor(
    repository: NnUNetRepository,
) : ViewModel() {
    val datasets = repository.datasets
    val anyDirConfigured = repository.anyDirConfigured
}
