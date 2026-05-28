package org.slurmdroid.nnunet.ui.progress

import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import org.slurmdroid.nnunet.NnUNetPluginApp
import org.slurmdroid.nnunet.domain.FoldProgress
import org.slurmdroid.nnunet.domain.parseDatasetName
import org.slurmdroid.nnunet.ui.NnUNetScaffold
import org.slurmdroid.plugin.api.PluginContract

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    datasetName: String,
    configName: String,
    onBack: () -> Unit,
    viewModel: ProgressViewModel = run {
        val app = LocalContext.current.applicationContext as NnUNetPluginApp
        viewModel(key = "$datasetName/$configName") {
            ProgressViewModel(app.plugin, datasetName, configName)
        }
    },
) {
    val folds by viewModel.folds.collectAsStateWithLifecycle()
    val lastPollTime by viewModel.lastPollTime.collectAsStateWithLifecycle()
    val lastPollError by viewModel.lastPollError.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(lastPollTime) { isRefreshing = false }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) { delay(30_000L); isRefreshing = false }
    }

    val (datasetId, humanName) = parseDatasetName(datasetName)
    val scaffoldTitle = if (humanName.isNotEmpty()) humanName else datasetName
    val scaffoldSubtitle = if (datasetId.isNotEmpty()) "Dataset $datasetId · nnU-Net" else "nnU-Net"
    NnUNetScaffold(title = scaffoldTitle, subtitle = scaffoldSubtitle, onBack = onBack) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                context.sendBroadcast(
                    Intent(PluginContract.ACTION_REQUEST_POLL)
                        .setPackage(PluginContract.SLURMDROID_PACKAGE)
                )
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            when (val f = folds) {
                null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    if (lastPollError != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp),
                        ) {
                            Text(
                                "Error loading training progress",
                                style = MaterialTheme.typography.titleSmall,
                            )
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
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = padding.calculateTopPadding() + 8.dp,
                        bottom = padding.calculateBottomPadding() + 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text(
                            configName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    items(f, key = { it.foldId }) { fold ->
                        FoldProgressCard(fold)
                    }
                    if (f.size > 1) {
                        item { SummaryCard(f) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FoldProgressCard(fold: FoldProgress) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Fold ${fold.foldId}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${fold.epochsDone} / ${fold.totalEpochs} epochs",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { fold.progressFraction },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${(fold.progressFraction * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "%.1fh elapsed".format(fold.elapsedHours),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    when {
                        fold.isComplete -> Text(
                            "Complete",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                        fold.etaHours > 0 -> Text(
                            "~%.0fh left".format(fold.etaHours),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(folds: List<FoldProgress>) {
    val totalDone = folds.sumOf { it.epochsDone }
    val totalPossible = folds.sumOf { it.totalEpochs }
    val overallFraction = if (totalPossible > 0) totalDone.toFloat() / totalPossible else 0f
    val totalElapsedH = folds.sumOf { it.elapsedHours }
    val longestEtaH = folds.maxOfOrNull { it.etaHours } ?: 0.0
    val completeFolds = folds.count { it.isComplete }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Overall  •  $completeFolds / ${folds.size} folds complete",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { overallFraction },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${(overallFraction * 100).toInt()}% overall",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "%.1fh total".format(totalElapsedH),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (longestEtaH > 0) {
                        Text(
                            "~%.0fh max left".format(longestEtaH),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
