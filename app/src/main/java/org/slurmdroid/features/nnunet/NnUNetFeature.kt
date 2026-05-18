package org.slurmdroid.features.nnunet

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import org.slurmdroid.core.feature.FeatureRoute
import org.slurmdroid.core.feature.ServerFeature
import org.slurmdroid.core.ssh.CommandExecutor
import javax.inject.Inject

// Placeholder – not registered in FeatureRegistry until implemented
class NnUNetFeature @Inject constructor() : ServerFeature {
    override val featureId = "nnunet"
    override val displayName = "nnU-Net"
    override val icon: ImageVector = Icons.Default.Psychology

    override fun provideRoutes(): List<FeatureRoute> = emptyList()

    @Composable
    override fun DashboardCard() {}

    override suspend fun poll(executor: CommandExecutor) {}
}
