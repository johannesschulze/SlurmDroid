package org.slurmdroid.features.nnunet

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import org.slurmdroid.core.feature.FeatureRoute
import org.slurmdroid.core.feature.ServerFeature
import org.slurmdroid.core.ssh.CommandExecutor
import org.slurmdroid.features.nnunet.data.NnUNetRepository
import org.slurmdroid.features.nnunet.ui.dashboard.NnUNetDashboardCard
import org.slurmdroid.features.nnunet.ui.detail.NnUNetDatasetDetailScreen
import org.slurmdroid.features.nnunet.ui.datasets.NnUNetDatasetScreen
import org.slurmdroid.features.nnunet.ui.progress.NnUNetProgressScreen
import org.slurmdroid.ui.main.LocalNavController
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NnUNetFeature @Inject constructor(
    private val repository: NnUNetRepository,
) : ServerFeature {
    override val featureId = "nnunet"
    override val displayName = "nnU-Net"
    override val icon: ImageVector = Icons.Default.Psychology

    override fun provideRoutes(): List<FeatureRoute> = listOf(
        FeatureRoute(
            route = "nnunet/datasets",
            label = "nnU-Net",
            icon = Icons.Default.Psychology,
            content = {
                val nav = LocalNavController.current
                NnUNetDatasetScreen(
                    onNavigateToDataset = { datasetName ->
                        nav.navigate("nnunet/dataset/$datasetName")
                    },
                )
            },
        ),
        FeatureRoute(
            route = "nnunet/dataset/{datasetName}",
            label = "Dataset",
            icon = Icons.Default.Psychology,
            content = {
                val nav = LocalNavController.current
                val datasetName = checkNotNull(
                    nav.currentBackStackEntry?.arguments?.getString("datasetName")
                )
                NnUNetDatasetDetailScreen(
                    onBack = { nav.popBackStack() },
                    onNavigateToProgress = { configName ->
                        nav.navigate("nnunet/progress/$datasetName/$configName")
                    },
                )
            },
            showInNav = false,
            subtreeOf = "nnunet/datasets",
        ),
        FeatureRoute(
            route = "nnunet/progress/{datasetName}/{configName}",
            label = "Training Progress",
            icon = Icons.Default.Psychology,
            content = {
                val nav = LocalNavController.current
                NnUNetProgressScreen(onBack = { nav.popBackStack() })
            },
            showInNav = false,
            subtreeOf = "nnunet/datasets",
        ),
    )

    @Composable
    override fun DashboardCard() {
        NnUNetDashboardCard()
    }

    override suspend fun poll(executor: CommandExecutor) {
        repository.poll()
    }
}
