package org.slurmdroid.nnunet.ui.datasets

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import org.slurmdroid.nnunet.NnUNetPluginApp
import org.slurmdroid.nnunet.ui.NnUNetScaffold
import org.slurmdroid.plugin.api.PluginContract

@OptIn(ExperimentalMaterial3Api::class)
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
    val lastPollTime by viewModel.lastPollTime.collectAsStateWithLifecycle()
    val debugInfo by viewModel.debugInfo.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(lastPollTime) { isRefreshing = false }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) { delay(30_000L); isRefreshing = false }
    }

    NnUNetScaffold(title = "nnU-Net") { padding ->
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
            if (datasets.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(padding)
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        when (anyDirConfigured) {
                            null -> "Connecting…"
                            false -> "No nnUNet directories configured.\n\nSet the paths in SlurmDroid → Settings → nnU-Net."
                            true -> "No datasets found."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    if (anyDirConfigured == true && debugInfo != null) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            debugInfo!!,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
}
