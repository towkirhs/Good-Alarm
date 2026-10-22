package com.goodalarm.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The one thing in Good Alarm. Depending on its fields it shows up as a task,
 * on the calendar, and/or as a ringing alarm.
 */
@Entity(tableName = "items")
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    /** When it happens / rings, in epoch milliseconds. For daily items: the next occurrence. */
    val timeMillis: Long,
    val alarmOn: Boolean = true,
    val repeatDaily: Boolean = false,
    /** Custom sound picked from the phone, or null for the default alarm sound. */
    val soundUri: String? = null,
    val done: Boolean = false,
    /** True when the alarm rang the maximum number of times and nobody answered. */
    val missed: Boolean = false,
    /** Tag such as "Home" or "Work"; empty = no tag. */
    val tag: String = "",
) {
    fun date(): LocalDate =
        Instant.ofEpochMilli(timeMillis).atZone(ZoneId.systemDefault()).toLocalDate()
}
