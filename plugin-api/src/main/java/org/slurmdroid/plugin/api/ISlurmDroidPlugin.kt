package org.slurmdroid.plugin.api

/**
 * Kotlin interface for plugin authors. Implement this and pass an instance to
 * [SlurmDroidPluginService.createPlugin].
 *
 * All methods are called on a binder thread — use thread-safe data structures.
 */
interface ISlurmDroidPlugin {
    /** Stable unique identifier matching the plugin APK's applicationId, e.g. "com.example.myplugin". */
    val id: String

    /** Human-readable name shown in SlurmDroid's Settings screen. */
    val displayName: String

    /**
     * Shell commands to run on the cluster during each poll cycle.
     * SlurmDroid executes these via SSH and calls [onResult] for each.
     */
    fun getCommands(): List<String>

    /** Receives trimmed stdout for each command returned by [getCommands]. */
    fun onResult(command: String, output: String)

    /** Settings the plugin wants SlurmDroid to render and persist. */
    fun getSettings(): List<PluginSetting>

    /**
     * Receives the current values of all settings declared in [getSettings].
     * Called once on bind and again whenever the user changes a value in Settings.
     * Toggle values are "true"/"false" strings.
     */
    fun onSettingsChanged(values: Map<String, String>)
}
