package org.slurmdroid.features.slurm.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.slurmdroid.core.Result
import org.slurmdroid.features.slurm.data.SlurmRepository
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: SlurmRepository,
) : ViewModel() {
    val history = repository.jobHistory

    fun resubmit(command: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            when (val r = repository.submitJob(command)) {
                is Result.Success -> {
                    repository.poll()
                    onResult(true, "Job submitted: ${r.data}")
                }
                is Result.AuthError -> onResult(false, "Auth error: ${r.message}")
                is Result.ConnectionError -> onResult(false, "Connection error: ${r.message}")
                else -> onResult(false, "Failed to submit job")
            }
        }
    }
}
