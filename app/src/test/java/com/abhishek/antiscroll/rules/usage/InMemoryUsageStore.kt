package com.abhishek.antiscroll.rules.usage

import java.time.LocalDate

/** Test double for [UsageStore]: keeps counters in a map. */
class InMemoryUsageStore : UsageStore {
    private val days = mutableMapOf<LocalDate, DayUsage>()

    override fun load(day: LocalDate): DayUsage? = days[day]

    override fun save(usage: DayUsage) {
        days[usage.day] = usage
    }

    override fun firstDay(): LocalDate? = days.keys.minOrNull()
}
