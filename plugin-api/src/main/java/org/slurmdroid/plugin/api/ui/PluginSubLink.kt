package org.slurmdroid.plugin.api.ui

import androidx.compose.ui.graphics.vector.ImageVector

data class PluginSubLink(
    val label: String,
    val route: String,
    val icon: ImageVector? = null,
)
