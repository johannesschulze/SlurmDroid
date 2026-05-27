package org.slurmdroid.core

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    var showRunningJobNotifications: Boolean
        get() = prefs.getBoolean(KEY_RUNNING_NOTIFS, true)
        set(v) { prefs.edit().putBoolean(KEY_RUNNING_NOTIFS, v).apply() }

    var logDirectory: String
        get() = prefs.getString(KEY_LOG_DIR, "slurm_logs") ?: "slurm_logs"
        set(v) { prefs.edit().putString(KEY_LOG_DIR, v).apply() }

    /** Full sbatch command from the last successful submission; pre-fills the new-job dialog. */
    var lastSubmittedCommand: String
        get() = prefs.getString(KEY_LAST_CMD, "") ?: ""
        set(v) { prefs.edit().putString(KEY_LAST_CMD, v).apply() }

    /**
     * Override for the $nnUNet_results path. When set, takes precedence over the server-side
     * environment variable (useful when the SSH shell doesn't source the user's profile).
     */
    var nnUNetResultsDir: String
        get() = prefs.getString(KEY_NNUNET_RESULTS, "") ?: ""
        set(v) { prefs.edit().putString(KEY_NNUNET_RESULTS, v).apply() }

    var nnUNetBaseDir: String
        get() = prefs.getString(KEY_NNUNET_BASE, "") ?: ""
        set(v) { prefs.edit().putString(KEY_NNUNET_BASE, v).apply() }

    var nnUNetRawDir: String
        get() = prefs.getString(KEY_NNUNET_RAW, "") ?: ""
        set(v) { prefs.edit().putString(KEY_NNUNET_RAW, v).apply() }

    var nnUNetPreprocessedDir: String
        get() = prefs.getString(KEY_NNUNET_PREPROCESSED, "") ?: ""
        set(v) { prefs.edit().putString(KEY_NNUNET_PREPROCESSED, v).apply() }

    fun getPluginSetting(pluginId: String, key: String, default: String): String =
        prefs.getString("plugin.$pluginId.$key", default) ?: default

    fun setPluginSetting(pluginId: String, key: String, value: String) {
        prefs.edit().putString("plugin.$pluginId.$key", value).apply()
    }

    companion object {
        private const val PREFS_FILE = "slurmdroid_app_prefs"
        private const val KEY_RUNNING_NOTIFS = "show_running_job_notifications"
        private const val KEY_LOG_DIR = "log_directory"
        private const val KEY_LAST_CMD = "last_submitted_command"
        private const val KEY_NNUNET_BASE = "nnunet_base_dir"
        private const val KEY_NNUNET_RESULTS = "nnunet_results_dir"
        private const val KEY_NNUNET_RAW = "nnunet_raw_dir"
        private const val KEY_NNUNET_PREPROCESSED = "nnunet_preprocessed_dir"
    }
}
