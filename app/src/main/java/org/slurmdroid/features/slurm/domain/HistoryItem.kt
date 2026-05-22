package org.slurmdroid.features.slurm.domain

sealed class HistoryItem {
    data class Single(val job: SacctJob) : HistoryItem()

    data class ArrayGroup(
        val parentJobId: String,
        val jobName: String,
        val partition: String,
        val startTimestamp: Long?,
        val fullCommand: String?,
        val children: List<SacctJob>,
    ) : HistoryItem()
}

fun HistoryItem.timestamp(): Long? = when (this) {
    is HistoryItem.Single -> job.startTimestamp
    is HistoryItem.ArrayGroup -> startTimestamp
}
