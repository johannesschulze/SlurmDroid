package org.slurmdroid.features.slurm.ui.detail

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.slurmdroid.features.slurm.domain.JobDetail
import org.slurmdroid.features.slurm.ui.detail.JobDetailViewModel.UiState
import org.slurmdroid.ui.main.LocalNavController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobDetailScreen(viewModel: JobDetailViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = LocalNavController.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Job Details") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = uiState) {
                is UiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is UiState.Error -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = viewModel::retry) { Text("Retry") }
                }
                is UiState.Success -> DetailContent(s.detail)
            }
        }
    }
}

@Composable
private fun DetailContent(d: JobDetail) {
    val isRunning = d.state == "RUNNING"
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(d.jobName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        d.state,
                        style = MaterialTheme.typography.labelMedium,
                        color = stateColor(d.state),
                    )
                    Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("#${d.jobId}", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // ── Timing ──────────────────────────────────────────────────────────
        item {
            Section("Timing") {
                if (d.startTime != null) {
                    DetailRow("Started", formatTimestamp(d.startTime))
                }
                if (d.endTime != null) {
                    DetailRow("Ended", formatTimestamp(d.endTime))
                }
                if (d.elapsed.isNotBlank()) {
                    DetailRow("Elapsed", d.elapsed)
                }
                if (d.timeLimit.isNotBlank() && d.timeLimit != "UNLIMITED") {
                    DetailRow("Time limit", d.timeLimit)
                }
                if (isRunning && d.timeLimit.isNotBlank() && d.timeLimit != "UNLIMITED") {
                    val fraction = remember(d.elapsed, d.timeLimit) {
                        parseElapsedFraction(d.elapsed, d.timeLimit)
                    }
                    if (fraction != null) {
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                        )
                    }
                }
            }
        }

        // ── Resources ───────────────────────────────────────────────────────
        item {
            Section("Resources") {
                DetailRow("Partition", d.partition)
                if (d.nodeList.isNotBlank()) DetailRow("Nodes", d.nodeList)
                if (d.nCpus != null) {
                    val cpuText = if (d.nNodes != null && d.nNodes > 1)
                        "${d.nCpus} (${d.nNodes} nodes)" else "${d.nCpus}"
                    DetailRow("CPUs", cpuText)
                }
                if (d.requestedMemory != null) DetailRow("Requested memory", d.requestedMemory)
            }
        }

        // ── Live stats (sstat, running only) ─────────────────────────────────
        if (d.avgCpu != null || d.maxRss != null || d.nTasks != null) {
            item {
                Section("Resource usage") {
                    if (d.avgCpu != null) DetailRow("Avg CPU time", d.avgCpu)
                    if (d.maxRss != null) DetailRow("Max RSS", d.maxRss)
                    if (d.nTasks != null) DetailRow("Tasks", "${d.nTasks}")
                }
            }
        }

        // ── Outcome (completed jobs) ──────────────────────────────────────────
        if (d.exitCode != null || d.maxRss != null && !isRunning) {
            item {
                Section("Outcome") {
                    if (d.exitCode != null) DetailRow("Exit code", d.exitCode)
                    if (d.maxRss != null) DetailRow("Max RSS", d.maxRss)
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        HorizontalDivider()
        content()
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun stateColor(state: String): Color = when (state.uppercase()) {
    "COMPLETED"                              -> Color(0xFF4CAF50)
    "FAILED", "TIMEOUT", "OUT_OF_MEMORY",
    "NODE_FAIL"                              -> MaterialTheme.colorScheme.error
    "CANCELLED"                              -> Color(0xFFFFA726)
    "RUNNING"                                -> MaterialTheme.colorScheme.primary
    else                                     -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun formatTimestamp(ms: Long): String =
    SimpleDateFormat("dd.MM.yy HH:mm:ss", Locale.getDefault()).format(Date(ms))

/** Parses "H:MM:SS" or "D-HH:MM:SS" → seconds. */
private fun parseTimeToSeconds(s: String): Long? {
    val clean = s.trim()
    return try {
        if ('-' in clean) {
            val (days, time) = clean.split('-')
            val parts = time.split(':').map { it.toLong() }
            days.toLong() * 86400 + parts[0] * 3600 + parts[1] * 60 + parts[2]
        } else {
            val parts = clean.split(':').map { it.toLong() }
            when (parts.size) {
                3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
                2 -> parts[0] * 60 + parts[1]
                else -> null
            }
        }
    } catch (_: Exception) { null }
}

private fun parseElapsedFraction(elapsed: String, limit: String): Float? {
    val e = parseTimeToSeconds(elapsed) ?: return null
    val l = parseTimeToSeconds(limit) ?: return null
    if (l <= 0) return null
    return (e.toFloat() / l).coerceIn(0f, 1f)
}
