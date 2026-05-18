package org.slurmdroid.features.slurm.domain

data class Partition(
    val name: String,
    val isDefault: Boolean,
    /** Partition availability: up / down / drain / inactive. Empty if not available from source. */
    val state: String,
    /** Total nodes. -1 means unknown (e.g. when sinfo is restricted and only sinfo_t_idle is available). */
    val nodesTotal: Int,
    /** Nodes in state idle or mix (partially used). */
    val nodesAvailable: Int,
    val cpusAllocated: Int,
    val cpusIdle: Int,
    val cpusTotal: Int,
) {
    val isTotalKnown: Boolean get() = nodesTotal >= 0

    val cpuUsageFraction: Float
        get() = if (cpusTotal > 0) cpusAllocated.toFloat() / cpusTotal else 0f

    val isUp: Boolean get() = state.isBlank() || state == "up"
}
