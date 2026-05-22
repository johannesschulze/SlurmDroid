package org.slurmdroid.features.slurm.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slurmdroid.core.Result
import org.slurmdroid.core.asError
import org.slurmdroid.core.db.AppDatabase
import org.slurmdroid.core.db.entities.JobHistory
import org.slurmdroid.core.ssh.CommandExecutor
import org.slurmdroid.core.ssh.SshCredentialStore
import org.slurmdroid.features.slurm.domain.JobDetail
import org.slurmdroid.features.slurm.domain.Partition
import org.slurmdroid.features.slurm.domain.SacctJob
import org.slurmdroid.features.slurm.domain.SlurmJob
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SlurmRepository @Inject constructor(
    private val commandExecutor: CommandExecutor,
    private val sinfoParser: SinfoParser,
    private val sinfoTIdleParser: SinfoTIdleParser,
    private val squeueParser: SqueueParser,
    private val sacctParser: SacctParser,
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

    private val _sacctJobs = MutableStateFlow<List<SacctJob>>(emptyList())
    val sacctJobs: StateFlow<List<SacctJob>> = _sacctJobs.asStateFlow()

    val jobHistory: Flow<List<JobHistory>> = database.jobHistoryDao().observeAll()

    private val pollMutex = Mutex()

    // ── polling ────────────────────────────────────────────────────────────────

    suspend fun poll() {
        if (!pollMutex.tryLock()) return   // skip if a poll is already in flight
        try {
            fetchPartitions()
            fetchJobs()
            fetchSacctHistory()
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

    private suspend fun fetchSacctHistory() {
        val user = credentialStore.username
        val thirtyDaysAgo = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
            .format(Date(System.currentTimeMillis() - 30L * 24 * 3600 * 1000))
        val result = commandExecutor.execute(
            "sacct -u $user -X -S $thirtyDaysAgo" +
                " -o \"JobID,JobName,State,Start,Elapsed,Partition\" --noheader -P"
        )
        if (result is Result.Success) {
            val parsed = sacctParser.parse(result.data)
            if (parsed is Result.Success && parsed.data.isNotEmpty()) {
                _sacctJobs.value = parsed.data.sortedByDescending { it.startTimestamp ?: 0L }
            }
        }
        // On failure keep existing data rather than wiping it
    }

    private suspend fun updateHistoryStatuses(activeJobs: List<SlurmJob>) {
        val dao = database.jobHistoryDao()
        activeJobs.forEach { job -> dao.updateStatus(job.jobId, job.state) }
    }

    // ── job detail ────────────────────────────────────────────────────────────

    /**
     * Fetches detail for a single job.
     * Uses sstat for jobs currently in the active list (RUNNING/PENDING),
     * sacct for everything else.
     */
    suspend fun fetchJobDetail(jobId: String): Result<JobDetail> {
        val active = _jobs.value.find { it.jobId == jobId }
        return if (active != null) fetchRunningJobDetail(active)
        else fetchHistoricalJobDetail(jobId)
    }

    private suspend fun fetchRunningJobDetail(job: SlurmJob): Result<JobDetail> {
        // Try .batch step first (most useful for batch jobs), fall back to any step
        var sstatOut = commandExecutor.execute(
            "sstat -j ${job.jobId}.batch --format=JobID,AveCPU,AveRSS,MaxRSS,NTasks --noheader -P 2>/dev/null"
        ).let { if (it is Result.Success && it.data.isNotBlank()) it.data else null }
        if (sstatOut == null) {
            sstatOut = commandExecutor.execute(
                "sstat -j ${job.jobId} --format=JobID,AveCPU,AveRSS,MaxRSS,NTasks --noheader -P 2>/dev/null"
            ).let { if (it is Result.Success) it.data else null }
        }
        val (avgCpu, maxRss, nTasks) = parseSstatFirstLine(sstatOut)
        return Result.Success(
            JobDetail(
                jobId = job.jobId,
                jobName = job.name,
                state = job.state,
                partition = job.partition,
                elapsed = job.timeUsed,
                timeLimit = job.timeLimit,
                nodeList = if (job.isRunning) job.reason else "",
                startTime = null,
                endTime = null,
                nCpus = null,
                nNodes = null,
                requestedMemory = null,
                maxRss = maxRss,
                exitCode = null,
                avgCpu = avgCpu,
                nTasks = nTasks,
            )
        )
    }

    private fun parseSstatFirstLine(output: String?): Triple<String?, String?, Int?> {
        val line = output?.lines()?.firstOrNull { it.isNotBlank() } ?: return Triple(null, null, null)
        val p = line.split('|')
        if (p.size < 5) return Triple(null, null, null)
        return Triple(
            p[1].trim().takeIf { it.isNotBlank() && it != "00:00:00.000" },
            p[3].trim().takeIf { it.isNotBlank() && it != "0" },
            p[4].trim().toIntOrNull(),
        )
    }

    private suspend fun fetchHistoricalJobDetail(jobId: String): Result<JobDetail> {
        val result = commandExecutor.execute(
            "sacct -j $jobId -X" +
                " -o \"JobID,JobName,State,Start,End,Elapsed,Partition,NodeList,AllocCPUS,AllocNodes,ReqMem,MaxRSS,ExitCode\"" +
                " --noheader -P"
        )
        if (result !is Result.Success) return result.asError()
        val line = result.data.lines().firstOrNull { it.isNotBlank() }
            ?: return Result.ParseError("No sacct data for job $jobId")
        val p = line.split('|')
        if (p.size < 13) return Result.ParseError("Unexpected sacct output for job $jobId")
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT)
        fun ts(s: String) = s.trim().takeIf { it.isNotBlank() && it != "None" && it != "Unknown" }
            ?.let { runCatching { fmt.parse(it)?.time }.getOrNull() }
        return Result.Success(
            JobDetail(
                jobId = p[0].trim(),
                jobName = p[1].trim(),
                state = p[2].trim().substringBefore(' '),
                startTime = ts(p[3]),
                endTime = ts(p[4]),
                elapsed = p[5].trim(),
                timeLimit = "",
                partition = p[6].trim(),
                nodeList = p[7].trim(),
                nCpus = p[8].trim().toIntOrNull(),
                nNodes = p[9].trim().toIntOrNull(),
                requestedMemory = p[10].trim().takeIf { it.isNotBlank() },
                maxRss = p[11].trim().takeIf { it.isNotBlank() && it != "0" },
                exitCode = p[12].trim().takeIf { it.isNotBlank() },
                avgCpu = null,
                nTasks = null,
            )
        )
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
            else -> result.asError()
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
