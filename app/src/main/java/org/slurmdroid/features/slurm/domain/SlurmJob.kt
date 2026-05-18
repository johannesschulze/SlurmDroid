package org.slurmdroid.features.slurm.domain

data class SlurmJob(
    val jobId: String,
    val name: String,
    /** RUNNING / PENDING / COMPLETING / COMPLETED / FAILED / CANCELLED / … */
    val state: String,
    /** Raw elapsed time string from squeue, e.g. "1:23:45" or "2-03:15:30". */
    val timeUsed: String,
    /** Raw time-limit string, e.g. "24:00:00" or "UNLIMITED". */
    val timeLimit: String,
    /** For PENDING: reason code. For RUNNING: node list. */
    val reason: String,
    val partition: String,
    /** Elapsed time in seconds (0 if not started yet). */
    val timeUsedSeconds: Long,
    /** Time limit in seconds, null if UNLIMITED. */
    val timeLimitSeconds: Long?,
) {
    val isRunning: Boolean get() = state == "RUNNING"
    val isPending: Boolean get() = state == "PENDING"

    /** Progress fraction [0, 1] for running jobs; null if limit is unknown. */
    val progressFraction: Float?
        get() = timeLimitSeconds?.let { limit ->
            if (limit > 0) (timeUsedSeconds.toFloat() / limit).coerceIn(0f, 1f) else null
        }
}
