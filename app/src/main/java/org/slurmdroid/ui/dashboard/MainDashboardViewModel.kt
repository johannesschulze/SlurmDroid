package org.slurmdroid.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.slurmdroid.features.slurm.data.SlurmRepository
import javax.inject.Inject

@HiltViewModel
class MainDashboardViewModel @Inject constructor(
    private val slurmRepository: SlurmRepository,
) : ViewModel() {
    val pollError = slurmRepository.pollError

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            slurmRepository.poll()
            _isRefreshing.value = false
        }
    }
}
