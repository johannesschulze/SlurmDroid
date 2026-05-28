package org.slurmdroid.features.slurm.ui.overview

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import org.slurmdroid.ui.main.LocalOpenDrawer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.slurmdroid.features.slurm.domain.Partition
import org.slurmdroid.features.slurm.domain.SlurmJob

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlurmOverviewScreen(
    onNavigateToJobs: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    viewModel: SlurmOverviewViewModel = hiltViewModel(),
) {
    val partitions by viewModel.partitions.collectAsStateWithLifecycle()
    val jobs by viewModel.jobs.collectAsStateWithLifecycle()
    val pollError by viewModel.pollError.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val pollCountdown by viewModel.pollCountdown.collectAsStateWithLifecycle()
    val openDrawer = LocalOpenDrawer.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Slurm") },
                navigationIcon = {
                    IconButton(onClick = openDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    CircularProgressIndicator(
                        progress = { pollCountdown },
                        modifier = Modifier.padding(end = 16.dp).size(20.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    )
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                pollError?.let { error ->
                    item {
                        Text(
                            error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                if (partitions.isNotEmpty()) {
                    item {
                        SectionLabel("Partitions")
                        Spacer(Modifier.height(8.dp))
                        PartitionTable(partitions)
                    }
                }

                item {
                    SectionLabel("Active Jobs (${jobs.size})")
                    Spacer(Modifier.height(8.dp))
                    if (jobs.isEmpty()) {
                        Text(
                            "No active jobs",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            jobs.forEach { job ->
                                CompactJobRow(
                                    job = job,
                                    onClick = { onNavigateToDetail(job.jobId) },
                                )
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = onNavigateToJobs,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.List,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Manage Jobs")
                        }
                        OutlinedButton(
                            onClick = onNavigateToHistory,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("History")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PartitionTable(partitions: List<Partition>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Text("Partition", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(2f))
            Text("Nodes", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1.5f))
            Text("CPUs", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(2f))
        }
        HorizontalDivider()
        partitions.forEach { PartitionRow(it) }
    }
}

@Composable
private fun PartitionRow(partition: Partition) {
    val dotColor = when {
        !partition.isUp -> MaterialTheme.colorScheme.error
        partition.nodesAvailable > 0 -> Color(0xFF4CAF50)
        else -> Color(0xFFFFA726)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(2f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(dotColor))
            Text(
                if (partition.isDefault) "${partition.name}*" else partition.name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
        val nodeText = when {
            !partition.isUp -> "—"
            partition.isTotalKnown -> "${partition.nodesAvailable} / ${partition.nodesTotal}"
            else -> "${partition.nodesAvailable}"
        }
        Text(nodeText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.5f))
        if (partition.cpusTotal > 0 && partition.isUp) {
            Row(
                modifier = Modifier.weight(2f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LinearProgressIndicator(
                    progress = { partition.cpuUsageFraction },
                    modifier = Modifier.weight(1f).height(4.dp).clip(CircleShape),
                )
                Text(
                    "${(partition.cpuUsageFraction * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Text("—", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(2f))
        }
    }
}

@Composable
private fun CompactJobRow(job: SlurmJob, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(job.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(job.jobId, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        if (job.isRunning && job.progressFraction != null) {
            LinearProgressIndicator(
                progress = { job.progressFraction!! },
                modifier = Modifier.width(64.dp).height(4.dp).clip(CircleShape),
            )
        } else {
            Text(job.state, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
