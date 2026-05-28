package org.slurmdroid.nnunet.data

import org.slurmdroid.nnunet.domain.EpochMetrics
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

    /** Parses per-epoch metrics (train_loss / val_loss / pseudo dice) from a single fold's log output. */
    fun parseFoldMetrics(output: String): List<EpochMetrics> {
        val epochMap = mutableMapOf<Int, EpochData>()
        var currentEpoch: Int? = null

        for (line in output.lineSequence()) {
            val trimmed = line.trim()

            val epochStart = EPOCH_START.find(trimmed)
            if (epochStart != null) {
                currentEpoch = epochStart.groupValues[1].toInt()
                continue
            }
            if (currentEpoch == null) continue
            val data = epochMap.getOrPut(currentEpoch!!) { EpochData() }

            TRAIN_LOSS.find(trimmed)?.let { data.trainLoss = it.groupValues[1].toFloatOrNull() }
            VAL_LOSS.find(trimmed)?.let { data.valLoss = it.groupValues[1].toFloatOrNull() }
            PSEUDO_DICE.find(trimmed)?.let { data.pseudoDice = parseMeanDice(it.groupValues[1]) }
        }

        return epochMap.entries
            .sortedBy { it.key }
            .map { (epoch, data) ->
                EpochMetrics(
                    epoch = epoch,
                    trainLoss = data.trainLoss,
                    valLoss = data.valLoss,
                    pseudoDice = data.pseudoDice,
                )
            }
    }

    private data class EpochData(
        var trainLoss: Float? = null,
        var valLoss: Float? = null,
        var pseudoDice: Float? = null,
    )

    private fun parseFolds(output: String, totalEpochs: Int): List<FoldProgress> {
        val epochTimesByFold = mutableMapOf<Int, MutableMap<Int, Double>>()
        val runningFolds = mutableSetOf<Int>()
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

            if (trimmed == "RUNNING") {
                runningFolds.add(currentFold!!)
                continue
            }

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
                    isRunning = fold in runningFolds,
                )
            }
    }

    private fun parseMeanDice(raw: String): Float? {
        val values = raw.trim().removeSurrounding("[", "]")
            .split(",")
            .mapNotNull { it.trim().toFloatOrNull() }
        return if (values.isEmpty()) null else values.average().toFloat()
    }

    companion object {
        private val CONFIG_MARKER = Regex("""^---CONFIG (.+)---$""")
        private val FOLD_MARKER = Regex("""^---FOLD (\d+)---$""")
        private val EPOCH_START = Regex("""Epoch (\d+)\s*$""")
        private val EPOCH_TIME = Regex("""Epoch time:\s+([\d.]+)\s+s""")
        private val TRAIN_LOSS = Regex("""train_loss\s+([-\d.]+)""")
        private val VAL_LOSS = Regex("""val_loss\s+([-\d.]+)""")
        private val PSEUDO_DICE = Regex("""Pseudo dice\s+(\[.+\])""")
    }
}
