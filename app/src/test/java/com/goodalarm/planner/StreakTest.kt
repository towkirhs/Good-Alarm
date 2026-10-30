package com.goodalarm.planner

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class StreakTest {
    private val everyDay = DayOfWeek.values().toSet()
    private val monWedFri = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)

    // 2026-10-26 is a Monday, so the 30th is a Friday.
    private val mon = LocalDate.of(2026, 10, 26)
    private val tue = mon.plusDays(1)
    private val wed = mon.plusDays(2)
    private val fri = mon.plusDays(4)

    @Test
    fun noCompletionsMeansNoStreak() {
        assertEquals(0, currentStreak(emptySet(), everyDay, fri))
    }

    @Test
    fun noScheduledDaysMeansNoStreak() {
        assertEquals(0, currentStreak(setOf(fri), emptySet(), fri))
    }

    @Test
    fun consecutiveDailyCompletionsCount() {
        assertEquals(3, currentStreak(setOf(fri, fri.minusDays(1), fri.minusDays(2)), everyDay, fri))
    }

    @Test
    fun todayNotDoneYetDoesNotBreakTheStreak() {
        assertEquals(2, currentStreak(setOf(fri.minusDays(1), fri.minusDays(2)), everyDay, fri))
    }

    @Test
    fun missedDayEndsTheStreak() {
        // Done today and 2 days ago, but missed yesterday.
        assertEquals(1, currentStreak(setOf(fri, fri.minusDays(2)), everyDay, fri))
    }

    @Test
    fun daysOffAreSkippedForWeeklySchedules() {
        // Mon, Wed, Fri all done; Tue and Thu are not scheduled.
        assertEquals(3, currentStreak(setOf(mon, wed, fri), monWedFri, fri))
    }

    @Test
    fun missedScheduledDayBreaksWeeklyStreak() {
        // Wed was missed.
        assertEquals(1, currentStreak(setOf(mon, fri), monWedFri, fri))
    }

    @Test
    fun completionOnAnUnscheduledDayIsIgnored() {
        // Today is Tuesday (not scheduled); Monday was missed, Tuesday's completion does not count.
        assertEquals(0, currentStreak(setOf(tue), monWedFri, tue))
    }
}
