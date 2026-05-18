package org.slurmdroid.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.slurmdroid.core.feature.ServerFeature

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboardScreen(
    features: List<ServerFeature>,
    viewModel: MainDashboardViewModel = hiltViewModel(),
) {
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val pollError by viewModel.pollError.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("SlurmDroid") }) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                pollError?.let { error ->
                    item {
                        Text(
                            error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                items(features, key = { it.featureId }) { feature ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        feature.DashboardCard()
                    }
                }
            }
        }
    }
}
