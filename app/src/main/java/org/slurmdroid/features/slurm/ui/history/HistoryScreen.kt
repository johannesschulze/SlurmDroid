package org.slurmdroid.features.slurm.ui.history

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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.slurmdroid.core.db.entities.JobHistory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val history by viewModel.history.collectAsStateWithLifecycle(initialValue = emptyList())
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var resubmitJob by remember { mutableStateOf<JobHistory?>(null) }

    resubmitJob?.let { job ->
        ResubmitDialog(
            initialCommand = job.fullCommand,
            onDismiss = { resubmitJob = null },
            onSubmit = { command ->
                viewModel.resubmit(command) { _, msg ->
                    scope.launch { snackbar.showSnackbar(msg) }
                }
                resubmitJob = null
            },
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Job History") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (history.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("No job history yet",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(history, key = { it.id }) { job ->
                    HistoryCard(job, onResubmit = { resubmitJob = job })
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(job: JobHistory, onResubmit: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
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
                Text(
                    job.lastKnownStatus,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val dateStr = remember(job.timestamp) {
                SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault()).format(Date(job.timestamp))
            }
            Text(
                buildString {
                    append(dateStr)
                    if (job.partition.isNotBlank()) append(" · ${job.partition}")
                    if (job.slurmJobId != null) append(" · #${job.slurmJobId}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = onResubmit,
                modifier = Modifier.align(Alignment.End),
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 4.dp).size(16.dp),
                )
                Text("Re-submit")
            }
        }
    }
}

@Composable
private fun ResubmitDialog(
    initialCommand: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var command by remember { mutableStateOf(initialCommand) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Re-submit job") },
        text = {
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                label = { Text("Command") },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                minLines = 3,
                maxLines = 6,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(command) },
                enabled = command.isNotBlank(),
            ) {
                Text("Submit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
