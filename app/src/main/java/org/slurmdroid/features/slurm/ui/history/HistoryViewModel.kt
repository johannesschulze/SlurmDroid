package org.slurmdroid.features.slurm.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.slurmdroid.core.Result
import org.slurmdroid.features.slurm.data.SlurmRepository
import org.slurmdroid.features.slurm.domain.SacctJob
import org.slurmdroid.service.PollScheduler
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: SlurmRepository,
    private val pollScheduler: PollScheduler,
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /**
     * Sacct jobs enriched with [fullCommand] for those submitted through the app.
     * Sorted newest-first by start time.
     */
    val history: StateFlow<List<SacctJob>> = combine(
        repository.sacctJobs,
        repository.jobHistory,
    ) { sacctList, localList ->
        val commandByJobId = localList
            .mapNotNull { entry -> entry.slurmJobId?.let { id -> id to entry.fullCommand } }
            .toMap()
        sacctList.map { job -> job.copy(fullCommand = commandByJobId[job.jobId]) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                repository.poll()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun resubmit(command: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            when (val r = repository.submitJob(command)) {
                is Result.Success -> {
                    repository.poll()
                    pollScheduler.scheduleBackoffAfterAction()
                    onResult(true, "Job submitted: ${r.data}")
                }
                is Result.AuthError -> onResult(false, "Auth error: ${r.message}")
                is Result.ConnectionError -> onResult(false, "Connection error: ${r.message}")
                else -> onResult(false, "Failed to submit job")
            }
        }
    }
}
