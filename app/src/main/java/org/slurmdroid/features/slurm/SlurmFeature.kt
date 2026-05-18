package org.slurmdroid.features.slurm

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import org.slurmdroid.core.feature.FeatureRoute
import org.slurmdroid.core.feature.ServerFeature
import org.slurmdroid.core.ssh.CommandExecutor
import org.slurmdroid.features.slurm.data.SlurmRepository
import org.slurmdroid.features.slurm.ui.dashboard.SlurmDashboardCard
import org.slurmdroid.features.slurm.ui.history.HistoryScreen
import org.slurmdroid.features.slurm.ui.jobs.JobsScreen
import org.slurmdroid.ui.main.LocalNavController
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SlurmFeature @Inject constructor(
    val repository: SlurmRepository,
) : ServerFeature {
    override val featureId = "slurm"
    override val displayName = "Slurm"
    override val icon: ImageVector = Icons.Default.GridView

    override fun provideRoutes(): List<FeatureRoute> = listOf(
        FeatureRoute(
            route = "slurm/jobs",
            label = "Jobs",
            icon = Icons.AutoMirrored.Filled.List,
            content = {
                val navController: NavController = LocalNavController.current
                JobsScreen(onNavigateToHistory = { navController.navigate("slurm/history") })
            },
        ),
        FeatureRoute(
            route = "slurm/history",
            label = "History",
            icon = Icons.Default.History,
            content = { HistoryScreen() },
        ),
    )

    @Composable
    override fun DashboardCard() {
        SlurmDashboardCard()
    }

    override suspend fun poll(executor: CommandExecutor) {
        repository.poll()
    }
}
