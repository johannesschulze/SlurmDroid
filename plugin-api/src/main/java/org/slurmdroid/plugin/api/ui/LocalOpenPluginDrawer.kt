package org.slurmdroid.plugin.api.ui

import androidx.compose.runtime.compositionLocalOf

/** Lambda that opens the plugin-level navigation drawer. Available everywhere inside [PluginDrawerScaffold]. */
val LocalOpenPluginDrawer = compositionLocalOf<() -> Unit> { {} }
