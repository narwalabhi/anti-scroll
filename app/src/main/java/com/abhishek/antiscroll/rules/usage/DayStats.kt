package com.abhishek.antiscroll.rules.usage

import com.abhishek.antiscroll.rules.profile.Surface
import java.time.LocalDate

/**
 * One calendar date's counters, midnight to midnight: what the status screen reads as "Today" and
 * in its history. The limits run on [DayUsage], 4 AM to 4 AM (docs/LLD.md, Calendar stats).
 * Counts and timestamps only.
 */
data class DayStats(
    val date: LocalDate,
    val opens: Int = 0,
    val millisBySurface: Map<Surface, Long> = emptyMap(),
    val swipesBySurface: Map<Surface, Int> = emptyMap(),
    /** The longest single open so far ("longest stretch"); the part of a visit after midnight counts on the new date. */
    val longestVisitMillis: Long = 0L,
    /** Time by local hour of day, 0 to 23 ("When you scroll"); empty for dates saved before it existed. */
    val millisByHour: List<Long> = emptyList(),
    /** True once any time that date was counted with enforcement on: a guarding day, not a baseline day. */
    val guarded: Boolean = false,
) {
    val totalMillis: Long get() = millisBySurface.values.sum()

    /** [millisByHour] as 24 buckets, zeros where nothing was recorded. */
    fun hours(): List<Long> = List(HOURS) { millisByHour.getOrElse(it) { 0L } }

    companion object {
        /** A day from before calendar stats existed, shown from its limit-window counters. */
        fun from(usage: DayUsage) = DayStats(usage.day, usage.opens, usage.millisBySurface, usage.swipesBySurface)

        const val HOURS = 24
    }
}

/** Reads and writes [DayStats], one per calendar date. */
interface StatsStore {
    fun load(date: LocalDate): DayStats?
    fun save(stats: DayStats)

    /** The earliest date with saved stats, or null if none. */
    fun firstDay(): LocalDate?
}
