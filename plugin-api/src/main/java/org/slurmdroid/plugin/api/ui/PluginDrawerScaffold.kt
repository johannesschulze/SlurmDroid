package org.slurmdroid.plugin.api.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private const val ACTION_FEATURE_ACTIVITY = "org.slurmdroid.plugin.FEATURE_ACTIVITY"
private const val SLURMDROID_PACKAGE = "org.slurmdroid"

private data class OtherPlugin(val label: String, val component: ComponentName)

@Composable
fun PluginDrawerScaffold(
    pluginDisplayName: String,
    pluginPackageName: String,
    rootRoute: String = "datasets",
    subLinks: List<PluginSubLink> = emptyList(),
    currentRoute: String? = null,
    lastPollTime: Long? = null,
    onNavigate: (route: String) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val otherPlugins = remember(pluginPackageName) {
        queryOtherPlugins(context, pluginPackageName)
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                PluginDrawerHeader(pluginDisplayName, lastPollTime)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Spacer(Modifier.height(4.dp))

                // ── Slurm (core app) ──────────────────────────────────────────
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Terminal, contentDescription = null) },
                    label = { Text("Slurm") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        context.startActivity(
                            Intent().setClassName(SLURMDROID_PACKAGE, "$SLURMDROID_PACKAGE.ui.main.MainActivity")
                                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        )
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // ── Active plugin ─────────────────────────────────────────────
                val pluginIsSelected = currentRoute != null &&
                    (currentRoute == rootRoute || subLinks.none { it.route == currentRoute })
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Extension, contentDescription = null) },
                    label = { Text(pluginDisplayName) },
                    selected = subLinks.isEmpty() && pluginIsSelected,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigate(rootRoute)
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
                subLinks.forEach { link ->
                    NavigationDrawerItem(
                        icon = link.icon?.let { { Icon(it, contentDescription = null) } },
                        label = { Text(link.label) },
                        selected = currentRoute == link.route,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onNavigate(link.route)
                        },
                        modifier = Modifier.padding(
                            start = NavigationDrawerItemDefaults.ItemPadding.calculateLeftPadding(
                                androidx.compose.ui.unit.LayoutDirection.Ltr
                            ) + 16.dp,
                            end = NavigationDrawerItemDefaults.ItemPadding.calculateRightPadding(
                                androidx.compose.ui.unit.LayoutDirection.Ltr
                            ),
                        ),
                    )
                }

                // ── Other plugins ─────────────────────────────────────────────
                if (otherPlugins.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    otherPlugins.forEach { plugin ->
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Extension, contentDescription = null) },
                            label = { Text(plugin.label) },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                context.startActivity(
                                    Intent().setComponent(plugin.component)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                        )
                    }
                }

                Spacer(Modifier.weight(1f))
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // ── Settings ──────────────────────────────────────────────────
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("slurmdroid://settings"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
                Spacer(Modifier.height(8.dp))
            }
        },
    ) {
        CompositionLocalProvider(
            LocalOpenPluginDrawer provides { scope.launch { drawerState.open() } },
        ) {
            content()
        }
    }
}

@Composable
private fun PluginDrawerHeader(pluginDisplayName: String, lastPollTime: Long?) {
    val now = System.currentTimeMillis()
    val ageMs = if (lastPollTime != null) now - lastPollTime else null

    val (dotColor, statusText) = when {
        ageMs == null -> MaterialTheme.colorScheme.outlineVariant to "Not yet polled"
        ageMs < 2 * 60_000L -> Color(0xFF4CAF50) to "Connected"
        ageMs < 10 * 60_000L -> Color(0xFFFFA726) to "Stale"
        else -> MaterialTheme.colorScheme.error to "Disconnected"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "SlurmDroid",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                pluginDisplayName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun queryOtherPlugins(context: Context, selfPackage: String): List<OtherPlugin> {
    val pm = context.packageManager
    val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.queryIntentActivities(
            Intent(ACTION_FEATURE_ACTIVITY),
            PackageManager.ResolveInfoFlags.of(0L),
        )
    } else {
        @Suppress("DEPRECATION")
        pm.queryIntentActivities(Intent(ACTION_FEATURE_ACTIVITY), 0)
    }
    return resolved
        .filter { it.activityInfo.packageName != selfPackage }
        .map { info ->
            OtherPlugin(
                label = info.loadLabel(pm).toString(),
                component = ComponentName(info.activityInfo.packageName, info.activityInfo.name),
            )
        }
}
