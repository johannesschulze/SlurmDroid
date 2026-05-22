package org.slurmdroid.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import org.slurmdroid.R
import org.slurmdroid.core.AppPreferences
import org.slurmdroid.features.slurm.domain.SacctJob
import org.slurmdroid.features.slurm.domain.SlurmJob
import org.slurmdroid.ui.main.MainActivity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JobNotificationManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences,
) {
    private val nm = context.getSystemService(NotificationManager::class.java)

    /** jobId → last observed state (only jobs seen since service start) */
    private val trackedStates = mutableMapOf<String, String>()

    init { createChannels() }

    private fun createChannels() {
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_RUNNING, "Running Jobs", NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Shown while your jobs are running on the cluster" },
                NotificationChannel(
                    CHANNEL_FINISHED, "Job Finished", NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Posted when a job completes, fails, or is cancelled" },
            )
        )
    }

    fun onPollComplete(activeJobs: List<SlurmJob>, sacctJobs: List<SacctJob>) {
        val activeById = activeJobs.associateBy { it.jobId }
        val totalRunning = activeJobs.count { it.state == "RUNNING" }

        // Check previously tracked jobs for transitions
        val iter = trackedStates.entries.iterator()
        while (iter.hasNext()) {
            val (jobId, prevState) = iter.next()
            val current = activeById[jobId]
            if (current == null) {
                // Job left the active queue — find its terminal state in sacct
                if (prevState == "RUNNING" || prevState == "PENDING") {
                    nm.cancel(notifId(jobId))
                    val finished = sacctJobs.find { it.jobId == jobId }
                    if (finished != null) postFinishedNotification(jobId, finished.jobName, finished.state)
                }
                iter.remove()
            } else {
                val newState = current.state
                when {
                    newState == "RUNNING" && prevState != "RUNNING" -> {
                        if (appPreferences.showRunningJobNotifications)
                            postRunningNotification(current, totalRunning)
                    }
                    newState == "RUNNING" -> {
                        if (appPreferences.showRunningJobNotifications) {
                            postRunningNotification(current, totalRunning)
                        } else {
                            nm.cancel(notifId(jobId))
                        }
                    }
                }
                trackedStates[jobId] = newState
            }
        }

        // Register newly seen jobs
        for (job in activeJobs) {
            if (job.jobId !in trackedStates) {
                trackedStates[job.jobId] = job.state
                if (job.state == "RUNNING" && appPreferences.showRunningJobNotifications) {
                    postRunningNotification(job, totalRunning)
                }
            }
        }
    }

    /** Called immediately when the user disables the setting. */
    fun cancelAllRunningNotifications() {
        trackedStates.forEach { (jobId, state) ->
            if (state == "RUNNING") nm.cancel(notifId(jobId))
        }
    }

    private fun postRunningNotification(job: SlurmJob, totalRunning: Int) {
        val progress = if (job.timeUsed.isNotBlank() && job.timeLimit.isNotBlank())
            "${job.timeUsed} / ${job.timeLimit}" else ""
        val text = buildString {
            append(job.partition)
            if (progress.isNotBlank()) append(" · $progress")
        }

        val uri = if (totalRunning == 1) "slurmdroid://job/${job.jobId}" else "slurmdroid://jobs"
        nm.notify(
            notifId(job.jobId),
            NotificationCompat.Builder(context, CHANNEL_RUNNING)
                .setContentTitle("SlurmDroid: ".plus(job.name))
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_notification)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(deepLinkIntent(uri, notifId(job.jobId)))
                .build(),
        )
    }

    private fun deepLinkIntent(uri: String, requestCode: Int): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri), context, MainActivity::class.java)
            .apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun postFinishedNotification(jobId: String, jobName: String, state: String) {
        val upper = state.uppercase()
        val title = when (upper) {
            "COMPLETED"                                              -> "$jobName completed"
            "FAILED", "TIMEOUT", "OUT_OF_MEMORY", "NODE_FAIL"       -> "$jobName failed ($state)"
            "CANCELLED"                                              -> "$jobName cancelled"
            else                                                     -> "$jobName: $state"
        }
        val isError = upper in setOf("FAILED", "TIMEOUT", "OUT_OF_MEMORY", "NODE_FAIL")
        nm.notify(
            notifId(jobId),
            NotificationCompat.Builder(context, CHANNEL_FINISHED)
                .setContentTitle(title)
                .setSmallIcon(R.drawable.ic_notification)
                .setAutoCancel(true)
                .setPriority(if (isError) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
                .build(),
        )
    }

    private fun notifId(jobId: String): Int = jobId.toIntOrNull() ?: jobId.hashCode()

    companion object {
        const val CHANNEL_RUNNING = "slurmdroid_running_jobs"
        const val CHANNEL_FINISHED = "slurmdroid_job_finished"
    }
}
