package org.slurmdroid.features.slurm

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import org.slurmdroid.core.feature.FeatureRoute
import org.slurmdroid.core.feature.ServerFeature
import org.slurmdroid.core.ssh.CommandExecutor
import org.slurmdroid.features.slurm.data.SlurmRepository
import org.slurmdroid.features.slurm.ui.detail.JobDetailScreen
import org.slurmdroid.features.slurm.ui.detail.LogViewScreen
import org.slurmdroid.features.slurm.ui.history.HistoryScreen
import org.slurmdroid.features.slurm.ui.jobs.JobsScreen
import org.slurmdroid.features.slurm.ui.overview.SlurmOverviewScreen
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
            route = "slurm",
            label = "Slurm",
            icon = Icons.Default.GridView,
            content = {
                val nav = LocalNavController.current
                SlurmOverviewScreen(
                    onNavigateToJobs = { nav.navigate("slurm/jobs") },
                    onNavigateToHistory = { nav.navigate("slurm/history") },
                    onNavigateToDetail = { jobId -> nav.navigate("slurm/job/$jobId") },
                )
            },
        ),
        FeatureRoute(
            route = "slurm/jobs",
            label = "Active Jobs",
            icon = Icons.AutoMirrored.Filled.List,
            content = {
                val nav = LocalNavController.current
                JobsScreen(
                    onBack = { nav.popBackStack() },
                    onNavigateToDetail = { jobId -> nav.navigate("slurm/job/$jobId") },
                )
            },
            showInNav = false,
            subtreeOf = "slurm",
        ),
        FeatureRoute(
            route = "slurm/history",
            label = "History",
            icon = Icons.Default.History,
            content = {
                val nav = LocalNavController.current
                HistoryScreen(
                    onBack = { nav.popBackStack() },
                    onNavigateToDetail = { jobId -> nav.navigate("slurm/job/$jobId") },
                )
            },
            showInNav = false,
            subtreeOf = "slurm",
        ),
        FeatureRoute(
            route = "slurm/job/{jobId}",
            label = "Job Detail",
            icon = Icons.Default.Info,
            content = { JobDetailScreen() },
            showInNav = false,
            subtreeOf = "slurm",
        ),
        FeatureRoute(
            route = "slurm/logview/{logFile}",
            label = "Log",
            icon = Icons.Default.Info,
            content = { LogViewScreen() },
            showInNav = false,
            subtreeOf = "slurm",
        ),
    )

    override suspend fun poll(executor: CommandExecutor) {
        repository.poll()
    }
}
