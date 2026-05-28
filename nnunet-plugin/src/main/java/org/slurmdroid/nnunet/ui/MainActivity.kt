package org.slurmdroid.nnunet.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.slurmdroid.nnunet.NnUNetPluginApp
import org.slurmdroid.nnunet.ui.datasets.DatasetListScreen
import org.slurmdroid.nnunet.ui.detail.DatasetDetailScreen
import org.slurmdroid.nnunet.ui.progress.ProgressScreen
import org.slurmdroid.plugin.api.ui.PluginDrawerScaffold
import org.slurmdroid.plugin.api.ui.PluginTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val plugin = (application as NnUNetPluginApp).plugin
        setContent {
            PluginTheme {
                val navController = rememberNavController()
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route

                val lastPollTime by plugin.lastPollTime.collectAsStateWithLifecycle()

                PluginDrawerScaffold(
                    pluginDisplayName = plugin.displayName,
                    pluginPackageName = packageName,
                    currentRoute = currentRoute,
                    lastPollTime = lastPollTime,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                ) {
                    NavHost(navController = navController, startDestination = "datasets") {
                        composable("datasets") {
                            DatasetListScreen(
                                onNavigateToDataset = { navController.navigate("dataset/$it") },
                            )
                        }
                        composable("dataset/{datasetName}") { backStack ->
                            val datasetName = backStack.arguments?.getString("datasetName")
                                ?: return@composable
                            DatasetDetailScreen(
                                datasetName = datasetName,
                                onBack = { navController.popBackStack() },
                                onNavigateToProgress = { configName ->
                                    navController.navigate("progress/$datasetName/$configName")
                                },
                            )
                        }
                        composable("progress/{datasetName}/{configName}") { backStack ->
                            val datasetName = backStack.arguments?.getString("datasetName")
                                ?: return@composable
                            val configName = backStack.arguments?.getString("configName")
                                ?: return@composable
                            ProgressScreen(
                                datasetName = datasetName,
                                configName = configName,
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                }
            }
        }
    }
}
