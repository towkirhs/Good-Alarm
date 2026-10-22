package com.goodalarm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.goodalarm.data.Item
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy")
private val dayFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM")

@Composable
fun CalendarScreen(
    all: List<Item>,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onOpen: (Item) -> Unit,
    onToggle: (Item) -> Unit,
) {
    var month by remember { mutableStateOf(YearMonth.from(selected)) }

    // One-time items sit on their own date; daily items appear on every day from their start.
    val byDay = remember(all) { all.filter { !it.repeatDaily }.groupBy { it.date() } }
    val daily = remember(all) { all.filter { it.repeatDaily } }
    fun itemsOn(day: LocalDate): List<Item> =
        byDay[day].orEmpty() + daily.filter { !it.date().isAfter(day) }

    val dayItems = itemsOn(selected).sortedBy { it.timeMillis }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
    ) {
        item(key = "month_header") {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = month.minusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
                }
                Text(
                    month.format(monthFormat),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { month = month.plusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                }
            }
        }
        item(key = "grid") {
            MonthGrid(
                month = month,
                selected = selected,
                hasItems = { itemsOn(it).isNotEmpty() },
                onSelect = { day ->
                    onSelect(day)
                    if (YearMonth.from(day) != month) month = YearMonth.from(day)
                },
            )
        }
        item(key = "day_title") {
            Text(
                selected.format(dayFormat),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
        }
        if (dayItems.isEmpty()) {
            item(key = "day_empty") {
                Text(
                    "Nothing on this day. Tap + to add something.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(dayItems, key = { it.id }) { item ->
            ItemRow(item, showDate = false, onOpen = onOpen, onToggle = onToggle)
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    selected: LocalDate,
    hasItems: (LocalDate) -> Boolean,
    onSelect: (LocalDate) -> Unit,
) {
    val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val first = month.atDay(1)
    val offset = (first.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val start = first.minusDays(offset.toLong())
    val today = LocalDate.now()

    Column {
        Row(Modifier.fillMaxWidth()) {
            for (i in 0 until 7) {
                Text(
                    firstDayOfWeek.plus(i.toLong()).getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        for (week in 0 until 6) {
            Row(Modifier.fillMaxWidth()) {
                for (d in 0 until 7) {
                    val day = start.plusDays((week * 7 + d).toLong())
                    DayCell(
                        day = day,
                        inMonth = day.month == month.month,
                        isSelected = day == selected,
                        isToday = day == today,
                        hasItems = hasItems(day),
                        onClick = { onSelect(day) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    inMonth: Boolean,
    isSelected: Boolean,
    isToday: Boolean,
    hasItems: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val textColor = when {
        isSelected -> colors.onPrimary
        !inMonth -> colors.onSurface.copy(alpha = 0.35f)
        isToday -> colors.primary
        else -> colors.onSurface
    }
    val dotColor = when {
        !hasItems -> Color.Transparent
        isSelected -> colors.onPrimary
        else -> colors.primary
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(3.dp)
            .clip(CircleShape)
            .background(if (isSelected) colors.primary else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(
                day.dayOfMonth.toString(),
                color = textColor,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.bodyMedium,
            )
            Box(
                Modifier
                    .padding(top = 2.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
        }
    }
}
