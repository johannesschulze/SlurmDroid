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
import org.slurmdroid.core.AppPreferences
import org.slurmdroid.core.Result
import org.slurmdroid.features.slurm.data.SlurmRepository
import org.slurmdroid.features.slurm.domain.HistoryItem
import org.slurmdroid.features.slurm.domain.JobFormState
import org.slurmdroid.features.slurm.domain.timestamp
import org.slurmdroid.service.PollScheduler
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: SlurmRepository,
    private val pollScheduler: PollScheduler,
    private val appPreferences: AppPreferences,
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val partitions = repository.partitions

    private val _slurmScripts = MutableStateFlow<List<String>>(emptyList())
    val slurmScripts: StateFlow<List<String>> = _slurmScripts.asStateFlow()

    fun refreshSlurmScripts() {
        viewModelScope.launch { _slurmScripts.value = repository.findSlurmScripts() }
    }

    /**
     * History enriched with [HistoryItem.Single] for regular jobs and
     * [HistoryItem.ArrayGroup] for Slurm array jobs, sorted newest-first.
     */
    val history: StateFlow<List<HistoryItem>> = combine(
        repository.sacctJobs,
        repository.jobHistory,
    ) { sacctList, localList ->
        val commandByJobId = localList
            .mapNotNull { entry -> entry.slurmJobId?.let { id -> id to entry.fullCommand } }
            .toMap()

        // Detect array tasks: ID like "4782656_0" where the suffix is numeric
        fun isArrayTask(jobId: String) =
            '_' in jobId && jobId.substringAfterLast('_').toIntOrNull() != null
        fun parentId(jobId: String) = jobId.substringBefore('_')

        val arrayTasks = sacctList.filter { isArrayTask(it.jobId) }
        val arrayParentIds = arrayTasks.map { parentId(it.jobId) }.toSet()

        // Standalone = not an array task and not a bare parent summary row for an array group
        val standalones = sacctList
            .filter { !isArrayTask(it.jobId) && it.jobId !in arrayParentIds }
            .map { job -> job.copy(fullCommand = commandByJobId[job.jobId]) }
            .map { HistoryItem.Single(it) }

        val groups = arrayTasks
            .groupBy { parentId(it.jobId) }
            .map { (pid, children) ->
                val first = children.minByOrNull { it.startTimestamp ?: Long.MAX_VALUE }
                    ?: children.first()
                HistoryItem.ArrayGroup(
                    parentJobId = pid,
                    jobName = first.jobName,
                    partition = first.partition,
                    startTimestamp = first.startTimestamp,
                    fullCommand = commandByJobId[pid],
                    children = children.sortedWith(
                        compareBy { it.jobId.substringAfterLast('_').toIntOrNull() ?: 0 }
                    ),
                )
            }

        (standalones + groups)
            .sortedByDescending { it.timestamp() ?: 0L }
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

    fun lastFormState(): JobFormState {
        val cmd = appPreferences.lastSubmittedCommand
        return if (cmd.isNotBlank()) JobFormState.fromSbatchCommand(cmd) else JobFormState()
    }

    fun resubmit(command: String, onResult: (Boolean, String) -> Unit) {
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
}
