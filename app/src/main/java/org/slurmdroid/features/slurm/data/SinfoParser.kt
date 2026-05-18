package org.slurmdroid.features.slurm.data

import org.slurmdroid.core.Result
import org.slurmdroid.features.slurm.domain.Partition
import javax.inject.Inject

/**
 * Parses output of:
 *   sinfo -o "%P|%a|%D|%T|%C" --noheader
 *
 * Fields: partitionName | availState | nodeCount | nodeState | cpus(alloc/idle/other/total)
 *
 * sinfo emits one line per unique (partition × nodeState) combination, so a single partition
 * can appear on multiple lines. This parser aggregates them into one [Partition] per name.
 */
class SinfoParser @Inject constructor() {

    fun parse(output: String): Result<List<Partition>> = try {
        val lines = output.lines().map { it.trim() }.filter { it.isNotBlank() }
        val raw = lines.mapNotNull { parseLine(it) }
        val partitions = raw
            .groupBy { it.partitionName }
            .map { (_, rows) -> aggregate(rows) }
        Result.Success(partitions)
    } catch (e: Exception) {
        Result.ParseError("sinfo parse failed: ${e.message}")
    }

    private data class Row(
        val partitionName: String,
        val isDefault: Boolean,
        val availState: String,
        val nodeCount: Int,
        val nodeState: String,
        val cpusAllocated: Int,
        val cpusIdle: Int,
        val cpusOther: Int,
        val cpusTotal: Int,
    )

    private fun parseLine(line: String): Row? {
        val cols = line.split("|")
        if (cols.size < 5) return null

        val rawName = cols[0].trim()
        val isDefault = rawName.endsWith("*")
        val name = rawName.trimEnd('*')

        val availState = cols[1].trim()
        val nodeCount = cols[2].trim().toIntOrNull() ?: return null
        val nodeState = cols[3].trim().lowercase()

        // %C format: allocated/idle/other/total
        val cpuParts = cols[4].trim().split("/")
        if (cpuParts.size < 4) return null
        val cpusAllocated = cpuParts[0].toIntOrNull() ?: 0
        val cpusIdle = cpuParts[1].toIntOrNull() ?: 0
        val cpusOther = cpuParts[2].toIntOrNull() ?: 0
        val cpusTotal = cpuParts[3].toIntOrNull() ?: 0

        return Row(name, isDefault, availState, nodeCount, nodeState, cpusAllocated, cpusIdle, cpusOther, cpusTotal)
    }

    private fun aggregate(rows: List<Row>): Partition {
        val first = rows.first()
        val nodesTotal = rows.sumOf { it.nodeCount }
        val nodesAvailable = rows
            .filter { it.nodeState in AVAILABLE_NODE_STATES }
            .sumOf { it.nodeCount }
        return Partition(
            name = first.partitionName,
            isDefault = first.isDefault,
            state = first.availState,
            nodesTotal = nodesTotal,
            nodesAvailable = nodesAvailable,
            cpusAllocated = rows.sumOf { it.cpusAllocated },
            cpusIdle = rows.sumOf { it.cpusIdle },
            cpusTotal = rows.sumOf { it.cpusTotal },
        )
    }

    companion object {
        /** Node states that count toward "available" for job submission. */
        private val AVAILABLE_NODE_STATES = setOf("idle", "mix")
    }
}
