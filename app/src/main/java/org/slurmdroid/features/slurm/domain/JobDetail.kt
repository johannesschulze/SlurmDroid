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
)
