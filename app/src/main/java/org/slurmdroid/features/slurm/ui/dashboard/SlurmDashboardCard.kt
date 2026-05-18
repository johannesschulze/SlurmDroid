package org.slurmdroid.features.slurm.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import org.slurmdroid.ui.main.LocalNavController

@Composable
fun SlurmDashboardCard(viewModel: SlurmDashboardViewModel = hiltViewModel()) {
    val partitions by viewModel.partitions.collectAsStateWithLifecycle()
    val jobs by viewModel.jobs.collectAsStateWithLifecycle()
    val navController = LocalNavController.current

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Slurm", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        if (partitions.isNotEmpty()) {
            Text(
                "Partitions",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            PartitionTable(partitions)
            Spacer(Modifier.height(16.dp))
        }

        Column(modifier = Modifier.fillMaxWidth().clickable { navController.navigate("slurm/jobs") }) {
            Text(
                "Active Jobs (${jobs.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (jobs.isNotEmpty()) {
                jobs.take(3).forEach { CompactJobRow(it) }
                if (jobs.size > 3) {
                    Text(
                        "+ ${jobs.size - 3} more…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            } else {
                Text(
                    "No active jobs",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PartitionTable(partitions: List<Partition>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Text(
                "Partition",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(2f),
            )
            Text(
                "Nodes",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1.5f),
            )
            Text(
                "CPUs",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(2f),
            )
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
        Text(
            nodeText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1.5f),
        )

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
private fun CompactJobRow(job: SlurmJob) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(job.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(
                job.jobId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (job.isRunning && job.progressFraction != null) {
            LinearProgressIndicator(
                progress = { job.progressFraction!! },
                modifier = Modifier.width(64.dp).height(4.dp).clip(CircleShape),
            )
        } else {
            Text(
                job.state,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
