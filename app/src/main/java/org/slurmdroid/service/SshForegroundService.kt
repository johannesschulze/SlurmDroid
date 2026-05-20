package org.slurmdroid.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slurmdroid.core.PollStateHolder
import org.slurmdroid.core.feature.FeatureRegistry
import org.slurmdroid.core.notifications.JobNotificationManager
import org.slurmdroid.core.ssh.CommandExecutor
import org.slurmdroid.core.ssh.SshManager
import org.slurmdroid.features.slurm.data.SlurmRepository
import org.slurmdroid.R
import org.slurmdroid.ui.main.MainActivity
import javax.inject.Inject

/**
 * Foreground service that maintains a persistent SSH session and runs
 * a 60-second polling loop over all registered [FeatureRegistry] features.
 *
 * Note: WorkManager's minimum periodic interval is 15 minutes, which is too coarse
 * for live cluster monitoring. The polling loop is therefore driven by a coroutine
 * delay inside this service. WorkManager is used only for deferred/backoff triggers
 * (step 10) after job submit/cancel.
 */
@AndroidEntryPoint
class SshForegroundService : Service() {

    @Inject lateinit var sshManager: SshManager
    @Inject lateinit var commandExecutor: CommandExecutor
    @Inject lateinit var featureRegistry: FeatureRegistry
    @Inject lateinit var slurmRepository: SlurmRepository
    @Inject lateinit var jobNotificationManager: JobNotificationManager
    @Inject lateinit var pollStateHolder: PollStateHolder

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        startPollingLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_POLL_NOW) {
            scope.launch { pollAllFeatures() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        // Disconnect best-effort on a separate scope; SSH server drops the connection
        // when the socket closes even if this doesn't complete.
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { sshManager.disconnect() }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startPollingLoop() {
        scope.launch {
            while (isActive) {
                pollAllFeatures()
                delay(pollStateHolder.intervalMs)
            }
        }
    }

    private suspend fun pollAllFeatures() {
        featureRegistry.features.forEach { feature ->
            runCatching { feature.poll(commandExecutor) }
        }
        pollStateHolder.lastPollCompletedAt.value = System.currentTimeMillis()
        jobNotificationManager.onPollComplete(
            slurmRepository.jobs.value,
            slurmRepository.sacctJobs.value,
        )
    }

    private fun buildNotification(): android.app.Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Cluster Monitor", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Keeps SlurmDroid connected to the cluster" }
        )
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SlurmDroid")
            .setContentText("Monitoring cluster…")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "slurmdroid_monitor"

        /** Send this action to trigger an out-of-schedule poll (e.g. after job submit/cancel). */
        const val ACTION_POLL_NOW = "org.slurmdroid.action.POLL_NOW"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, SshForegroundService::class.java))
        }

        fun pollNow(context: Context) {
            context.startForegroundService(
                Intent(context, SshForegroundService::class.java)
                    .apply { action = ACTION_POLL_NOW }
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SshForegroundService::class.java))
        }
    }
}
