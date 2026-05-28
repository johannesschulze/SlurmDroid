package org.slurmdroid.ui.main

import androidx.compose.runtime.compositionLocalOf

/** Lambda that opens the app-level navigation drawer. Available everywhere inside [AppNavigation]. */
val LocalOpenDrawer = compositionLocalOf<() -> Unit> { {} }
