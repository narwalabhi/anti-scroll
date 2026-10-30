package com.abhishek.antiscroll.rules.usage

import java.time.LocalDate

/** Reads and writes the day's counters. Budget checks get this, never write access to settings. */
interface UsageStore {
    fun load(day: LocalDate): DayUsage?
    fun save(usage: DayUsage)

    /** The earliest day with saved counters, or null if none: when the baseline started. */
    fun firstDay(): LocalDate?
}
