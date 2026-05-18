package org.slurmdroid.core.feature

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import org.slurmdroid.core.ssh.CommandExecutor

data class FeatureRoute(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val content: @Composable () -> Unit,
)

interface ServerFeature {
    val featureId: String
    val displayName: String
    val icon: ImageVector

    fun provideRoutes(): List<FeatureRoute>

    @Composable
    fun DashboardCard()

    suspend fun poll(executor: CommandExecutor)

    @Composable
    fun SettingsSection() {}
}
