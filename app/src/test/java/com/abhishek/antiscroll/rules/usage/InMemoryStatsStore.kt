package com.abhishek.antiscroll.rules.usage

import java.time.LocalDate

/** Test double for [StatsStore]: keeps stats in a map. */
class InMemoryStatsStore : StatsStore {
    private val dates = mutableMapOf<LocalDate, DayStats>()

    override fun load(date: LocalDate): DayStats? = dates[date]

    override fun save(stats: DayStats) {
        dates[stats.date] = stats
    }

    override fun firstDay(): LocalDate? = dates.keys.minOrNull()
}
