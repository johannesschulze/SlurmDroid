package org.slurmdroid.ui.dashboard

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.slurmdroid.core.feature.ServerFeature
import org.slurmdroid.core.ssh.ConnectionStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboardScreen(
    features: List<ServerFeature>,
    viewModel: MainDashboardViewModel = hiltViewModel(),
) {
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val pollCountdown by viewModel.pollCountdown.collectAsStateWithLifecycle()

    val statusColor by animateColorAsState(
        targetValue = when (connectionStatus) {
            ConnectionStatus.Connected -> Color(0xFF4CAF50)
            ConnectionStatus.Connecting -> Color(0xFFFFA726)
            ConnectionStatus.AuthError,
            ConnectionStatus.ConnectionError -> MaterialTheme.colorScheme.error
            ConnectionStatus.Disconnected -> MaterialTheme.colorScheme.outlineVariant
        },
        label = "connection-status-color",
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SlurmDroid") },
                actions = {
                    CircularProgressIndicator(
                        progress = { pollCountdown },
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(20.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    )
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(statusColor),
            )
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                Crossfade(
                    targetState = connectionStatus == ConnectionStatus.Connected,
                    label = "dashboard-content",
                    modifier = Modifier.fillMaxSize(),
                ) { isConnected ->
                    if (isConnected) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(features, key = { it.featureId }) { feature ->
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    feature.DashboardCard()
                                }
                            }
                        }
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                when (connectionStatus) {
                                    ConnectionStatus.Connecting -> "Connecting to cluster…"
                                    ConnectionStatus.AuthError -> "Authentication failed — check Settings"
                                    ConnectionStatus.ConnectionError -> "Connection failed — check Settings"
                                    else -> "Not connected"
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}
