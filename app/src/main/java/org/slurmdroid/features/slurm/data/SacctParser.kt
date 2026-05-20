package org.slurmdroid.features.slurm.data

import org.slurmdroid.core.Result
import org.slurmdroid.features.slurm.domain.SacctJob
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SacctParser @Inject constructor() {

    fun parse(output: String): Result<List<SacctJob>> = try {
        val jobs = output.lines()
            .filter { it.isNotBlank() }
            .mapNotNull { parseLine(it) }
        Result.Success(jobs)
    } catch (e: Exception) {
        Result.ParseError("Failed to parse sacct output: ${e.message}")
    }

    private fun parseLine(line: String): SacctJob? {
        val parts = line.split('|')
        if (parts.size < 6) return null
        val jobId = parts[0].trim()
        // -X (allocations-only) should already exclude steps, but filter defensively
        if (jobId.contains('.')) return null
        return SacctJob(
            jobId = jobId,
            jobName = parts[1].trim(),
            // State can be "CANCELLED by 1234" — keep only the first word
            state = parts[2].trim().substringBefore(' '),
            startTimestamp = parseDateTime(parts[3].trim()),
            elapsed = parts[4].trim(),
            partition = parts[5].trim(),
        )
    }

    private fun parseDateTime(s: String): Long? {
        if (s.isBlank() || s == "None" || s == "Unknown" || s == "N/A") return null
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).parse(s)?.time
        } catch (_: Exception) {
            null
        }
    }
}
