package org.slurmdroid.plugin.api

/**
 * Kotlin interface for plugin authors. Implement this and pass an instance to
 * [SlurmDroidPluginService.createPlugin].
 *
 * All methods are called on a binder thread — use thread-safe data structures.
 *
 * Choose one polling style:
 * - **Multi-round** (recommended for complex plugins): override [poll] and use the
 *   provided executor to run SSH commands in any order, using earlier results to
 *   decide later ones. Leave [getCommands]/[onResult] with empty/default bodies.
 * - **Single-round** (simple plugins): override [getCommands] and [onResult].
 *   Leave [poll] with its default empty body.
 */
interface ISlurmDroidPlugin {
    /** Stable unique identifier matching the plugin APK's applicationId, e.g. "com.example.myplugin". */
    val id: String

    /** Human-readable name shown in SlurmDroid's Settings screen. */
    val displayName: String

    /**
     * Called once per poll cycle with an [executor] that runs SSH commands synchronously.
     * Use this for plugins that need multi-round SSH (discover → check → read).
     * The default implementation runs [getCommands] and delivers results to [onResult].
     */
    fun poll(executor: (String) -> String) {
        getCommands().forEach { command -> onResult(command, executor(command)) }
    }

    /** Return the commands to run each poll cycle (single-round style). */
    fun getCommands(): List<String> = emptyList()

    /** Receives trimmed stdout for each command returned by [getCommands]. */
    fun onResult(command: String, output: String) {}

    /** Settings the plugin wants SlurmDroid to render and persist. */
    fun getSettings(): List<PluginSetting>

    /**
     * Receives the current values of all settings declared in [getSettings].
     * Called once on bind and again whenever the user changes a value in Settings.
     * Toggle values are "true"/"false" strings.
     */
    fun onSettingsChanged(values: Map<String, String>)
}
