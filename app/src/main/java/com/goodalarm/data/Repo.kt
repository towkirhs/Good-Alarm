package com.goodalarm.data

import android.content.Context
import com.goodalarm.alarm.AlarmScheduler
import com.goodalarm.widget.WidgetRefresher

/**
 * Every change to items goes through here, so the alarm and the home-screen widget
 * always stay in sync whether the change came from the app or the widget.
 */
object Repo {

    suspend fun save(context: Context, item: Item): Item {
        val dao = AppDatabase.get(context).items()
        val saved = if (item.id == 0L) {
            item.copy(id = dao.insert(item))
        } else {
            dao.update(item)
            item
        }
        AlarmScheduler.schedule(context, saved)
        WidgetRefresher.refresh(context)
        return saved
    }

    suspend fun delete(context: Context, item: Item) {
        AlarmScheduler.cancel(context, item.id)
        AppDatabase.get(context).items().delete(item)
        WidgetRefresher.refresh(context)
    }

    /** Ticking a daily item moves it to its next day instead of marking it done forever. */
    suspend fun toggleDone(context: Context, item: Item) {
        val updated = if (!item.done && item.repeatDaily) {
            val after = maxOf(System.currentTimeMillis(), item.timeMillis)
            item.copy(timeMillis = AlarmScheduler.nextOccurrence(item.timeMillis, after), missed = false)
        } else {
            item.copy(done = !item.done, missed = false)
        }
        AppDatabase.get(context).items().update(updated)
        AlarmScheduler.schedule(context, updated)
        WidgetRefresher.refresh(context)
    }
}
