package org.slurmdroid.nnunet.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.slurmdroid.nnunet.NnUNetPluginApp
import org.slurmdroid.nnunet.domain.NnUNetStageStatus
import org.slurmdroid.nnunet.domain.NnUNetTrainingConfig
import org.slurmdroid.nnunet.domain.NnUNetWorkflow
import org.slurmdroid.nnunet.ui.NnUNetScaffold

@Composable
fun DatasetDetailScreen(
    datasetName: String,
    onBack: () -> Unit,
    onNavigateToProgress: (configName: String) -> Unit,
    viewModel: DatasetDetailViewModel = run {
        val app = LocalContext.current.applicationContext as NnUNetPluginApp
        viewModel(key = datasetName) { DatasetDetailViewModel(app.plugin, datasetName) }
    },
) {
    val workflow by viewModel.workflow.collectAsStateWithLifecycle()

    NnUNetScaffold(title = datasetName, subtitle = "nnU-Net", onBack = onBack) { padding ->
        when (val w = workflow) {
            null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Waiting for next poll…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    PipelineCard(workflow = w, onNavigateToProgress = onNavigateToProgress)
                }
            }
        }
    }
}

@Composable
private fun PipelineCard(
    workflow: NnUNetWorkflow,
    onNavigateToProgress: (String) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            StageRow(label = "Raw Data", status = workflow.rawDataStatus)
            StageConnector()
            StageRow(label = "Planning", status = workflow.planningStatus)
            StageConnector()
            StageRow(
                label = "Preprocessing",
                status = workflow.preprocessingStatus,
                detail = if (workflow.preprocessingStatus == NnUNetStageStatus.InProgress && workflow.preprocessingTotal > 0)
                    "${workflow.preprocessingCurrent} / ${workflow.preprocessingTotal} cases" else null,
                progress = if (workflow.preprocessingStatus == NnUNetStageStatus.InProgress && workflow.preprocessingTotal > 0)
                    workflow.preprocessingProgress else null,
            )
            StageConnector()
            StageRow(
                label = "Training",
                status = workflow.trainingStatus,
                detail = when {
                    workflow.trainingConfigs.isEmpty() -> null
                    else -> "${workflow.trainingConfigs.sumOf { it.completeFolds }} / " +
                        "${workflow.trainingConfigs.size * 5} folds"
                },
            )
            if (workflow.trainingConfigs.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier.padding(start = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    workflow.trainingConfigs.forEach { config ->
                        ConfigProgressRow(config = config, onClick = { onNavigateToProgress(config.configName) })
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            StageConnector()
            StageRow(label = "Postprocessing", status = workflow.postprocessingStatus)
        }
    }
}

@Composable
private fun StageRow(
    label: String,
    status: NnUNetStageStatus,
    detail: String? = null,
    progress: Float? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusDot(status)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                )
            }
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        StatusChip(status)
    }
}

@Composable
private fun StageConnector() {
    Row(modifier = Modifier.padding(start = 10.dp)) {
        Surface(
            modifier = Modifier.width(2.dp).height(12.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        ) {}
    }
}

@Composable
private fun StatusDot(status: NnUNetStageStatus) {
    val (icon, color) = statusIconAndColor(status)
    Surface(
        modifier = Modifier.size(20.dp),
        shape = CircleShape,
        color = color.copy(alpha = 0.15f),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.padding(3.dp))
    }
}

@Composable
private fun StatusChip(status: NnUNetStageStatus) {
    val label = when (status) {
        NnUNetStageStatus.Unknown -> "—"
        NnUNetStageStatus.Missing -> "Missing"
        NnUNetStageStatus.InProgress -> "In progress"
        NnUNetStageStatus.Complete -> "Done"
    }
    val (_, color) = statusIconAndColor(status)
    Text(label, style = MaterialTheme.typography.labelSmall, color = color)
}

@Composable
private fun statusIconAndColor(status: NnUNetStageStatus): Pair<ImageVector, Color> {
    val primary = MaterialTheme.colorScheme.primary
    val error = MaterialTheme.colorScheme.error
    val outline = MaterialTheme.colorScheme.outline
    return when (status) {
        NnUNetStageStatus.Complete -> Icons.Default.Check to Color(0xFF4CAF50)
        NnUNetStageStatus.InProgress -> Icons.Default.HourglassEmpty to primary
        NnUNetStageStatus.Missing -> Icons.Default.Close to error
        NnUNetStageStatus.Unknown -> Icons.Default.QuestionMark to outline
    }
}

@Composable
private fun ConfigProgressRow(
    config: NnUNetTrainingConfig,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    config.configName,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${config.completeFolds}/${config.totalFolds} folds",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { config.overallProgress },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
            )
        }
    }
}
