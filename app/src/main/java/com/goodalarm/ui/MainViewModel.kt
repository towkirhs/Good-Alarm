package com.goodalarm.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.goodalarm.alarm.AlarmScheduler
import com.goodalarm.data.AppDatabase
import com.goodalarm.data.Item
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).items()

    val items: StateFlow<List<Item>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(item: Item) {
        viewModelScope.launch {
            val saved = if (item.id == 0L) {
                item.copy(id = dao.insert(item))
            } else {
                dao.update(item)
                item
            }
            AlarmScheduler.schedule(getApplication<Application>(), saved)
        }
    }

    fun delete(item: Item) {
        viewModelScope.launch {
            AlarmScheduler.cancel(getApplication<Application>(), item.id)
            dao.delete(item)
        }
    }

    /** Ticking a daily item moves it to its next day instead of marking it done forever. */
    fun toggleDone(item: Item) {
        viewModelScope.launch {
            val updated = if (!item.done && item.repeatDaily) {
                val after = maxOf(System.currentTimeMillis(), item.timeMillis)
                item.copy(timeMillis = AlarmScheduler.nextOccurrence(item.timeMillis, after), missed = false)
            } else {
                item.copy(done = !item.done, missed = false)
            }
            dao.update(updated)
            AlarmScheduler.schedule(getApplication<Application>(), updated)
        }
    }
}
