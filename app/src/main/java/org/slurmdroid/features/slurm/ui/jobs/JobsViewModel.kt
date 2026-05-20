package org.slurmdroid.features.slurm.ui.jobs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.slurmdroid.core.PollStateHolder
import org.slurmdroid.core.Result
import org.slurmdroid.features.slurm.data.SlurmRepository
import org.slurmdroid.service.PollScheduler
import javax.inject.Inject

@HiltViewModel
class JobsViewModel @Inject constructor(
    private val repository: SlurmRepository,
    private val pollScheduler: PollScheduler,
    private val pollStateHolder: PollStateHolder,
) : ViewModel() {
    val jobs = repository.jobs
    val pollError = repository.pollError

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val pollCountdown: StateFlow<Float> = flow {
        while (true) {
            val lastPoll = pollStateHolder.lastPollCompletedAt.value
            val fraction = if (lastPoll == 0L) 1f else {
                val elapsed = System.currentTimeMillis() - lastPoll
                (1f - elapsed.toFloat() / pollStateHolder.intervalMs).coerceIn(0f, 1f)
            }
            emit(fraction)
            delay(200)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1f)

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
