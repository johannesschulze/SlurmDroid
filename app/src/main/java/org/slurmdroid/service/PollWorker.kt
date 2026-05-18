package org.slurmdroid.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.slurmdroid.features.slurm.data.SlurmRepository

@HiltWorker
class PollWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: SlurmRepository,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = try {
        repository.poll()
        Result.success()
    } catch (_: Exception) {
        Result.retry()
    }
}
