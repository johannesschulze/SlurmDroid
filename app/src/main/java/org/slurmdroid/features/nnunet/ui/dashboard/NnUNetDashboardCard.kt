package org.slurmdroid.features.nnunet.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.slurmdroid.ui.main.LocalNavController

@Composable
fun NnUNetDashboardCard(viewModel: NnUNetDashboardViewModel = hiltViewModel()) {
    val datasets by viewModel.datasets.collectAsStateWithLifecycle()
    val resolvedResultsDir by viewModel.resolvedResultsDir.collectAsStateWithLifecycle()
    val navController = LocalNavController.current

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("nnU-Net", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        when {
            resolvedResultsDir == null && datasets.isEmpty() -> {
                Text(
                    "nnUNet_results not set on server.\nConfigure the path in Settings → nnU-Net.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            datasets.isEmpty() -> {
                Text(
                    "No datasets found in $resolvedResultsDir",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                Column(
                    modifier = Modifier.fillMaxWidth().clickable {
                        navController.navigate("nnunet/datasets") {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                ) {
                    Text(
                        "${datasets.size} dataset${if (datasets.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    datasets.take(3).forEach { dataset ->
                        Text(dataset.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    }
                    if (datasets.size > 3) {
                        Text(
                            "+ ${datasets.size - 3} more…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
