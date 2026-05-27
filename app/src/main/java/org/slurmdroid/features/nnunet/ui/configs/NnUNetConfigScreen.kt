package org.slurmdroid.features.nnunet.ui.configs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.slurmdroid.features.nnunet.ui.NnUNetScaffold

@Composable
fun NnUNetConfigScreen(
    onBack: () -> Unit,
    onNavigateToProgress: (configName: String) -> Unit,
    viewModel: NnUNetConfigViewModel = hiltViewModel(),
) {
    val configs by viewModel.configs.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    // Auto-navigate when there is exactly one config
    LaunchedEffect(isLoading, configs) {
        if (!isLoading && configs.size == 1) onNavigateToProgress(configs.first())
    }

    NnUNetScaffold(
        title = viewModel.datasetName,
        subtitle = "nnU-Net",
        onBack = onBack,
    ) { padding ->
        when {
            isLoading || (!isLoading && configs.size == 1) -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            configs.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No training configurations found for\n${viewModel.datasetName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 8.dp,
                ),
            ) {
                items(configs) { config ->
                    ListItem(
                        headlineContent = { Text(config, maxLines = 1) },
                        modifier = Modifier.clickable { onNavigateToProgress(config) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
