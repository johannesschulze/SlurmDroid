package org.slurmdroid.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Receives poll-request broadcasts from plugin UIs and forwards them to [SshForegroundService]. */
class PollRequestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SshForegroundService.pollNow(context)
    }
}
