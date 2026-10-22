package com.goodalarm.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.goodalarm.MainActivity
import com.goodalarm.R
import com.goodalarm.data.AppDatabase
import com.goodalarm.data.Item
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import java.time.format.TextStyle as DateTextStyle

private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy")
private val dayFormat = DateTimeFormatter.ofPattern("d EEE")
private val timeFormat = DateTimeFormatter.ofPattern("h:mm a")

/**
 * Home-screen widget: month calendar on the left, the chosen day's tasks on the right.
 * Top right: tag filter (tap to switch All → Home → Work → …), add, refresh, open app.
 * On a narrow widget only the day panel is shown.
 */
class CalendarWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dao = AppDatabase.get(context).items()
        val initial = dao.getAll()
        provideContent {
            // Live list: the widget redraws by itself while it is awake and data changes.
            val items by dao.observeAll().collectAsState(initial = initial)
            GlanceTheme {
                WidgetContent(items)
            }
        }
    }
}

@Composable
private fun WidgetContent(items: List<Item>) {
    val prefs = currentState<Preferences>()
    val today = LocalDate.now()
    val selected = prefs[WidgetKeys.DAY]?.let(LocalDate::ofEpochDay) ?: today
    val month = prefs[WidgetKeys.MONTH]?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
        ?: YearMonth.from(selected)
    val tag = prefs[WidgetKeys.TAG].orEmpty()

    // Same rule as the app: one-time items on their date, daily items on every day from their start.
    val shown = items.filter { tag.isEmpty() || it.tag == tag }
    val byDay = shown.filter { !it.repeatDaily }.groupBy { it.date() }
    val daily = shown.filter { it.repeatDaily }
    val itemsOn: (LocalDate) -> List<Item> = { day ->
        byDay[day].orEmpty() + daily.filter { !it.date().isAfter(day) }
    }

    val size = LocalSize.current
    val wide = size.width >= 280.dp
    // Six week rows share the height left after the header and weekday rows.
    val cellHeight = ((size.height.value - 24f - 32f - 18f) / 6f).coerceIn(22f, 48f).dp

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(24.dp)
            .padding(12.dp),
    ) {
        if (wide) {
            MonthPanel(
                modifier = GlanceModifier.defaultWeight(),
                month = month,
                selected = selected,
                today = today,
                hasItems = { itemsOn(it).isNotEmpty() },
                cellHeight = cellHeight,
            )
            Spacer(GlanceModifier.width(12.dp))
        }
        DayPanel(
            modifier = GlanceModifier.defaultWeight(),
            selected = selected,
            tag = tag,
            dayItems = itemsOn(selected).sortedBy { it.timeMillis },
        )
    }
}

// ---------- Left: month grid ----------

