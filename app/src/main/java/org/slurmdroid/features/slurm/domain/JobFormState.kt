package org.slurmdroid.features.slurm.domain

data class JobFormState(
    val partition: String = "",
    val jobName: String = "",
    val command: String = "",
    /** Non-null when the job should be submitted as `sbatch [options] scriptPath` instead of `--wrap`. */
    val scriptPath: String? = null,
    /** Arguments passed to the script after the path, e.g. `--epochs 10`. */
    val scriptArgs: String = "",
    val nodes: String = "",
    val cpus: String = "",
    val memory: String = "",
    val gpus: String = "",
    val hours: String = "",
    val minutes: String = "",
    val seconds: String = "",
) {
    fun timeString(): String? {
        val h = hours.toIntOrNull() ?: 0
        val m = minutes.toIntOrNull() ?: 0
        val s = seconds.toIntOrNull() ?: 0
        if (h == 0 && m == 0 && s == 0) return null
        return "%02d:%02d:%02d".format(h, m, s)
    }

    fun toSbatchCommand(): String = buildList {
        add("sbatch")
        if (partition.isNotBlank()) add("--partition=$partition")
        if (jobName.isNotBlank()) add("-J $jobName")
        nodes.toIntOrNull()?.takeIf { it > 0 }?.let { add("--nodes=$it") }
        cpus.toIntOrNull()?.takeIf { it > 0 }?.let { add("--cpus-per-task=$it") }
        if (memory.isNotBlank()) add("--mem=$memory")
        gpus.toIntOrNull()?.takeIf { it > 0 }?.let { add("--gres=gpu:$it") }
        timeString()?.let { add("--time=$it") }
        if (scriptPath != null) {
            add(scriptPath)
            if (scriptArgs.isNotBlank()) add(scriptArgs)
        } else {
            add("--wrap='${command.replace("'", "\\'")}'")
        }
    }.joinToString(" ")

    companion object {
        fun fromSbatchCommand(line: String): JobFormState {
            fun findArg(vararg flags: String): String? {
                for (flag in flags) {
                    Regex("""$flag=([^\s]+)""").find(line)
                        ?.let { return it.groupValues[1].trim('\'', '"') }
                    Regex("""$flag\s+([^\s]+)""").find(line)
                        ?.let { return it.groupValues[1].trim('\'', '"') }
                }
                return null
            }

            val wrapIdx = line.indexOf("--wrap=")
            val wrapCommand = if (wrapIdx >= 0) {
                val after = line.substring(wrapIdx + 7)
                when {
                    after.startsWith("'") ->
                        after.drop(1).substringBefore("'").replace("\\'", "'")
                    after.startsWith("\"") ->
                        after.drop(1).substringBefore("\"").replace("\\\"", "\"")
                    else -> after.substringBefore(" ")
                }
            } else ""

            // If no --wrap, walk tokens: first non-option token is the script path,
            // remaining non-option tokens are script args.
            // Short flags that consume the next token as a value (-J, -p, -N, -c, -t).
            val shortFlagsWithValue = setOf("-J", "-p", "-N", "-c", "-t")
            var scriptPath: String? = null
            val scriptArgTokens = mutableListOf<String>()
            if (wrapIdx < 0) {
                val tokens = line.trim().split(Regex("\\s+"))
                var skipNext = false
                for (token in tokens) {
                    when {
                        token == "sbatch" -> {}
                        skipNext -> skipNext = false
                        token in shortFlagsWithValue -> skipNext = true
                        token.startsWith("-") -> {}
                        scriptPath == null -> scriptPath = token
                        else -> scriptArgTokens.add(token)
                    }
                }
            }

            val (h, m, s) = parseTimeParts(findArg("--time", "-t"))

            return JobFormState(
                partition = findArg("--partition", "-p") ?: "",
                jobName = findArg("--job-name", "-J") ?: "",
                command = wrapCommand,
                scriptPath = scriptPath,
                scriptArgs = scriptArgTokens.joinToString(" "),
                nodes = findArg("--nodes", "-N") ?: "",
                cpus = findArg("--cpus-per-task", "-c") ?: "",
                memory = findArg("--mem") ?: "",
                gpus = Regex("""--gres=gpu:(\d+)""").find(line)?.groupValues?.get(1) ?: "",
                hours = h,
                minutes = m,
                seconds = s,
            )
        }

        private fun parseTimeParts(time: String?): Triple<String, String, String> {
            if (time.isNullOrBlank()) return Triple("", "", "")
            return try {
                if ('-' in time) {
                    val (days, rest) = time.split('-', limit = 2)
                    val parts = rest.split(':')
                    val h = (days.toLong() * 24 + (parts.getOrNull(0)?.toLongOrNull() ?: 0)).toString()
                    Triple(h, parts.getOrNull(1) ?: "0", parts.getOrNull(2) ?: "0")
                } else {
                    val parts = time.split(':')
                    when (parts.size) {
                        3 -> Triple(parts[0], parts[1], parts[2])
                        2 -> Triple(parts[0], parts[1], "")
                        1 -> Triple(parts[0], "", "")
                        else -> Triple("", "", "")
                    }
                }
            } catch (_: Exception) { Triple("", "", "") }
        }
    }
}
