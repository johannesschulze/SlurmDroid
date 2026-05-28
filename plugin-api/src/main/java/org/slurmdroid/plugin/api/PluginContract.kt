package org.slurmdroid.plugin.api

object PluginContract {
    const val ACTION_FEATURE = "org.slurmdroid.plugin.FEATURE"
    const val ACTION_FEATURE_ACTIVITY = "org.slurmdroid.plugin.FEATURE_ACTIVITY"

    /**
     * Broadcast sent by a plugin UI to request SlurmDroid to trigger an out-of-schedule poll.
     * Must be sent as an explicit broadcast targeting [SLURMDROID_PACKAGE] so it reaches the
     * manifest-declared receiver on Android 8.0+ (implicit broadcasts are blocked there).
     */
    const val ACTION_REQUEST_POLL = "org.slurmdroid.action.REQUEST_POLL"

    /** Package name of the SlurmDroid host app. Use with [Intent.setPackage] for broadcasts. */
    const val SLURMDROID_PACKAGE = "org.slurmdroid"
}
