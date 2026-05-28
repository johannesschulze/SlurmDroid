package org.slurmdroid.nnunet.ui.fold

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.slurmdroid.nnunet.NnUNetPluginApp
import org.slurmdroid.nnunet.domain.EpochMetrics
import org.slurmdroid.nnunet.domain.FoldProgress
import org.slurmdroid.nnunet.domain.parseDatasetName
import org.slurmdroid.nnunet.ui.NnUNetScaffold

@Composable
fun FoldDetailScreen(
    datasetName: String,
    configName: String,
    foldId: Int,
    onBack: () -> Unit,
    viewModel: FoldDetailViewModel = run {
        val app = LocalContext.current.applicationContext as NnUNetPluginApp
        viewModel(key = "$datasetName/$configName/fold$foldId") {
            FoldDetailViewModel(app.plugin, datasetName, configName, foldId)
        }
    },
) {
    val fold by viewModel.fold.collectAsStateWithLifecycle()
    val epochMetrics by viewModel.epochMetrics.collectAsStateWithLifecycle()
    val lastPollError by viewModel.lastPollError.collectAsStateWithLifecycle()

    val (_, humanName) = parseDatasetName(datasetName)
    val subtitle = buildString {
        if (humanName.isNotEmpty()) append("$humanName · ")
        append(configName)
    }

    NnUNetScaffold(title = "Fold $foldId", subtitle = subtitle, onBack = onBack) { padding ->
        when (val f = fold) {
            null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                if (lastPollError != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Text("Error loading fold data", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            lastPollError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
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
            }
            else -> FoldDetailContent(fold = f, epochMetrics = epochMetrics, padding = padding)
        }
    }
}

@Composable
private fun FoldDetailContent(
    fold: FoldProgress,
    epochMetrics: List<EpochMetrics>?,
    padding: PaddingValues,
) {
    val displayed = epochMetrics?.let { if (it.size <= 100) it else it.takeLast(100) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SummaryCard(fold, epochMetrics) }

        when {
            epochMetrics == null -> item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Loading metrics…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            epochMetrics.size >= 2 -> item { LossChartCard(epochMetrics) }
        }

        if (displayed != null && displayed.isNotEmpty()) {
            item {
                val label = if ((epochMetrics?.size ?: 0) > 100) "Last 100 epochs" else "Per-epoch metrics"
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(displayed, key = { it.epoch }) { m -> EpochRow(m) }
        }
    }
}

@Composable
private fun SummaryCard(fold: FoldProgress, epochMetrics: List<EpochMetrics>?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${fold.epochsDone} / ${fold.totalEpochs} epochs",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                when {
                    fold.isRunning && !fold.isComplete -> Text(
                        "Running",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    fold.isComplete -> Text(
                        "Complete",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { fold.progressFraction },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "%.1fh elapsed".format(fold.elapsedHours),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!fold.isComplete && fold.etaHours > 0) {
                    Text(
                        "~%.0fh remaining".format(fold.etaHours),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val bestEpoch = epochMetrics?.minByOrNull { it.valLoss ?: Float.MAX_VALUE }
            if (bestEpoch?.valLoss != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Best val loss %.4f at epoch ${bestEpoch.epoch}".format(bestEpoch.valLoss),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LossChartCard(metrics: List<EpochMetrics>) {
    val trainColor = MaterialTheme.colorScheme.primary
    val valColor = MaterialTheme.colorScheme.tertiary

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)) {
            Text("Loss curve", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot(trainColor, "Train")
                LegendDot(valColor, "Val")
            }
            Spacer(Modifier.height(8.dp))

            val allLosses = metrics.flatMap { listOfNotNull(it.trainLoss, it.valLoss) }
            if (allLosses.isEmpty()) return@Column
            val minLoss = allLosses.min()
            val maxLoss = allLosses.max()
            val range = (maxLoss - minLoss).coerceAtLeast(1e-6f)
            val n = metrics.size

            Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                val w = size.width
                val h = size.height
                fun xOf(i: Int) = if (n > 1) i.toFloat() / (n - 1) * w else w / 2f
                fun yOf(v: Float) = h - (v - minLoss) / range * h

                val trainPts = metrics.mapIndexedNotNull { i, m -> m.trainLoss?.let { Offset(xOf(i), yOf(it)) } }
                for (i in 1 until trainPts.size) {
                    drawLine(trainColor.copy(alpha = 0.8f), trainPts[i - 1], trainPts[i], 2.dp.toPx(), StrokeCap.Round)
                }
                val valPts = metrics.mapIndexedNotNull { i, m -> m.valLoss?.let { Offset(xOf(i), yOf(it)) } }
                for (i in 1 until valPts.size) {
                    drawLine(valColor.copy(alpha = 0.8f), valPts[i - 1], valPts[i], 2.dp.toPx(), StrokeCap.Round)
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EpochRow(m: EpochMetrics) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Ep ${m.epoch}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(52.dp),
        )
        Text(
            m.trainLoss?.let { "T: %.4f".format(it) } ?: "T: —",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            m.valLoss?.let { "V: %.4f".format(it) } ?: "V: —",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            m.pseudoDice?.let { "D: %.3f".format(it) } ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}
