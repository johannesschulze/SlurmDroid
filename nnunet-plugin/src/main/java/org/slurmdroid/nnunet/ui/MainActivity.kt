package org.slurmdroid.nnunet.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.slurmdroid.nnunet.ui.datasets.DatasetListScreen
import org.slurmdroid.nnunet.ui.detail.DatasetDetailScreen
import org.slurmdroid.nnunet.ui.progress.ProgressScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                val navController = rememberNavController()
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
