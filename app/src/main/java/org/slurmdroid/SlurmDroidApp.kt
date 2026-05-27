package org.slurmdroid

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import org.slurmdroid.core.plugin.PluginManager
import javax.inject.Inject

@HiltAndroidApp
class SlurmDroidApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var pluginManager: PluginManager

    override fun onCreate() {
        super.onCreate()
        pluginManager.discoverAndBind()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
