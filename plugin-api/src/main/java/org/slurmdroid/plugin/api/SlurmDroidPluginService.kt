package org.slurmdroid.plugin.api

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder

/**
 * Abstract base Service for SlurmDroid plugins. Extend this, override [createPlugin],
 * and declare the service in your manifest with:
 *
 * ```xml
 * <service android:name=".MyPluginService" android:exported="true">
 *     <intent-filter>
 *         <action android:name="org.slurmdroid.plugin.FEATURE" />
 *     </intent-filter>
 * </service>
 * ```
 *
 * To also appear in SlurmDroid's navigation drawer with a dedicated screen, declare an
 * Activity with the FEATURE_ACTIVITY intent filter. SlurmDroid will launch it via startActivity
 * when the user taps the drawer entry. The Activity runs in your process and can use any UI.
 *
 * ```xml
 * <activity android:name=".MyPluginActivity" android:exported="true">
 *     <intent-filter>
 *         <action android:name="org.slurmdroid.plugin.FEATURE_ACTIVITY" />
 *         <category android:name="android.intent.category.DEFAULT" />
 *     </intent-filter>
 * </activity>
 * ```
 */
abstract class SlurmDroidPluginService : Service() {

    protected abstract fun createPlugin(): ISlurmDroidPlugin

    private val plugin: ISlurmDroidPlugin by lazy { createPlugin() }

    private val binder = object : IPluginService.Stub() {
        override fun getId(): String = plugin.id
        override fun getDisplayName(): String = plugin.displayName
        override fun poll(bridge: ICommandBridge) = plugin.poll { command -> bridge.execute(command) }
        override fun getCommands(): List<String> = plugin.getCommands()
        override fun onResult(command: String, output: String) = plugin.onResult(command, output)
        override fun getSettings(): List<PluginSettingParcel> = plugin.getSettings().map { it.toParcel() }
        override fun onSettingsChanged(values: Bundle) {
            val map = values.keySet().associateWith { values.getString(it, "") }
            plugin.onSettingsChanged(map)
        }
    }

    override fun onBind(intent: Intent): IBinder = binder
}
