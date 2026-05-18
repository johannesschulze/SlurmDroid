package org.slurmdroid.service

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules a burst of short-interval polls after a user action (submit/cancel).
 * The immediate poll at t=0 is handled inline by the caller; this schedules
 * the remaining steps of the [0, 5, 10, 20, 60] second backoff sequence.
 */
@Singleton
class PollScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun scheduleBackoffAfterAction() {
        val wm = WorkManager.getInstance(context)
        // Cancel any in-flight backoff from a previous action before rescheduling
        wm.cancelAllWorkByTag(BACKOFF_TAG)
        listOf(5L, 10L, 20L, 60L).forEach { delaySecs ->
            wm.enqueue(
                OneTimeWorkRequestBuilder<PollWorker>()
                    .setInitialDelay(delaySecs, TimeUnit.SECONDS)
                    .addTag(BACKOFF_TAG)
                    .build()
            )
        }
    }

    companion object {
        const val BACKOFF_TAG = "slurmdroid_backoff_poll"
    }
}
