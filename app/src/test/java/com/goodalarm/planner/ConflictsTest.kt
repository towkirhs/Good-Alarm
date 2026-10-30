package com.goodalarm.planner

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class ConflictsTest {
    private val hour = 3_600_000L
    private val minute = 60_000L
    private val friday = LocalDate.of(2026, 10, 30)
    private val saturday = friday.plusDays(1)

    private fun at(day: LocalDate, h: Int) = day.atTime(h, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun block(id: Long, startH: Int, endH: Int, day: LocalDate = friday) =
        Block(id, "b$id", at(day, startH), (endH - startH) * hour)

    private fun point(id: Long, h: Int) = Block(id, "p$id", at(friday, h), 0)

    private fun ids(pairs: List<Pair<Block, Block>>) = pairs.map { it.first.id to it.second.id }

    // --- overlaps ---

    @Test
    fun overlappingBlocksAreReportedOnce() {
        val result = findOverlaps(listOf(block(1, 15, 17), block(2, 16, 18)))
        assertEquals(listOf(1L to 2L), ids(result))
    }

    @Test
    fun blocksThatOnlyTouchDoNotOverlap() {
        assertEquals(emptyList<Pair<Block, Block>>(), findOverlaps(listOf(block(1, 15, 16), block(2, 16, 17))))
    }

    @Test
    fun itemWithNoDurationNeverConflicts() {
        assertEquals(emptyList<Pair<Block, Block>>(), findOverlaps(listOf(block(1, 15, 17), point(2, 16))))
    }

    @Test
    fun identicalTimesOverlap() {
        assertEquals(listOf(1L to 2L), ids(findOverlaps(listOf(block(1, 9, 10), block(2, 9, 10)))))
    }

    @Test
    fun oneLongBlockCanOverlapSeveralOthers() {
        // 9-12 overlaps 10-11 and 11-13; 10-11 and 11-13 only touch.
        val result = findOverlaps(listOf(block(1, 9, 12), block(2, 10, 11), block(3, 11, 13)))
        assertEquals(listOf(1L to 2L, 1L to 3L), ids(result))
    }

    @Test
    fun resultDoesNotDependOnInputOrder() {
        val result = findOverlaps(listOf(block(2, 16, 18), block(1, 15, 17)))
        assertEquals(listOf(1L to 2L), ids(result))
    }

    // --- overload ---

    @Test
    fun dayOverCapacityIsOverloaded() {
        val blocks = listOf(block(1, 9, 12), block(2, 13, 17))
        assertEquals(listOf(friday), overloadedDays(blocks, ZoneOffset.UTC, 6 * hour))
    }

    @Test
    fun dayWithinCapacityIsNotOverloaded() {
        val blocks = listOf(block(1, 9, 12), block(2, 13, 16))
        assertEquals(emptyList<LocalDate>(), overloadedDays(blocks, ZoneOffset.UTC, 6 * hour))
    }

    @Test
    fun overlappingBlocksBothCountTowardDemand() {
        // Two identical 1h blocks need 2h in total, more than the 90 minutes available.
        val blocks = listOf(block(1, 9, 10), block(2, 9, 10))
        assertEquals(listOf(friday), overloadedDays(blocks, ZoneOffset.UTC, 90 * minute))
    }

    @Test
    fun daysAreNotAddedTogether() {
        val blocks = listOf(block(1, 9, 13, friday), block(2, 9, 13, saturday))
        assertEquals(emptyList<LocalDate>(), overloadedDays(blocks, ZoneOffset.UTC, 6 * hour))
    }

    @Test
    fun itemWithNoDurationAddsNoDemand() {
        assertEquals(emptyList<LocalDate>(), overloadedDays(listOf(point(1, 9)), ZoneOffset.UTC, 0))
    }
}
