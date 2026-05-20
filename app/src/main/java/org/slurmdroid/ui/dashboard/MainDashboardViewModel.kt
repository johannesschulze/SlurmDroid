package org.slurmdroid.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.slurmdroid.core.ssh.ConnectionStatus
import org.slurmdroid.core.ssh.SshManager
import org.slurmdroid.features.slurm.data.SlurmRepository
import javax.inject.Inject

@HiltViewModel
class MainDashboardViewModel @Inject constructor(
    private val slurmRepository: SlurmRepository,
    sshManager: SshManager,
) : ViewModel() {
    val pollError = slurmRepository.pollError
    val connectionStatus = sshManager.connectionStatus

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                slurmRepository.poll()
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
