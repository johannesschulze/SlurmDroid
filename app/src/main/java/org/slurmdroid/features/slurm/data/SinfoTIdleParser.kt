package org.slurmdroid.features.slurm.data

import org.slurmdroid.core.Result
import org.slurmdroid.features.slurm.domain.Partition
import javax.inject.Inject

/**
 * Parses output of the cluster-specific `sinfo_t_idle` command used when the standard
 * `sinfo` is restricted (Access/permission denied).
 *
 * Example line:
 *   Partition gpu_a100_il             :      0 nodes idle
 *
 * Only idle node count is available; nodesTotal is set to -1 (unknown) and CPU fields to 0.
 */
class SinfoTIdleParser @Inject constructor() {

    private val lineRegex = Regex("""^\s*Partition\s+(\S+)\s*:\s*(\d+)\s+nodes\s+idle""")

    fun parse(output: String): Result<List<Partition>> = try {
        val partitions = output.lines()
            .mapNotNull { lineRegex.find(it.trim()) }
            .map { match ->
                Partition(
                    name = match.groupValues[1],
                    isDefault = false,
                    state = "up",
                    nodesTotal = -1,
                    nodesAvailable = match.groupValues[2].toInt(),
                    cpusAllocated = 0,
                    cpusIdle = 0,
                    cpusTotal = 0,
                )
            }
        Result.Success(partitions)
    } catch (e: Exception) {
        Result.ParseError("sinfo_t_idle parse failed: ${e.message}")
    }
}
