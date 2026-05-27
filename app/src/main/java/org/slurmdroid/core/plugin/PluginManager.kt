package org.slurmdroid.core.plugin

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slurmdroid.core.AppPreferences
import org.slurmdroid.core.Result
import org.slurmdroid.core.ssh.CommandExecutor
import org.slurmdroid.plugin.api.ICommandBridge
import org.slurmdroid.plugin.api.IPluginService
import org.slurmdroid.plugin.api.PluginSettingParcel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PluginManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences,
) {
    data class BoundPlugin(
        val packageName: String,
        val id: String,
        val displayName: String,
        val service: IPluginService,
        val settings: List<PluginSettingParcel>,
        /** Non-null when the plugin APK declares an Activity with the FEATURE_ACTIVITY intent filter. */
        val activityComponent: ComponentName?,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connections = mutableMapOf<String, ServiceConnection>()

    private val _plugins = MutableStateFlow<List<BoundPlugin>>(emptyList())
    val plugins: StateFlow<List<BoundPlugin>> = _plugins.asStateFlow()

    /** Scans installed packages for plugin services (and optional UI activities) and binds them. */
    fun discoverAndBind() {
        val pm = context.packageManager

        // Build a package → ActivityComponentName map for plugins that also declare a UI Activity.
        val activityMap = queryActivities(pm).associate { info ->
            info.activityInfo.packageName to
                ComponentName(info.activityInfo.packageName, info.activityInfo.name)
        }

        queryServices(pm).forEach { info ->
            bindPlugin(
                packageName = info.serviceInfo.packageName,
                className = info.serviceInfo.name,
                activityComponent = activityMap[info.serviceInfo.packageName],
            )
        }
    }

    private fun queryServices(pm: PackageManager) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentServices(Intent(ACTION_FEATURE), PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentServices(Intent(ACTION_FEATURE), 0)
        }

    private fun queryActivities(pm: PackageManager) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(Intent(ACTION_FEATURE_ACTIVITY), PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(Intent(ACTION_FEATURE_ACTIVITY), 0)
        }

    private fun bindPlugin(packageName: String, className: String, activityComponent: ComponentName?) {
        if (connections.containsKey(packageName)) return

        val intent = Intent(ACTION_FEATURE).apply { setClassName(packageName, className) }

        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                val service = IPluginService.Stub.asInterface(binder)
                scope.launch {
                    runCatching {
                        val id = service.id
                        val displayName = service.displayName
                        val settings = service.settings ?: emptyList()

                        service.onSettingsChanged(buildSettingsBundle(id, settings))

                        _plugins.update { prev ->
                            prev.filter { it.packageName != packageName } + BoundPlugin(
                                packageName = packageName,
                                id = id,
                                displayName = displayName,
                                service = service,
                                settings = settings,
                                activityComponent = activityComponent,
                            )
                        }
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                _plugins.update { list -> list.filter { it.packageName != packageName } }
            }
        }

        connections[packageName] = conn
        context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
    }

    /** Calls poll() on each bound plugin, providing an ICommandBridge backed by [executor]. */
    suspend fun pollAll(executor: CommandExecutor) {
        _plugins.value.forEach { plugin ->
            runCatching {
                val bridge = object : ICommandBridge.Stub() {
                    override fun execute(command: String): String =
                        when (val result = runBlocking { executor.execute(command) }) {
                            is Result.Success -> result.data
                            else -> ""
                        }
                }
                plugin.service.poll(bridge)
            }
        }
    }

    /** Pushes the current persisted values for [pluginId]'s settings back to the plugin service. */
    fun notifySettingsChanged(pluginId: String) {
        val plugin = _plugins.value.find { it.id == pluginId } ?: return
        scope.launch {
            runCatching {
                plugin.service.onSettingsChanged(buildSettingsBundle(pluginId, plugin.settings))
            }
        }
    }

    private fun buildSettingsBundle(pluginId: String, settings: List<PluginSettingParcel>): Bundle {
        val bundle = Bundle()
        settings.forEach { s ->
            bundle.putString(s.key, appPreferences.getPluginSetting(pluginId, s.key, s.defaultText))
        }
        return bundle
    }

    /**
     * Registers a receiver for package install/replace/remove events so plugins are
     * automatically re-discovered without requiring an app restart.
     * Call once from Application.onCreate().
     */
    fun registerPackageReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        context.registerReceiver(packageReceiver, filter)
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val pkg = intent.data?.schemeSpecificPart ?: return
            when (intent.action) {
                Intent.ACTION_PACKAGE_REMOVED -> {
                    // Unbind and forget — onServiceDisconnected may already have fired.
                    connections.remove(pkg)?.let { context.unbindService(it) }
                    _plugins.update { list -> list.filter { it.packageName != pkg } }
                }
                Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REPLACED -> {
                    // Drop stale connection so bindPlugin doesn't skip this package.
                    connections.remove(pkg)?.let { runCatching { context.unbindService(it) } }
                    _plugins.update { list -> list.filter { it.packageName != pkg } }
                    discoverAndBind()
                }
            }
        }
    }

    companion object {
        const val ACTION_FEATURE = "org.slurmdroid.plugin.FEATURE"
        const val ACTION_FEATURE_ACTIVITY = "org.slurmdroid.plugin.FEATURE_ACTIVITY"
    }
}
