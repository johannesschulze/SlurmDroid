package org.slurmdroid.features.slurm.data

import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

data class ScontrolJobInfo(
    val numCpus: Int? = null,
    val numNodes: Int? = null,
    val timeLimit: String? = null,
    val startTime: Long? = null,
    val endTime: Long? = null,
    val nodeList: String? = null,
    val requestedMemory: String? = null,
    val submitLine: String? = null,
)

class ScontrolParser @Inject constructor() {

    private val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT)

    fun parse(output: String): ScontrolJobInfo {
        if (output.isBlank()) return ScontrolJobInfo()

        // SubmitLine must be captured before flattening — it contains spaces.
        val submitLine = Regex("""SubmitLine=(.+)""").find(output)?.groupValues?.get(1)?.trim()
            ?.takeIf { it.isNotBlank() }

        fun field(key: String): String? =
            Regex("""\b${Regex.escape(key)}=(\S+)""").find(output)?.groupValues?.get(1)
                ?.takeIf { it != "(null)" && it != "N/A" && it != "None" && it != "Unknown" }

        fun parseIso(s: String?): Long? = s?.let {
            runCatching { isoFmt.parse(it)?.time }.getOrNull()
        }

        // Extract mem= from ReqTRES=cpu=1,mem=127500M,node=1,...
        val requestedMemory = field("ReqTRES")
            ?.split(',')
            ?.firstOrNull { it.startsWith("mem=") }
            ?.substringAfter("=")
            ?.takeIf { it.isNotBlank() && it != "0" }

        // NodeList for running jobs; SchedNodeList for scheduled-but-not-yet-running.
        val nodeList = field("NodeList") ?: field("SchedNodeList")

        return ScontrolJobInfo(
            numCpus = field("NumCPUs")?.toIntOrNull(),
            numNodes = field("NumNodes")?.toIntOrNull(),
            timeLimit = field("TimeLimit"),
            startTime = parseIso(field("StartTime")),
            endTime = parseIso(field("EndTime")),
            nodeList = nodeList,
            requestedMemory = requestedMemory,
            submitLine = submitLine,
        )
    }
}
