package org.slurmdroid.ui.dashboard

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
import org.slurmdroid.core.ssh.ConnectionStatus
import org.slurmdroid.core.ssh.SshManager
import org.slurmdroid.features.slurm.data.SlurmRepository
import javax.inject.Inject

@HiltViewModel
class MainDashboardViewModel @Inject constructor(
    private val slurmRepository: SlurmRepository,
    private val pollStateHolder: PollStateHolder,
    sshManager: SshManager,
) : ViewModel() {
    val pollError = slurmRepository.pollError
    val connectionStatus = sshManager.connectionStatus

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** Fraction 1→0 representing time remaining until the next background poll. */
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
            try {
                slurmRepository.poll()
                pollStateHolder.lastPollCompletedAt.value = System.currentTimeMillis()
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
