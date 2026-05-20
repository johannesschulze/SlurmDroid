package org.slurmdroid.core

import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PollStateHolder @Inject constructor() {
    /** Millisecond timestamp of the last completed poll. 0 before the first poll. */
    val lastPollCompletedAt = MutableStateFlow(0L)

    val intervalMs: Long = POLL_INTERVAL_MS

    companion object {
        const val POLL_INTERVAL_MS = 10_000L
    }
}
