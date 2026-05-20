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

    companion object {
        private const val PREFS_FILE = "slurmdroid_app_prefs"
        private const val KEY_RUNNING_NOTIFS = "show_running_job_notifications"
    }
}
