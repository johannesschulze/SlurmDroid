package org.slurmdroid.ui.main

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.slurmdroid.core.PollStateHolder
import org.slurmdroid.core.feature.FeatureRegistry
import org.slurmdroid.core.feature.ServerFeature
import org.slurmdroid.core.plugin.PluginManager
import org.slurmdroid.core.ssh.ConnectionStatus
import org.slurmdroid.core.ssh.ManualOtpRequest
import org.slurmdroid.core.ssh.SshManager
import org.slurmdroid.ui.settings.SettingsScreen
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    registry: FeatureRegistry,
    val manualOtpRequest: ManualOtpRequest,
    sshManager: SshManager,
    pollStateHolder: PollStateHolder,
    pluginManager: PluginManager,
) : ViewModel() {
    val features: List<ServerFeature> = registry.features
    val plugins = pluginManager.plugins
    val pendingDeepLinkIntent = MutableStateFlow<Intent?>(null)

    val connectionStatus = sshManager.connectionStatus

    val pollCountdown = flow {
        while (true) {
            val lastPoll = pollStateHolder.lastPollCompletedAt.value
            val fraction = if (lastPoll == 0L) 1f else {
                val elapsed = System.currentTimeMillis() - lastPoll
                (1f - elapsed.toFloat() / pollStateHolder.intervalMs).coerceIn(0f, 1f)
            }
            emit(fraction)
            delay(200)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1f)
}

@Composable
fun AppNavigation(viewModel: AppViewModel = hiltViewModel()) {
    val features = viewModel.features
    val navController = rememberNavController()
    val featureRoutes = features.flatMap { it.provideRoutes() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val pollCountdown by viewModel.pollCountdown.collectAsStateWithLifecycle()
    val plugins by viewModel.plugins.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val otpPending by viewModel.manualOtpRequest.isPending.collectAsStateWithLifecycle()
    if (otpPending) {
        OtpDialog(
            onSubmit = { otp -> viewModel.manualOtpRequest.submit(otp) },
            onDismiss = { viewModel.manualOtpRequest.cancel() },
        )
    }

    val pendingDeepLink by viewModel.pendingDeepLinkIntent.collectAsStateWithLifecycle()
    LaunchedEffect(pendingDeepLink) {
        pendingDeepLink?.let { intent ->
            navController.handleDeepLink(intent)
            viewModel.pendingDeepLinkIntent.value = null
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    fun navigateTo(route: String) {
        scope.launch { drawerState.close() }
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                DrawerHeader(connectionStatus = connectionStatus, pollCountdown = pollCountdown)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Spacer(Modifier.height(4.dp))

                features.forEach { feature ->
                    val isSelected = feature.provideRoutes().any { it.route == currentRoute }
                    NavigationDrawerItem(
                        icon = { Icon(feature.icon, contentDescription = null) },
                        label = { Text(feature.displayName) },
                        selected = isSelected,
                        onClick = { navigateTo(feature.homeRoute) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                }

                val uiPlugins = plugins.filter { it.activityComponent != null }
                if (uiPlugins.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    uiPlugins.forEach { plugin ->
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Extension, contentDescription = null) },
                            label = { Text(plugin.displayName) },
                            badge = if (!plugin.isEnabled) {
                                {
                                    Text(
                                        "disabled",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else null,
                            selected = false,
                            onClick = {
                                if (plugin.isEnabled) {
                                    scope.launch { drawerState.close() }
                                    context.startActivity(
                                        Intent().apply { component = plugin.activityComponent }
                                    )
                                }
                            },
                            modifier = Modifier
                                .padding(NavigationDrawerItemDefaults.ItemPadding)
                                .alpha(if (plugin.isEnabled) 1f else 0.45f),
                        )
                    }
                }

                Spacer(Modifier.weight(1f))
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                    selected = currentRoute == "settings",
                    onClick = { navigateTo("settings") },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
                Spacer(Modifier.height(8.dp))
            }
        },
    ) {
        CompositionLocalProvider(
            LocalNavController provides navController,
            LocalOpenDrawer provides { scope.launch { drawerState.open() } },
        ) {
            NavHost(
                navController = navController,
                startDestination = features.first().homeRoute,
                modifier = Modifier.fillMaxSize(),
            ) {
                featureRoutes.forEach { route ->
                    composable(
                        route = route.route,
                        deepLinks = route.deepLinkUri
                            ?.let { listOf(navDeepLink { uriPattern = it }) }
                            ?: emptyList(),
                    ) { route.content() }
                }
                composable(
                    "settings",
                    deepLinks = listOf(navDeepLink { uriPattern = "slurmdroid://settings" }),
                ) { SettingsScreen() }
            }
        }
    }
}

@Composable
private fun DrawerHeader(connectionStatus: ConnectionStatus, pollCountdown: Float) {
    val statusColor = when (connectionStatus) {
        ConnectionStatus.Connected -> Color(0xFF4CAF50)
        ConnectionStatus.Connecting -> Color(0xFFFFA726)
        ConnectionStatus.AuthError,
        ConnectionStatus.ConnectionError -> MaterialTheme.colorScheme.error
        ConnectionStatus.Disconnected -> MaterialTheme.colorScheme.outlineVariant
    }
    val statusText = when (connectionStatus) {
        ConnectionStatus.Connected -> "Connected"
        ConnectionStatus.Connecting -> "Connecting…"
        ConnectionStatus.AuthError -> "Authentication error"
        ConnectionStatus.ConnectionError -> "Connection error"
        ConnectionStatus.Disconnected -> "Disconnected"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "SlurmDroid",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        CircularProgressIndicator(
            progress = { pollCountdown },
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        )
    }
}

@Composable
private fun OtpDialog(onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var otp by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("One-Time Password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "The server is requesting an OTP but no TOTP secret is configured. " +
                        "Enter your current one-time password.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = otp,
                    onValueChange = { otp = it.trim() },
                    label = { Text("OTP") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(otp) }, enabled = otp.isNotBlank()) {
                Text("Submit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
