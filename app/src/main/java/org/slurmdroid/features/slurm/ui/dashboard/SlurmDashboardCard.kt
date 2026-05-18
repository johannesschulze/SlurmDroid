package org.slurmdroid.features.slurm.ui.dashboard

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
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

@Composable
fun SlurmDashboardCard(viewModel: SlurmDashboardViewModel = hiltViewModel()) {
    val partitions by viewModel.partitions.collectAsStateWithLifecycle()
    val jobs by viewModel.jobs.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Slurm", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        if (partitions.isNotEmpty()) {
            Text("Partitions", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(partitions) { PartitionChip(it) }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (jobs.isNotEmpty()) {
            Text("Active Jobs (${jobs.size})", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            jobs.take(3).forEach { CompactJobRow(it) }
            if (jobs.size > 3) {
                Text("+ ${jobs.size - 3} more…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp))
            }
        } else {
            Text("No active jobs", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PartitionChip(partition: Partition) {
    val dotColor = when {
        !partition.isUp -> MaterialTheme.colorScheme.error
        partition.nodesAvailable > 0 -> Color(0xFF4CAF50)   // green
        else -> Color(0xFFFFA726)                             // orange — up but all busy
    }
    Card {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(8.dp).clip(CircleShape).background(dotColor)
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(partition.name, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold)
                val nodeText = if (partition.isTotalKnown)
                    "${partition.nodesAvailable}/${partition.nodesTotal} nodes idle"
                else
                    "${partition.nodesAvailable} nodes idle"
                Text(nodeText, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (partition.cpusTotal > 0) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { partition.cpuUsageFraction },
                        modifier = Modifier.width(100.dp).height(4.dp).clip(CircleShape),
                    )
                }
            }
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
