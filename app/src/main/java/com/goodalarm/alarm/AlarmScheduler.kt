package com.goodalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.goodalarm.MainActivity
import com.goodalarm.data.AppDatabase
import com.goodalarm.data.Item
import java.time.Instant
import java.time.ZoneId

/** Talks to Android's alarm clock. One pending alarm per item. */
object AlarmScheduler {
    const val EXTRA_ID = "item_id"
    const val EXTRA_ATTEMPT = "attempt"

    /** An ignored alarm rings at most this many times in total. */
    const val MAX_RINGS = 5

    /** Snooze and re-ring gap. */
    const val RERING_MS = 5 * 60 * 1000L

    /** First daily occurrence strictly after [after]. */
    fun nextOccurrence(timeMillis: Long, after: Long = System.currentTimeMillis()): Long {
        var t = Instant.ofEpochMilli(timeMillis).atZone(ZoneId.systemDefault())
        while (t.toInstant().toEpochMilli() <= after) t = t.plusDays(1)
        return t.toInstant().toEpochMilli()
    }

    /** When this item should next ring, or null if it shouldn't. */
    fun nextTrigger(item: Item, now: Long = System.currentTimeMillis()): Long? {
        if (!item.alarmOn) return null
        if (item.done && !item.repeatDaily) return null
        if (item.timeMillis > now) return item.timeMillis
        return if (item.repeatDaily) nextOccurrence(item.timeMillis, now) else null
    }

    /** Sets (or clears) the alarm to match the item. */
    fun schedule(context: Context, item: Item) {
        val at = nextTrigger(item)
        if (at == null) cancel(context, item.id) else setAt(context, item.id, at, attempt = 0)
    }

    fun setAt(context: Context, id: Long, atMillis: Long, attempt: Int) {
        val am = context.getSystemService(AlarmManager::class.java)
        val fire = firePendingIntent(context, id, attempt)
        val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (canExact) {
            // setAlarmClock = a real alarm clock: exact, wakes the phone, ignores battery saver.
            val showApp = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(atMillis, showApp), fire)
        } else {
            // Fallback if exact alarms are switched off in settings (may be a few minutes late).
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, fire)
        }
    }

    fun cancel(context: Context, id: Long) {
        context.getSystemService(AlarmManager::class.java)
            .cancel(firePendingIntent(context, id, 0))
    }

    /** After a reboot every alarm is gone, so set them all again. */
    suspend fun rescheduleAll(context: Context) {
        AppDatabase.get(context).items().getAll().forEach { schedule(context, it) }
    }

    // Request code = item id, so each item has exactly one pending alarm (extras are updated).
    private fun firePendingIntent(context: Context, id: Long, attempt: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_ATTEMPT, attempt)
        return PendingIntent.getBroadcast(
            context, id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
