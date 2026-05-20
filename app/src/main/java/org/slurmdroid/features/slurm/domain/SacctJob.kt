package org.slurmdroid.features.slurm.domain

data class SacctJob(
    val jobId: String,
    val jobName: String,
    val state: String,
    val startTimestamp: Long?,
    val elapsed: String,
    val partition: String,
    /** Non-null only for jobs that were submitted through the app and have a stored command. */
    val fullCommand: String? = null,
)
