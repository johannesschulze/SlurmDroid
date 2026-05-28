package org.slurmdroid.nnunet.domain

enum class NnUNetStageStatus {
    Unknown,
    Missing,
    InProgress,
    Complete,
}

data class NnUNetTrainingConfig(
    val configName: String,
    val folds: List<FoldProgress>,
) {
    val completeFolds: Int = folds.count { it.isComplete }
    val totalFolds: Int = 5
    val isComplete: Boolean = completeFolds == totalFolds
    val overallProgress: Float =
        if (folds.isEmpty()) 0f
        else folds.sumOf { it.progressFraction.toDouble() }.toFloat() / totalFolds
}

data class NnUNetWorkflow(
    val rawDataStatus: NnUNetStageStatus = NnUNetStageStatus.Unknown,
    val planningStatus: NnUNetStageStatus = NnUNetStageStatus.Unknown,
    val preprocessingStatus: NnUNetStageStatus = NnUNetStageStatus.Unknown,
    val preprocessingCurrent: Int = 0,
    val preprocessingTotal: Int = 0,
    val trainingConfigs: List<NnUNetTrainingConfig> = emptyList(),
    val postprocessingStatus: NnUNetStageStatus = NnUNetStageStatus.Unknown,
) {
    val preprocessingProgress: Float
        get() = if (preprocessingTotal > 0) preprocessingCurrent.toFloat() / preprocessingTotal else 0f

    val trainingStatus: NnUNetStageStatus get() = when {
        trainingConfigs.isEmpty() -> NnUNetStageStatus.Missing
        trainingConfigs.all { it.isComplete } -> NnUNetStageStatus.Complete
        trainingConfigs.any { it.folds.isNotEmpty() } -> NnUNetStageStatus.InProgress
        else -> NnUNetStageStatus.Missing
    }
}
