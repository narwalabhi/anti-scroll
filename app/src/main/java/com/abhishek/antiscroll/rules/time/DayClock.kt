package com.abhishek.antiscroll.rules.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Maps a moment to the AntiScroll day it belongs to. A day runs from [resetHour] local time
 * to [resetHour] the next day, so 3:59 AM still counts as the previous day.
 *
 * Works on local wall-clock time, so a daylight-saving change does not move the boundary.
 * Ignoring a clock that moves backward is up to the caller, which knows the last time it saw.
 */
class DayClock(private val resetHour: Int = 4) {

    init {
        require(resetHour in 0..23) { "resetHour must be 0..23, was $resetHour" }
    }

    fun dayOf(at: Instant, zone: ZoneId): LocalDate =
        at.atZone(zone).toLocalDateTime().minusHours(resetHour.toLong()).toLocalDate()

    /** When the day containing [at] ends: the next reset hour. */
    fun nextReset(at: Instant, zone: ZoneId): Instant =
        dayOf(at, zone).plusDays(1).atTime(resetHour, 0).atZone(zone).toInstant()
}
