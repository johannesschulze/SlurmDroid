package org.slurmdroid.plugin.api;

/**
 * Passed to IPluginService.poll() so plugins can execute arbitrary SSH commands
 * in sequence, using the result of one command to decide the next.
 *
 * Calls are synchronous and block the calling thread until the command completes.
 * SlurmDroid executes the command via its existing SSH session and returns trimmed
 * stdout, or an empty string on error.
 */
interface ICommandBridge {
    String execute(String command);
}
