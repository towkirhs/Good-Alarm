package com.goodalarm.planner

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Something that takes up time. A duration of 0 is a point in time:
 * it never conflicts with anything and adds no workload.
 */
data class Block(
    val id: Long,
    val title: String,
    val startMillis: Long,
    val durationMillis: Long,
) {
    val endMillis: Long get() = startMillis + durationMillis
}

/** Pairs of blocks that overlap. Blocks that only touch (one ends as the next starts) do not. */
fun findOverlaps(blocks: List<Block>): List<Pair<Block, Block>> {
    val timed = blocks
        .filter { it.durationMillis > 0 }
        .sortedWith(compareBy({ it.startMillis }, { it.id }))
    val result = mutableListOf<Pair<Block, Block>>()
    for (i in timed.indices) {
        for (j in i + 1 until timed.size) {
            // Sorted by start, so nothing after this can overlap timed[i] either.
            if (timed[j].startMillis >= timed[i].endMillis) break
            result += timed[i] to timed[j]
        }
    }
    return result
}

/**
 * Days whose blocks add up to more than [capacityMillis]. Overlapping blocks both count,
 * since you would need the time for both. A block belongs to the day it starts on.
 */
fun overloadedDays(blocks: List<Block>, zone: ZoneId, capacityMillis: Long): List<LocalDate> =
    blocks
        .groupBy { Instant.ofEpochMilli(it.startMillis).atZone(zone).toLocalDate() }
        .filterValues { day -> day.sumOf { it.durationMillis } > capacityMillis }
        .keys
        .sorted()
