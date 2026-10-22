package com.goodalarm.widget

import android.content.Context
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import com.goodalarm.data.AppDatabase
import com.goodalarm.data.Repo
import com.goodalarm.data.Tags
import java.time.LocalDate
import java.time.YearMonth

/** What each widget remembers (each widget on the home screen has its own copy). */
object WidgetKeys {
    val DAY = longPreferencesKey("selected_epoch_day")
    val MONTH = stringPreferencesKey("shown_month")
    val TAG = stringPreferencesKey("tag_filter")

    val PARAM_DAY = ActionParameters.Key<Long>("day")
    val PARAM_DELTA = ActionParameters.Key<Int>("delta")
    val PARAM_ID = ActionParameters.Key<Long>("item_id")
}

/** Connects the widget to Android. */
class CalendarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CalendarWidget()
}

/** Redraws every Good Alarm widget; called whenever tasks change in the app. */
object WidgetRefresher {
    suspend fun refresh(context: Context) {
        runCatching { CalendarWidget().updateAll(context) }
    }
}

/** Tap a day in the grid. */
class SelectDayAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val day = parameters[WidgetKeys.PARAM_DAY] ?: return
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[WidgetKeys.DAY] = day
            prefs[WidgetKeys.MONTH] = YearMonth.from(LocalDate.ofEpochDay(day)).toString()
        }
        CalendarWidget().update(context, glanceId)
    }
}

/** < and > arrows. */
class ChangeMonthAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val delta = parameters[WidgetKeys.PARAM_DELTA] ?: return
        updateAppWidgetState(context, glanceId) { prefs ->
            val selectedDay = prefs[WidgetKeys.DAY]?.let(LocalDate::ofEpochDay) ?: LocalDate.now()
            val current = prefs[WidgetKeys.MONTH]?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
                ?: YearMonth.from(selectedDay)
            prefs[WidgetKeys.MONTH] = current.plusMonths(delta.toLong()).toString()
        }
        CalendarWidget().update(context, glanceId)
    }
}

/** Tag chip: All → Home → Work → Personal → your own tags → All. */
class CycleTagAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val options = listOf("") + Tags.all(AppDatabase.get(context).items().getAll())
        updateAppWidgetState(context, glanceId) { prefs ->
            val index = options.indexOf(prefs[WidgetKeys.TAG].orEmpty())
            prefs[WidgetKeys.TAG] = options[(index + 1) % options.size]
        }
        CalendarWidget().update(context, glanceId)
    }
}

/** Refresh button: jump back to today. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs.remove(WidgetKeys.DAY)
            prefs.remove(WidgetKeys.MONTH)
        }
        CalendarWidget().update(context, glanceId)
    }
}

/** Tick a task right on the home screen. */
class ToggleDoneAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[WidgetKeys.PARAM_ID] ?: return
        val item = AppDatabase.get(context).items().get(id) ?: return
        Repo.toggleDone(context, item)
    }
}
