package org.slurmdroid.ui.main

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.lifecycle.HiltViewModel
import org.slurmdroid.core.feature.FeatureRegistry
import org.slurmdroid.core.feature.ServerFeature
import org.slurmdroid.ui.dashboard.MainDashboardScreen
import org.slurmdroid.ui.settings.SettingsScreen
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    registry: FeatureRegistry,
) : ViewModel() {
    val features: List<ServerFeature> = registry.features
}

private data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

@Composable
fun AppNavigation(viewModel: AppViewModel = hiltViewModel()) {
    val features = viewModel.features
    val navController = rememberNavController()
    val featureRoutes = features.flatMap { it.provideRoutes() }

    val bottomItems = buildList {
        add(NavItem("dashboard", "Dashboard", Icons.Default.Home))
        featureRoutes.forEach { add(NavItem(it.route, it.label, it.icon)) }
        add(NavItem("settings", "Settings", Icons.Default.Settings))
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomItems.forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute == item.route,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        CompositionLocalProvider(LocalNavController provides navController) {
            NavHost(
                navController = navController,
                startDestination = "dashboard",
                modifier = Modifier.padding(padding),
            ) {
                composable("dashboard") {
                    MainDashboardScreen(features = features)
                }
                featureRoutes.forEach { route ->
                    composable(route.route) { route.content() }
                }
                composable("settings") {
                    SettingsScreen()
                }
            }
        }
    }
}
