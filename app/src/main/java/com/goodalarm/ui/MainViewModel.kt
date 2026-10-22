package com.goodalarm.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.goodalarm.data.AppDatabase
import com.goodalarm.data.Item
import com.goodalarm.data.Repo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).items()

    val items: StateFlow<List<Item>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(item: Item) {
        viewModelScope.launch { Repo.save(getApplication<Application>(), item) }
    }

    fun delete(item: Item) {
        viewModelScope.launch { Repo.delete(getApplication<Application>(), item) }
    }

    fun toggleDone(item: Item) {
        viewModelScope.launch { Repo.toggleDone(getApplication<Application>(), item) }
    }
}
