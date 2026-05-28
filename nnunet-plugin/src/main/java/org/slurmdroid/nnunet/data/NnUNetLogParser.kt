package org.slurmdroid.nnunet.data

import org.slurmdroid.nnunet.domain.FoldProgress

class NnUNetLogParser {

    fun parseMultiConfig(output: String, totalEpochs: Int = 1000): Map<String, List<FoldProgress>> {
        val result = mutableMapOf<String, List<FoldProgress>>()
        var currentConfig: String? = null
        val currentBlock = StringBuilder()

        fun flushConfig() {
            val config = currentConfig ?: return
            result[config] = parseFolds(currentBlock.toString(), totalEpochs)
            currentBlock.clear()
        }

        for (line in output.lineSequence()) {
            val configMarker = CONFIG_MARKER.matchEntire(line.trim())
            if (configMarker != null) {
                flushConfig()
                currentConfig = configMarker.groupValues[1]
                continue
            }
            if (currentConfig != null) currentBlock.appendLine(line)
        }
        flushConfig()
        return result
    }

    fun parse(output: String, totalEpochs: Int = 1000): List<FoldProgress> =
        parseFolds(output, totalEpochs)

    private fun parseFolds(output: String, totalEpochs: Int): List<FoldProgress> {
        val epochTimesByFold = mutableMapOf<Int, MutableMap<Int, Double>>()
        var currentFold: Int? = null
        var currentEpoch: Int? = null

        for (line in output.lineSequence()) {
            val trimmed = line.trim()

            val foldMarker = FOLD_MARKER.matchEntire(trimmed)
            if (foldMarker != null) {
                currentFold = foldMarker.groupValues[1].toInt()
                currentEpoch = null
                continue
            }

            if (currentFold == null) continue

            val epochStart = EPOCH_START.find(trimmed)
            if (epochStart != null) {
                currentEpoch = epochStart.groupValues[1].toInt()
                continue
            }

            val epochTime = EPOCH_TIME.find(trimmed)
            if (epochTime != null && currentEpoch != null) {
                epochTimesByFold
                    .getOrPut(currentFold!!) { mutableMapOf() }[currentEpoch!!] =
                    epochTime.groupValues[1].toDouble()
                currentEpoch = null
            }
        }

        return epochTimesByFold.entries
            .sortedBy { it.key }
            .map { (fold, times) ->
                FoldProgress(
                    foldId = fold,
                    epochsDone = times.size,
                    totalEpochs = totalEpochs,
                    elapsedSeconds = times.values.sum(),
                )
            }
    }

    companion object {
        private val CONFIG_MARKER = Regex("""^---CONFIG (.+)---$""")
        private val FOLD_MARKER = Regex("""^---FOLD (\d+)---$""")
        private val EPOCH_START = Regex("""Epoch (\d+)\s*$""")
        private val EPOCH_TIME = Regex("""Epoch time:\s+([\d.]+)\s+s""")
    }
}