@Composable
private fun MonthPanel(
    modifier: GlanceModifier,
    month: YearMonth,
    selected: LocalDate,
    today: LocalDate,
    hasItems: (LocalDate) -> Boolean,
    cellHeight: androidx.compose.ui.unit.Dp,
) {
    val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val first = month.atDay(1)
    val offset = (first.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val start = first.minusDays(offset.toLong())

    Column(modifier = modifier) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().height(32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WidgetIcon(
                R.drawable.ic_w_chevron_left, "Previous month",
                actionRunCallback<ChangeMonthAction>(actionParametersOf(WidgetKeys.PARAM_DELTA to -1)),
            )
            Text(
                text = month.format(monthFormat).uppercase(),
                modifier = GlanceModifier.defaultWeight(),
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
            )
            WidgetIcon(
                R.drawable.ic_w_chevron_right, "Next month",
                actionRunCallback<ChangeMonthAction>(actionParametersOf(WidgetKeys.PARAM_DELTA to 1)),
            )
        }
        Row(modifier = GlanceModifier.fillMaxWidth().height(18.dp)) {
            for (i in 0 until 7) {
                val dow = firstDayOfWeek.plus(i.toLong())
                Text(
                    text = dow.getDisplayName(DateTextStyle.NARROW, Locale.getDefault()),
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }
        for (week in 0 until 6) {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                for (d in 0 until 7) {
                    val day = start.plusDays((week * 7 + d).toLong())
                    DayCell(
                        modifier = GlanceModifier.defaultWeight().height(cellHeight),
                        day = day,
                        inMonth = day.month == month.month,
                        isSelected = day == selected,
                        isToday = day == today,
                        hasItems = hasItems(day),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    modifier: GlanceModifier,
    day: LocalDate,
    inMonth: Boolean,
    isSelected: Boolean,
    isToday: Boolean,
    hasItems: Boolean,
) {
    val colors = GlanceTheme.colors
    val transparent = ColorProvider(Color.Transparent)
    val textColor = when {
        isSelected -> colors.onPrimary
        !inMonth -> colors.outline
        isToday -> colors.primary
        else -> colors.onSurface
    }
    Column(
        modifier = modifier.clickable(
            actionRunCallback<SelectDayAction>(actionParametersOf(WidgetKeys.PARAM_DAY to day.toEpochDay())),
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .size(26.dp)
                .cornerRadius(13.dp)
                .background(if (isSelected) colors.primary else transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.dayOfMonth.toString(),
                style = TextStyle(
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                ),
            )
        }
        Spacer(GlanceModifier.height(2.dp))
        Box(
            modifier = GlanceModifier
                .size(4.dp)
                .cornerRadius(2.dp)
                .background(if (hasItems && inMonth) colors.primary else transparent),
        ) {}
    }
}

// ---------- Right: chosen day ----------

@Composable
private fun DayPanel(
    modifier: GlanceModifier,
    selected: LocalDate,
    tag: String,
    dayItems: List<Item>,
) {
    val context = LocalContext.current
    val openApp: Action = actionStartActivity(Intent(context, MainActivity::class.java))
    val addIntent = Intent(context, MainActivity::class.java)
        .putExtra(MainActivity.EXTRA_ADD_DAY, selected.toEpochDay())
        .putExtra(MainActivity.EXTRA_ADD_TAG, tag)

    Column(modifier = modifier) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().height(32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selected.format(dayFormat),
                modifier = GlanceModifier.defaultWeight(),
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            TagChip(tag)
            WidgetIcon(R.drawable.ic_w_add, "Add", actionStartActivity(addIntent))
            WidgetIcon(R.drawable.ic_w_refresh, "Back to today", actionRunCallback<RefreshAction>())
            WidgetIcon(R.drawable.ic_w_settings, "Open Good Alarm", openApp)
        }
        Spacer(GlanceModifier.height(6.dp))
        if (dayItems.isEmpty()) {
            Text(
                text = if (tag.isEmpty()) "Nothing on this day" else "Nothing tagged $tag",
                modifier = GlanceModifier.padding(top = 8.dp, start = 4.dp),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
            )
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(dayItems, itemId = { it.id }) { item ->
                    WidgetItemRow(item, openApp)
                }
            }
        }
    }
}

/** Shows the active tag; tap to switch to the next one. */
@Composable
private fun TagChip(tag: String) {
    Row(
        modifier = GlanceModifier
            .height(28.dp)
            .cornerRadius(14.dp)
            .background(GlanceTheme.colors.secondaryContainer)
            .padding(horizontal = 10.dp)
            .clickable(actionRunCallback<CycleTagAction>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_w_tag),
            contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
            modifier = GlanceModifier.size(14.dp),
        )
        Spacer(GlanceModifier.width(4.dp))
        Text(
            text = tag.ifEmpty { "All" },
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onSecondaryContainer,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
private fun WidgetItemRow(item: Item, openApp: Action) {
    val time = Instant.ofEpochMilli(item.timeMillis).atZone(ZoneId.systemDefault()).format(timeFormat)
    val details = buildString {
        append(time)
        if (item.tag.isNotBlank()) append(" · ${item.tag}")
        if (item.missed) append(" · missed")
    }
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckBox(
            checked = item.done,
            onCheckedChange = actionRunCallback<ToggleDoneAction>(
                actionParametersOf(WidgetKeys.PARAM_ID to item.id),
            ),
        )
        Column(modifier = GlanceModifier.defaultWeight().clickable(openApp)) {
            Text(
                text = item.title,
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 14.sp,
                    textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None,
                ),
            )
            Text(
                text = details,
                maxLines = 1,
                style = TextStyle(
                    color = if (item.missed) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp,
                ),
            )
        }
        if (item.alarmOn) {
            Image(
                provider = ImageProvider(R.drawable.ic_w_alarm),
                contentDescription = "Alarm on",
                colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                modifier = GlanceModifier.size(16.dp),
            )
        }
    }
}

/** Round tappable icon. */
@Composable
private fun WidgetIcon(iconRes: Int, description: String, onClick: Action) {
    Box(
        modifier = GlanceModifier.size(32.dp).cornerRadius(16.dp).clickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = description,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface),
            modifier = GlanceModifier.size(22.dp),
        )
    }
}
