package org.slurmdroid.features.slurm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.slurmdroid.features.slurm.domain.JobFormState
import org.slurmdroid.features.slurm.domain.Partition

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmitJobDialog(
    partitions: List<Partition>,
    slurmScripts: List<String> = emptyList(),
    initialState: JobFormState = JobFormState(),
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    val defaultPartition = initialState.partition.ifBlank {
        partitions.firstOrNull { it.isDefault }?.name ?: partitions.firstOrNull()?.name ?: ""
    }

    var selectedPartition by remember { mutableStateOf(defaultPartition) }
    var partitionExpanded by remember { mutableStateOf(false) }
    var jobName by remember { mutableStateOf(initialState.jobName) }
    var command by remember { mutableStateOf(initialState.command) }
    var useScript by remember { mutableStateOf(initialState.scriptPath != null) }
    var selectedScript by remember { mutableStateOf(initialState.scriptPath ?: "") }
    var scriptArgs by remember { mutableStateOf(initialState.scriptArgs) }
    var scriptExpanded by remember { mutableStateOf(false) }
    var nodes by remember { mutableStateOf(initialState.nodes) }
    var cpus by remember { mutableStateOf(initialState.cpus) }
    var memory by remember { mutableStateOf(initialState.memory) }
    var gpus by remember { mutableStateOf(initialState.gpus) }
    var hours by remember { mutableStateOf(initialState.hours) }
    var minutes by remember { mutableStateOf(initialState.minutes) }
    var seconds by remember { mutableStateOf(initialState.seconds) }

    val canSubmit = if (useScript) selectedScript.isNotBlank() else command.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Submit Job") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Partition picker
                ExposedDropdownMenuBox(
                    expanded = partitionExpanded,
                    onExpandedChange = { partitionExpanded = it },
                ) {
                    OutlinedTextField(
                        value = selectedPartition,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Partition") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = partitionExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = partitionExpanded,
                        onDismissRequest = { partitionExpanded = false },
                    ) {
                        partitions.forEach { partition ->
                            DropdownMenuItem(
                                text = {
                                    Text(if (partition.isDefault) "${partition.name} (default)" else partition.name)
                                },
                                onClick = {
                                    selectedPartition = partition.name
                                    partitionExpanded = false
                                },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = jobName,
                    onValueChange = { jobName = it },
                    label = { Text("Job name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Mode toggle: Command vs Script file
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !useScript,
                        onClick = { useScript = false },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        label = { Text("Command") },
                    )
                    SegmentedButton(
                        selected = useScript,
                        onClick = { useScript = true },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        label = { Text("Script file") },
                    )
                }

                if (useScript) {
                    // Script file picker
                    ExposedDropdownMenuBox(
                        expanded = scriptExpanded,
                        onExpandedChange = { scriptExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = selectedScript,
                            onValueChange = { selectedScript = it },
                            label = { Text("Script path") },
                            placeholder = { Text("./run.slurm") },
                            trailingIcon = {
                                if (slurmScripts.isNotEmpty())
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = scriptExpanded)
                            },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryEditable),
                        )
                        if (slurmScripts.isNotEmpty()) {
                            ExposedDropdownMenu(
                                expanded = scriptExpanded,
                                onDismissRequest = { scriptExpanded = false },
                            ) {
                                slurmScripts.forEach { path ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                path,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        },
                                        onClick = {
                                            selectedScript = path
                                            scriptExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                    if (slurmScripts.isEmpty()) {
                        Text(
                            "No .slurm files found — type path manually",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedTextField(
                        value = scriptArgs,
                        onValueChange = { scriptArgs = it },
                        label = { Text("Arguments (optional)") },
                        placeholder = { Text("--epochs 10 --lr 0.001") },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    // Inline command field
                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it },
                        label = { Text("Command") },
                        placeholder = { Text("python train.py") },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = nodes,
                        onValueChange = { nodes = it.filter(Char::isDigit) },
                        label = { Text("Nodes") },
                        placeholder = { Text("1") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = cpus,
                        onValueChange = { cpus = it.filter(Char::isDigit) },
                        label = { Text("CPUs/task") },
                        placeholder = { Text("1") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = memory,
                        onValueChange = { memory = it },
                        label = { Text("Memory") },
                        placeholder = { Text("4G") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = gpus,
                        onValueChange = { gpus = it.filter(Char::isDigit) },
                        label = { Text("GPUs") },
                        placeholder = { Text("0") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }

                // Duration picker: HH : MM : SS
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Time limit", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = hours,
                            onValueChange = { hours = it.filter(Char::isDigit).take(4) },
                            label = { Text("HH") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        Text(":", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = minutes,
                            onValueChange = { v ->
                                val n = v.filter(Char::isDigit).take(2)
                                minutes = if (n.toIntOrNull()?.let { it < 60 } == true || n.isEmpty()) n else "59"
                            },
                            label = { Text("MM") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(80.dp),
                        )
                        Text(":", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = seconds,
                            onValueChange = { v ->
                                val n = v.filter(Char::isDigit).take(2)
                                seconds = if (n.toIntOrNull()?.let { it < 60 } == true || n.isEmpty()) n else "59"
                            },
                            label = { Text("SS") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(80.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val form = JobFormState(
                        partition = selectedPartition,
                        jobName = jobName,
                        command = command.trim(),
                        scriptPath = if (useScript) selectedScript.trim() else null,
                        scriptArgs = if (useScript) scriptArgs.trim() else "",
                        nodes = nodes,
                        cpus = cpus,
                        memory = memory,
                        gpus = gpus,
                        hours = hours,
                        minutes = minutes,
                        seconds = seconds,
                    )
                    onSubmit(form.toSbatchCommand())
                },
                enabled = canSubmit,
            ) { Text("Submit") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
