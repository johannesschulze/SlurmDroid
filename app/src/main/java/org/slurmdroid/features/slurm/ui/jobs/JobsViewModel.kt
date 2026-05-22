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
import org.slurmdroid.core.AppPreferences
import org.slurmdroid.core.PollStateHolder
import org.slurmdroid.core.Result
import org.slurmdroid.features.slurm.data.SlurmRepository
import org.slurmdroid.features.slurm.domain.JobFormState
import org.slurmdroid.service.PollScheduler
import javax.inject.Inject

@HiltViewModel
class JobsViewModel @Inject constructor(
    private val repository: SlurmRepository,
    private val pollScheduler: PollScheduler,
    private val pollStateHolder: PollStateHolder,
    private val appPreferences: AppPreferences,
) : ViewModel() {
    val jobs = repository.jobs
    val partitions = repository.partitions
    val pollError = repository.pollError

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _slurmScripts = MutableStateFlow<List<String>>(emptyList())
    val slurmScripts: StateFlow<List<String>> = _slurmScripts.asStateFlow()

    fun refreshSlurmScripts() {
        viewModelScope.launch { _slurmScripts.value = repository.findSlurmScripts() }
    }

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

    fun lastFormState(): JobFormState {
        val cmd = appPreferences.lastSubmittedCommand
        return if (cmd.isNotBlank()) JobFormState.fromSbatchCommand(cmd) else JobFormState()
    }

    fun submitJob(command: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            when (val r = repository.submitJob(command)) {
                is Result.Success -> {
                    appPreferences.lastSubmittedCommand = command
                    repository.poll()
                    pollScheduler.scheduleBackoffAfterAction()
                    onResult(true, "Job submitted: ${r.data}")
                }
                is Result.AuthError -> onResult(false, "Auth error: ${r.message}")
                is Result.ConnectionError -> onResult(false, "Connection error: ${r.message}")
                is Result.UnknownError -> onResult(false, "Submit failed: ${r.message}")
                else -> onResult(false, "Submit failed")
            }
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
