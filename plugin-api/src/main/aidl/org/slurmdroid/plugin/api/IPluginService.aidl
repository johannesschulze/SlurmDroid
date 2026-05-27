package org.slurmdroid.plugin.api;
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
     * Shell commands to execute on the cluster during each poll cycle.
     * SlurmDroid executes them via SSH and passes results back via onResult().
     */
    List<String> getCommands();

    /** Called once per command after SSH execution. output is trimmed stdout. */
    void onResult(String command, String output);

    /** Settings the plugin wants SlurmDroid to render and persist. */
    List<PluginSettingParcel> getSettings();

    /**
     * Called when stored settings are delivered to the plugin (on bind and on change).
     * keys are setting keys; values are their current string representations.
     */
    void onSettingsChanged(in Bundle values);
}
