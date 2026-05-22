package org.slurmdroid.features.slurm.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.slurmdroid.features.slurm.domain.HistoryItem
import org.slurmdroid.features.slurm.domain.JobFormState
import org.slurmdroid.features.slurm.domain.SacctJob
import org.slurmdroid.features.slurm.domain.timestamp
import org.slurmdroid.features.slurm.ui.SubmitJobDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    onNavigateToDetail: (String) -> Unit = {},
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val partitions by viewModel.partitions.collectAsStateWithLifecycle()
    val slurmScripts by viewModel.slurmScripts.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var submitInitialState by remember { mutableStateOf<JobFormState?>(null) }

    val grouped = remember(history) {
        val fmt = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
        val todayStr = fmt.format(Date())
        val yesterdayStr = fmt.format(Date(System.currentTimeMillis() - 86_400_000L))
        history.groupBy { item ->
            val raw = item.timestamp()?.let { fmt.format(Date(it)) } ?: "Unknown"
            when (raw) {
                todayStr -> "Today"
                yesterdayStr -> "Yesterday"
                else -> raw
            }
        }
    }

    LaunchedEffect(Unit) { viewModel.refresh() }

    submitInitialState?.let { initial ->
        SubmitJobDialog(
            partitions = partitions,
            slurmScripts = slurmScripts,
            initialState = initial,
            onDismiss = { submitInitialState = null },
            onSubmit = { command ->
                submitInitialState = null
                viewModel.resubmit(command) { _, msg ->
                    scope.launch { snackbar.showSnackbar(msg) }
                }
            },
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Job History") }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                viewModel.refreshSlurmScripts()
                submitInitialState = viewModel.lastFormState()
            }) {
                Icon(Icons.Default.Add, contentDescription = "Submit job")
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            if (history.isEmpty() && !isRefreshing) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No job history", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    grouped.forEach { (dateLabel, items) ->
                        stickyHeader(key = "header_$dateLabel") {
                            Text(
                                dateLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(vertical = 8.dp),
                            )
                        }
                        items(
                            items,
                            key = { item ->
                                when (item) {
                                    is HistoryItem.Single -> "s_${item.job.jobId}"
                                    is HistoryItem.ArrayGroup -> "g_${item.parentJobId}"
                                }
                            },
                        ) { item ->
                            when (item) {
                                is HistoryItem.Single -> HistoryCard(
                                    job = item.job,
                                    onClick = { onNavigateToDetail(item.job.jobId) },
                                    onResubmit = {
                                        viewModel.refreshSlurmScripts()
                                        submitInitialState = item.job.fullCommand
                                            ?.let { JobFormState.fromSbatchCommand(it) }
                                            ?: viewModel.lastFormState()
                                    },
                                )
                                is HistoryItem.ArrayGroup -> ArrayGroupCard(
                                    group = item,
                                    onClickParent = { onNavigateToDetail(item.parentJobId) },
                                    onClickChild = onNavigateToDetail,
                                    onResubmit = {
                                        viewModel.refreshSlurmScripts()
                                        submitInitialState = item.fullCommand
                                            ?.let { JobFormState.fromSbatchCommand(it) }
                                            ?: viewModel.lastFormState()
                                    },
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(job: SacctJob, onClick: () -> Unit = {}, onResubmit: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    job.jobName,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                Text(job.state, style = MaterialTheme.typography.labelSmall, color = stateColor(job.state))
            }
            val dateStr = remember(job.startTimestamp) {
                job.startTimestamp?.let {
                    SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault()).format(Date(it))
                } ?: "—"
            }
            Text(
                buildString {
                    append(dateStr)
                    if (job.partition.isNotBlank()) append(" · ${job.partition}")
                    append(" · #${job.jobId}")
                    if (job.elapsed.isNotBlank() && job.elapsed != "00:00:00") append(" · ${job.elapsed}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (job.fullCommand != null) {
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = onResubmit, modifier = Modifier.align(Alignment.End)) {
                    Icon(Icons.Default.Refresh, contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp).size(16.dp))
                    Text("Re-submit")
                }
            }
        }
    }
}

@Composable
private fun ArrayGroupCard(
    group: HistoryItem.ArrayGroup,
    onClickParent: () -> Unit,
    onClickChild: (String) -> Unit,
    onResubmit: () -> Unit,
) {
    val dateStr = remember(group.startTimestamp) {
        group.startTimestamp?.let {
            SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault()).format(Date(it))
        } ?: "—"
    }
    // Summarise child states for the header badge
    val stateSummary = remember(group.children) {
        group.children.groupingBy { it.state }.eachCount()
            .entries.sortedByDescending { it.value }
            .joinToString(" · ") { (s, n) -> if (n == 1) s else "$n×$s" }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // ── Parent header ──────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClickParent),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(group.jobName, style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f), maxLines = 1)
                    Text("${group.children.size} tasks",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    buildString {
                        append(dateStr)
                        if (group.partition.isNotBlank()) append(" · ${group.partition}")
                        append(" · #${group.parentJobId}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(stateSummary, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (group.fullCommand != null) {
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = onResubmit, modifier = Modifier.align(Alignment.End)) {
                    Icon(Icons.Default.Refresh, contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp).size(16.dp))
                    Text("Re-submit")
                }
            }

            // ── Child tasks ────────────────────────────────────────────────────
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            group.children.forEach { child ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onClickChild(child.jobId) }
                        .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "#${child.jobId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (child.elapsed.isNotBlank() && child.elapsed != "00:00:00") {
                            Text(child.elapsed, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(child.state, style = MaterialTheme.typography.labelSmall,
                            color = stateColor(child.state))
                    }
                }
            }
        }
    }
}

@Composable
private fun stateColor(state: String): Color = when (state.uppercase()) {
    "COMPLETED"                    -> Color(0xFF4CAF50)
    "FAILED", "TIMEOUT",
    "OUT_OF_MEMORY", "NODE_FAIL"   -> MaterialTheme.colorScheme.error
    "CANCELLED"                    -> Color(0xFFFFA726)
    "RUNNING"                      -> MaterialTheme.colorScheme.primary
    else                           -> MaterialTheme.colorScheme.onSurfaceVariant
}
