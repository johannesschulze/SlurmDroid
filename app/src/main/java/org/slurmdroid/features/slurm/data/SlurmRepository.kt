package org.slurmdroid.features.slurm.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slurmdroid.core.Result
import org.slurmdroid.core.db.AppDatabase
import org.slurmdroid.core.db.entities.JobHistory
import org.slurmdroid.core.ssh.CommandExecutor
import org.slurmdroid.core.ssh.SshCredentialStore
import org.slurmdroid.features.slurm.domain.Partition
import org.slurmdroid.features.slurm.domain.SlurmJob
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SlurmRepository @Inject constructor(
    private val commandExecutor: CommandExecutor,
    private val sinfoParser: SinfoParser,
    private val sinfoTIdleParser: SinfoTIdleParser,
    private val squeueParser: SqueueParser,
    private val credentialStore: SshCredentialStore,
    private val database: AppDatabase,
) {
    private val _partitions = MutableStateFlow<List<Partition>>(emptyList())
    val partitions: StateFlow<List<Partition>> = _partitions.asStateFlow()

    private val _jobs = MutableStateFlow<List<SlurmJob>>(emptyList())
    val jobs: StateFlow<List<SlurmJob>> = _jobs.asStateFlow()

    /** Non-null when the last squeue poll failed. Cleared on success. */
    private val _pollError = MutableStateFlow<String?>(null)
    val pollError: StateFlow<String?> = _pollError.asStateFlow()

    val jobHistory: Flow<List<JobHistory>> = database.jobHistoryDao().observeAll()

    private val pollMutex = Mutex()

    // ── polling ────────────────────────────────────────────────────────────────

    suspend fun poll() {
        if (!pollMutex.tryLock()) return   // skip if a poll is already in flight
        try {
            fetchPartitions()
            fetchJobs()
        } finally {
            pollMutex.unlock()
        }
    }

    private suspend fun fetchPartitions() {
        // Try full sinfo first (works on unrestricted clusters)
        val sinfoResult = commandExecutor.execute(
            "sinfo -o \"%P|%a|%D|%T|%C\" --noheader"
        )
        if (sinfoResult is Result.Success) {
            val parsed = sinfoParser.parse(sinfoResult.data)
            if (parsed is Result.Success && parsed.data.isNotEmpty()) {
                _partitions.value = parsed.data
                return
            }
        }

        // Fall back to sinfo_t_idle (KIT/SCC cluster and similarly restricted setups)
        val tIdleResult = commandExecutor.execute("sinfo_t_idle")
        if (tIdleResult is Result.Success) {
            val parsed = sinfoTIdleParser.parse(tIdleResult.data)
            if (parsed is Result.Success) {
                _partitions.value = parsed.data
                return
            }
        }

        // Both failed — keep existing partition list rather than wiping it
    }

    private suspend fun fetchJobs() {
        val user = credentialStore.username
        val result = commandExecutor.execute(
            "squeue -u $user -o \"%i|%j|%T|%M|%l|%R|%P\" --noheader"
        )
        when (result) {
            is Result.Success -> {
                val parsed = squeueParser.parse(result.data)
                when (parsed) {
                    is Result.Success -> {
                        _jobs.value = parsed.data
                        _pollError.value = null
                        updateHistoryStatuses(parsed.data)
                    }
                    else -> _pollError.value = "Failed to parse job list"
                }
            }
            is Result.AuthError -> _pollError.value = "Authentication error — check credentials"
            is Result.ConnectionError -> _pollError.value = "Connection lost"
            else -> _pollError.value = "Failed to fetch jobs"
        }
    }

    private suspend fun updateHistoryStatuses(activeJobs: List<SlurmJob>) {
        val dao = database.jobHistoryDao()
        activeJobs.forEach { job -> dao.updateStatus(job.jobId, job.state) }
    }

    // ── actions ────────────────────────────────────────────────────────────────

    /**
     * Submits a job via `sbatch` and records it in [jobHistory].
     * Returns [Result.Success] with the Slurm job ID string on success.
     */
    suspend fun submitJob(command: String): Result<String> {
        val result = commandExecutor.execute(command)
        return when (result) {
            is Result.Success -> {
                val jobId = extractJobId(result.data)
                saveToHistory(command, jobId)
                Result.Success(jobId ?: result.data.trim())
            }
            else -> @Suppress("UNCHECKED_CAST") (result as Result<String>)
        }
    }

    /** Cancels a running or pending job. */
    suspend fun cancelJob(jobId: String): Result<String> =
        commandExecutor.execute("scancel $jobId")

    // ── helpers ────────────────────────────────────────────────────────────────

    private suspend fun saveToHistory(command: String, jobId: String?) {
        val partition = Regex("""(?:-p|--partition)[= ](\S+)""").find(command)?.groupValues?.get(1) ?: ""
        val jobName = Regex("""(?:-J|--job-name)[= ](\S+)""").find(command)?.groupValues?.get(1)
            ?: command.trim().split(Regex("\\s+")).lastOrNull()?.substringAfterLast("/") ?: "unknown"
        database.jobHistoryDao().insert(
            JobHistory(
                timestamp = System.currentTimeMillis(),
                jobName = jobName,
                fullCommand = command,
                partition = partition,
                lastKnownStatus = "SUBMITTED",
                slurmJobId = jobId,
            )
        )
    }

    /** Extracts the Slurm job ID from sbatch output: "Submitted batch job 12345" */
    private fun extractJobId(output: String): String? =
        Regex("""Submitted batch job (\S+)""").find(output)?.groupValues?.get(1)
}
