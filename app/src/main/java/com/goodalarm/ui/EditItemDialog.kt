@file:OptIn(ExperimentalMaterial3Api::class)

package com.goodalarm.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.goodalarm.data.Item
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateButtonFormat = DateTimeFormatter.ofPattern("EEE d MMM yyyy")
private val timeButtonFormat = DateTimeFormatter.ofPattern("h:mm a")

@Composable
fun EditItemDialog(
    item: Item,
    knownTags: List<String>,
    onDismiss: () -> Unit,
    onSave: (Item) -> Unit,
    onDelete: (Item) -> Unit,
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val start = remember(item.id) { Instant.ofEpochMilli(item.timeMillis).atZone(zone).toLocalDateTime() }

    var title by remember { mutableStateOf(item.title) }
    var notes by remember { mutableStateOf(item.notes) }
    var date by remember { mutableStateOf(start.toLocalDate()) }
    var time by remember { mutableStateOf(start.toLocalTime().withSecond(0).withNano(0)) }
    var alarmOn by remember { mutableStateOf(item.alarmOn) }
    var repeatDaily by remember { mutableStateOf(item.repeatDaily) }
    var sound by remember { mutableStateOf(item.soundUri) }
    var tag by remember { mutableStateOf(item.tag) }
    var newTag by remember { mutableStateOf("") }
    val tagChoices = (knownTags + tag).filter { it.isNotBlank() }.distinct()

    // Pick any audio file on the phone; keep permission to read it after restarts.
    val pickSound = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            sound = uri.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item.id == 0L) "New" else "Edit") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            DatePickerDialog(
                                context,
                                { _, y, m, d -> date = LocalDate.of(y, m + 1, d) },
                                date.year, date.monthValue - 1, date.dayOfMonth,
                            ).show()
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(date.format(dateButtonFormat)) }
                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, h, min -> time = LocalTime.of(h, min) },
                                time.hour, time.minute, DateFormat.is24HourFormat(context),
                            ).show()
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(time.format(timeButtonFormat)) }
                }
                SwitchRow("Alarm (loud)", alarmOn) { alarmOn = it }
                SwitchRow("Repeat every day", repeatDaily) { repeatDaily = it }
                Text("Tag", style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = tag.isBlank(), onClick = { tag = "" }, label = { Text("None") })
                    tagChoices.forEach { choice ->
                        FilterChip(
                            selected = tag == choice,
                            onClick = { tag = choice },
                            label = { Text(choice) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newTag,
                        onValueChange = { newTag = it },
                        label = { Text("New tag") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            if (newTag.isNotBlank()) {
                                tag = newTag.trim()
                                newTag = ""
                            }
                        },
                    ) { Text("Add") }
                }
                if (alarmOn) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (sound == null) "Sound: default alarm" else "Sound: custom file",
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { pickSound.launch(arrayOf("audio/*")) }) { Text("Choose") }
                        if (sound != null) {
                            TextButton(onClick = { sound = null }) { Text("Reset") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val millis = LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()
                val movedToFuture = millis != item.timeMillis && millis > System.currentTimeMillis()
                onSave(
                    item.copy(
                        title = title.trim().ifEmpty { "Untitled" },
                        notes = notes.trim(),
                        timeMillis = millis,
                        alarmOn = alarmOn,
                        repeatDaily = repeatDaily,
                        soundUri = sound,
                        tag = tag,
                        // Moving a finished item to a new future time makes it active again.
                        done = if (movedToFuture) false else item.done,
                        missed = if (movedToFuture) false else item.missed,
                    ),
                )
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (item.id != 0L) {
                    TextButton(onClick = { onDelete(item) }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
