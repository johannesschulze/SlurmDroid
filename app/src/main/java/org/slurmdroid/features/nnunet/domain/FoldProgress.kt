package org.slurmdroid.features.nnunet.domain

data class FoldProgress(
    val foldId: Int,
    val epochsDone: Int,
    val totalEpochs: Int = 1000,
    val elapsedSeconds: Double,
) {
    val progressFraction: Float get() = (epochsDone.toFloat() / totalEpochs).coerceIn(0f, 1f)
    val avgSecondsPerEpoch: Double get() = if (epochsDone > 0) elapsedSeconds / epochsDone else 0.0
    val etaSeconds: Double get() = (totalEpochs - epochsDone) * avgSecondsPerEpoch
    val isComplete: Boolean get() = epochsDone >= totalEpochs
    val elapsedHours: Double get() = elapsedSeconds / 3600.0
    val etaHours: Double get() = etaSeconds / 3600.0
}
