@file:OptIn(ExperimentalMaterial3Api::class)

package com.goodalarm.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.goodalarm.data.Item
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val whenFormat = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a")
private val timeFormat = DateTimeFormatter.ofPattern("h:mm a")

@Composable
fun TaskList(
    all: List<Item>,
    header: @Composable () -> Unit,
    onOpen: (Item) -> Unit,
    onToggle: (Item) -> Unit,
) {
    val now = System.currentTimeMillis()
    val today = LocalDate.now()
    val active = all.filter { !it.done }
    // Each item lands in exactly one section (list keys must be unique).
    val overdue = active.filter { it.timeMillis < now }
    val todayItems = active.filter { it.timeMillis >= now && it.date() == today }
    val upcoming = active.filter { it.timeMillis >= now && it.date() > today }
    val done = all.filter { it.done }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
    ) {
        item(key = "header") { header() }
        item(key = "title") {
            Text(
                "Good Alarm",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
        }
        section("Overdue", overdue, showDate = true, onOpen, onToggle)
        section("Today", todayItems, showDate = false, onOpen, onToggle)
        section("Upcoming", upcoming, showDate = true, onOpen, onToggle)
        section("Done", done, showDate = true, onOpen, onToggle)
        if (all.isEmpty()) {
            item(key = "empty") {
                Text(
                    "Nothing yet. Tap + to add a task or alarm.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 32.dp),
                )
            }
        }
    }
}

private fun LazyListScope.section(
    title: String,
    list: List<Item>,
    showDate: Boolean,
    onOpen: (Item) -> Unit,
    onToggle: (Item) -> Unit,
) {
    if (list.isEmpty()) return
    item(key = "section_$title") {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .animateItem()
                .padding(top = 16.dp, bottom = 8.dp),
        )
    }
    items(list, key = { it.id }) { item ->
        ItemRow(item, showDate, onOpen, onToggle, Modifier.animateItem())
    }
}

/** One row: checkbox, title, time, alarm bell. Shared by the task list and the calendar. */
@Composable
fun ItemRow(
    item: Item,
    showDate: Boolean,
    onOpen: (Item) -> Unit,
    onToggle: (Item) -> Unit,
    modifier: Modifier = Modifier,
) {
    val time = Instant.ofEpochMilli(item.timeMillis).atZone(ZoneId.systemDefault())
    val details = buildString {
        append(if (showDate) time.format(whenFormat) else time.format(timeFormat))
        if (item.repeatDaily) append("  ·  every day")
        if (item.missed) append("  ·  missed")
    }
    Card(
        onClick = { onOpen(item) },
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            Modifier.padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = item.done, onCheckedChange = { onToggle(item) })
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (item.done) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    details,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (item.alarmOn) {
                Icon(
                    Icons.Filled.Notifications,
                    contentDescription = "Alarm on",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
