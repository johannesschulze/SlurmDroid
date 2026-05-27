package org.slurmdroid.features.slurm.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.slurmdroid.core.PollStateHolder
import org.slurmdroid.features.slurm.data.SlurmRepository
import javax.inject.Inject

@HiltViewModel
class SlurmOverviewViewModel @Inject constructor(
    private val repository: SlurmRepository,
    private val pollStateHolder: PollStateHolder,
) : ViewModel() {
    val partitions = repository.partitions
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
            kotlinx.coroutines.delay(200)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1f)

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try { repository.poll() } finally { _isRefreshing.value = false }
        }
    }
}
