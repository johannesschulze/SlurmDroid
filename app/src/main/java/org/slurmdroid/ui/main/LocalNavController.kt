package org.slurmdroid.ui.main

import androidx.compose.runtime.compositionLocalOf
import androidx.navigation.NavController

val LocalNavController = compositionLocalOf<NavController> {
    error("LocalNavController not provided")
}
