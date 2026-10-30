package com.goodalarm.planner

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * How many scheduled days in a row were completed, counting back from [today].
 * Days that are not scheduled are skipped. Today does not break the streak until it is over,
 * so a scheduled-but-not-yet-done today is ignored. Completions on unscheduled days do not count.
 */
fun currentStreak(done: Set<LocalDate>, scheduledDays: Set<DayOfWeek>, today: LocalDate): Int {
    if (scheduledDays.isEmpty()) return 0
    var streak = 0
    var day = today
    while (true) {
        if (day.dayOfWeek in scheduledDays) {
            if (day in done) streak++ else if (day != today) return streak
        }
        day = day.minusDays(1)
    }
}
