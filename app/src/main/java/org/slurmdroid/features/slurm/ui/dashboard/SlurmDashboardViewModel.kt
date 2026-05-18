package org.slurmdroid.features.slurm.ui.dashboard

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import org.slurmdroid.features.slurm.data.SlurmRepository
import javax.inject.Inject

@HiltViewModel
class SlurmDashboardViewModel @Inject constructor(
    repository: SlurmRepository,
) : ViewModel() {
    val partitions = repository.partitions
    val jobs = repository.jobs
}
