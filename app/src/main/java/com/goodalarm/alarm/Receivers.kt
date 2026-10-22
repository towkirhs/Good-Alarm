package com.goodalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Android calls this at alarm time; it hands over to the ringing service. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ring = Intent(context, AlarmService::class.java)
            .setAction(AlarmService.ACTION_RING)
            .putExtras(intent)
        ContextCompat.startForegroundService(context, ring)
    }
}

/** Reboot, app update, or clock change: put every alarm back. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AlarmScheduler.rescheduleAll(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}
