package org.slurmdroid.plugin.api;
import org.slurmdroid.plugin.api.ICommandBridge;
import org.slurmdroid.plugin.api.PluginSettingParcel;

/**
 * AIDL interface implemented by external plugin services.
 * SlurmDroid binds to this interface to discover and poll plugins.
 *
 * Plugin APKs must export a Service with intent filter:
 *   action="org.slurmdroid.plugin.FEATURE"
 * and implement this interface via SlurmDroidPluginService.
 */
interface IPluginService {
    /** Stable unique identifier (e.g. "com.example.myplugin"). */
    String getId();

    /** Human-readable name shown in Settings. */
    String getDisplayName();

    /**
     * Called once per poll cycle. Use bridge.execute() to run SSH commands in any
     * order, using earlier results to decide later commands — equivalent to
     * ServerFeature.poll(executor). Runs on a binder thread; must not block the
     * main thread.
     *
     * Implement this for plugins that need multi-round SSH (e.g. discover datasets,
     * then check each one). Leave the body empty and implement getCommands/onResult
     * instead for simple single-round plugins.
     */
    void poll(ICommandBridge bridge);

    /**
     * Simple alternative to poll(): return a fixed list of commands and receive
     * results via onResult(). Use when commands do not depend on each other.
     * Ignored if poll() does meaningful work.
     */
    List<String> getCommands();

    /** Called once per command returned by getCommands() after SSH execution. */
    void onResult(String command, String output);

    /** Settings the plugin wants SlurmDroid to render and persist. */
    List<PluginSettingParcel> getSettings();

    /**
     * Called when stored settings are delivered to the plugin (on bind and on change).
     * Keys are setting keys; values are their current string representations.
     */
    void onSettingsChanged(in Bundle values);
}
