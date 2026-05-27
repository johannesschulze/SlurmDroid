package org.slurmdroid.features.nnunet.ui.dashboard

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import org.slurmdroid.features.nnunet.data.NnUNetRepository
import javax.inject.Inject

@HiltViewModel
class NnUNetDashboardViewModel @Inject constructor(
    repository: NnUNetRepository,
) : ViewModel() {
    val datasets = repository.datasets
    val resolvedResultsDir = repository.resolvedResultsDir
}
