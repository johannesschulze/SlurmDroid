package org.slurmdroid.features.slurm.data

import org.slurmdroid.core.Result
import org.slurmdroid.features.slurm.domain.SlurmJob
import javax.inject.Inject

/**
 * Parses output of:
 *   squeue -u <user> -o "%i|%j|%T|%M|%l|%R|%P" --noheader
 *
 * Fields: jobId | name | state | elapsed | timeLimit | reason | partition
 *
 * Time formats accepted: [D-]H:MM:SS, M:SS, UNLIMITED, 0:00.
 */
class SqueueParser @Inject constructor() {

    fun parse(output: String): Result<List<SlurmJob>> = try {
        val jobs = output.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { parseLine(it) }
        Result.Success(jobs)
    } catch (e: Exception) {
        Result.ParseError("squeue parse failed: ${e.message}")
    }

    private fun parseLine(line: String): SlurmJob? {
        val cols = line.split("|")
        if (cols.size < 7) return null

        val jobId = cols[0].trim()
        val name = cols[1].trim()
        val state = cols[2].trim()
        val timeUsed = cols[3].trim()
        val timeLimit = cols[4].trim()
        val reason = cols[5].trim().let { if (it == "(null)") "" else it }
        val partition = cols[6].trim()

        return SlurmJob(
            jobId = jobId,
            name = name,
            state = state,
            timeUsed = timeUsed,
            timeLimit = timeLimit,
            reason = reason,
            partition = partition,
            timeUsedSeconds = parseTimeToSeconds(timeUsed) ?: 0L,
            timeLimitSeconds = parseTimeToSeconds(timeLimit),
        )
    }

    companion object {
        /**
         * Parses Slurm time strings into total seconds.
         *
         * Accepted formats:
         *   - UNLIMITED / NOT_SET → null
         *   - D-HH:MM:SS
         *   - H:MM:SS or HH:MM:SS
         *   - M:SS
         *   - 0:00
         */
        fun parseTimeToSeconds(time: String): Long? {
            val t = time.trim()
            if (t.isBlank() || t.equals("UNLIMITED", ignoreCase = true) ||
                t.equals("NOT_SET", ignoreCase = true) || t == "N/A"
            ) return null

            return try {
                val dayParts = t.split("-")
                val days = if (dayParts.size == 2) dayParts[0].toLong() else 0L
                val clockPart = if (dayParts.size == 2) dayParts[1] else dayParts[0]

                val clockParts = clockPart.split(":")
                val seconds = when (clockParts.size) {
                    3 -> clockParts[0].toLong() * 3600 + clockParts[1].toLong() * 60 + clockParts[2].toLong()
                    2 -> clockParts[0].toLong() * 60 + clockParts[1].toLong()
                    1 -> clockParts[0].toLong()
                    else -> 0L
                }
                days * 86_400L + seconds
            } catch (_: Exception) {
                null
            }
        }
    }
}
