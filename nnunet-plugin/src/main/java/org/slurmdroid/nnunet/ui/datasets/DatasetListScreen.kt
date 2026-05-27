package org.slurmdroid.nnunet.ui.datasets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.slurmdroid.nnunet.NnUNetPluginApp
import org.slurmdroid.nnunet.ui.NnUNetScaffold

@Composable
fun DatasetListScreen(
    onNavigateToDataset: (datasetName: String) -> Unit,
    viewModel: DatasetListViewModel = run {
        val app = LocalContext.current.applicationContext as NnUNetPluginApp
        viewModel { DatasetListViewModel(app.plugin) }
    },
) {
    val datasets by viewModel.datasets.collectAsStateWithLifecycle()
    val anyDirConfigured by viewModel.anyDirConfigured.collectAsStateWithLifecycle()

    NnUNetScaffold(title = "nnU-Net") { padding ->
        if (datasets.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when (anyDirConfigured) {
                        null -> "Connecting…"
                        false -> "No nnUNet directories configured.\n\nSet the paths in SlurmDroid → Settings → nnU-Net."
                        true -> "No datasets found.\n\nMake sure nnUNet_raw, nnUNet_preprocessed, or nnUNet_results contain dataset folders."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 8.dp,
                ),
            ) {
                items(datasets) { dataset ->
                    ListItem(
                        headlineContent = {
                            Text(dataset.name, fontWeight = FontWeight.Medium, maxLines = 1)
                        },
                        modifier = Modifier.clickable { onNavigateToDataset(dataset.name) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
