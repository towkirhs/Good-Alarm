package com.goodalarm.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.goodalarm.data.Item
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@Composable
fun GoodAlarmApp(
    header: @Composable () -> Unit,
    vm: MainViewModel = viewModel(),
) {
    val all by vm.items.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selectedDay by remember { mutableStateOf(LocalDate.now()) }
    var editing by remember { mutableStateOf<Item?>(null) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text("Tasks") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                    label = { Text("Calendar") },
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = newItem(if (tab == 1) selectedDay else null) }) {
                Icon(Icons.Filled.Add, contentDescription = "Add")
            }
        },
    ) { padding ->
        Crossfade(targetState = tab, modifier = Modifier.padding(padding), label = "tab") { page ->
            when (page) {
                0 -> TaskList(
                    all = all,
                    header = header,
                    onOpen = { editing = it },
                    onToggle = vm::toggleDone,
                )
                else -> CalendarScreen(
                    all = all,
                    selected = selectedDay,
                    onSelect = { selectedDay = it },
                    onOpen = { editing = it },
                    onToggle = vm::toggleDone,
                )
            }
        }
    }

    editing?.let { item ->
        EditItemDialog(
            item = item,
            onDismiss = { editing = null },
            onSave = {
                vm.save(it)
                editing = null
            },
            onDelete = {
                vm.delete(it)
                editing = null
            },
        )
    }
}

/** A new item: next full hour today, or 9 AM on the day picked in the calendar. */
private fun newItem(day: LocalDate?): Item {
    val now = LocalDateTime.now()
    val at = if (day != null && day != now.toLocalDate()) {
        day.atTime(9, 0)
    } else {
        now.plusHours(1).withMinute(0).withSecond(0).withNano(0)
    }
    return Item(title = "", timeMillis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
}
