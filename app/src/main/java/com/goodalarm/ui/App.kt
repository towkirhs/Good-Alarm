@file:OptIn(ExperimentalMaterial3Api::class)

package com.goodalarm.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.goodalarm.data.Item
import com.goodalarm.data.Tags
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Asks the app to open the "New" screen, e.g. from the widget's + button. */
data class AddRequest(val day: LocalDate, val tag: String)

@Composable
fun GoodAlarmApp(
    header: @Composable () -> Unit,
    addRequest: AddRequest? = null,
    onAddRequestHandled: () -> Unit = {},
    vm: MainViewModel = viewModel(),
) {
    val all by vm.items.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selectedDay by remember { mutableStateOf(LocalDate.now()) }
    var editing by remember { mutableStateOf<Item?>(null) }
    var tagFilter by rememberSaveable { mutableStateOf<String?>(null) }

    val tags = Tags.all(all)
    val shown = all.filter { Tags.matches(it, tagFilter) }

    LaunchedEffect(addRequest) {
        addRequest?.let {
            editing = newItem(it.day, it.tag)
            onAddRequestHandled()
        }
    }

    val topContent: @Composable () -> Unit = {
        Column {
            header()
            TagFilterRow(tags, tagFilter) { tagFilter = it }
        }
    }

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
            FloatingActionButton(onClick = {
                editing = newItem(if (tab == 1) selectedDay else null, tagFilter.orEmpty())
            }) {
                Icon(Icons.Filled.Add, contentDescription = "Add")
            }
        },
    ) { padding ->
        Crossfade(targetState = tab, modifier = Modifier.padding(padding), label = "tab") { page ->
            when (page) {
                0 -> TaskList(
                    all = shown,
                    filtered = tagFilter != null,
                    header = topContent,
                    onOpen = { editing = it },
                    onToggle = vm::toggleDone,
                )
                else -> CalendarScreen(
                    all = shown,
                    header = { TagFilterRow(tags, tagFilter) { tagFilter = it } },
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
            knownTags = tags,
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

/** All / Home / Work / … chips. Tap one to show only that tag. */
@Composable
fun TagFilterRow(tags: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("All") })
        tags.forEach { tag ->
            FilterChip(
                selected = selected == tag,
                onClick = { onSelect(if (selected == tag) null else tag) },
                label = { Text(tag) },
            )
        }
    }
}

/** A new item: next full hour today, or 9 AM on another chosen day. */
private fun newItem(day: LocalDate?, tag: String): Item {
    val now = LocalDateTime.now()
    val at = if (day != null && day != now.toLocalDate()) {
        day.atTime(9, 0)
    } else {
        now.plusHours(1).withMinute(0).withSecond(0).withNano(0)
    }
    return Item(
        title = "",
        timeMillis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        tag = tag,
    )
}
