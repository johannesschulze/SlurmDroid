package org.slurmdroid.features.slurm.domain

data class JobDetail(
    val jobId: String,
    val jobName: String,
    val state: String,
    val partition: String,
    val elapsed: String,
    val timeLimit: String,
    val nodeList: String,
    // sacct fields (completed jobs)
    val startTime: Long?,
    val endTime: Long?,
    val nCpus: Int?,
    val nNodes: Int?,
    val requestedMemory: String?,
    val maxRss: String?,
    val exitCode: String?,
    // sstat fields (running jobs only)
    val avgCpu: String?,
    val nTasks: Int?,
    /** Full sbatch command from local history, if this job was submitted through the app. */
    val fullCommand: String? = null,
    /** Submission line from `scontrol show job` — available for any job, not just app-submitted ones. */
    val submitLine: String? = null,
    /** Non-empty when this is a Slurm array parent job — contains the individual array tasks. */
    val arrayChildren: List<SacctJob> = emptyList(),
)
