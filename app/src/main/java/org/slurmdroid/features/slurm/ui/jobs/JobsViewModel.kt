package org.slurmdroid.features.slurm.ui.jobs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.slurmdroid.core.Result
import org.slurmdroid.features.slurm.data.SlurmRepository
import org.slurmdroid.service.PollScheduler
import javax.inject.Inject

@HiltViewModel
class JobsViewModel @Inject constructor(
    private val repository: SlurmRepository,
    private val pollScheduler: PollScheduler,
) : ViewModel() {
    val jobs = repository.jobs
    val pollError = repository.pollError

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            repository.poll()
            _isRefreshing.value = false
        }
    }

    fun cancelJob(jobId: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            when (val r = repository.cancelJob(jobId)) {
                is Result.Success -> {
                    repository.poll()
                    pollScheduler.scheduleBackoffAfterAction()
                    onResult(true, "Job $jobId cancelled")
                }
                is Result.AuthError -> onResult(false, "Auth error: ${r.message}")
                is Result.ConnectionError -> onResult(false, "Connection error: ${r.message}")
                else -> onResult(false, "Failed to cancel job $jobId")
            }
        }
    }
}
