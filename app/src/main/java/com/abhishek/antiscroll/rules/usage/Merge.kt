package com.abhishek.antiscroll.rules.usage

import com.abhishek.antiscroll.rules.profile.Surface
import java.time.Instant

/**
 * Combining the same day counted in two places: a restored backup and what was counted since a
 * reinstall (docs/LLD.md, Backup folder). The two cover different stretches of the day, so time,
 * opens and swipes add up; the rest takes whichever is further along.
 */
operator fun DayStats.plus(other: DayStats): DayStats {
    require(date == other.date) { "different dates: $date and ${other.date}" }
    return DayStats(
        date = date,
        opens = opens + other.opens,
        millisBySurface = millisBySurface.sum(other.millisBySurface),
        swipesBySurface = swipesBySurface.sum(other.swipesBySurface),
        longestVisitMillis = maxOf(longestVisitMillis, other.longestVisitMillis),
        millisByHour = if (millisByHour.isEmpty() && other.millisByHour.isEmpty()) {
            emptyList()
        } else {
            hours().zip(other.hours(), Long::plus)
        },
        guarded = guarded || other.guarded,
    )
}

operator fun DayUsage.plus(other: DayUsage): DayUsage {
    require(day == other.day) { "different days: $day and ${other.day}" }
    return DayUsage(
        day = day,
        opens = opens + other.opens,
        millisBySurface = millisBySurface.sum(other.millisBySurface),
        swipesBySurface = swipesBySurface.sum(other.swipesBySurface),
        overridesUsed = maxOf(overridesUsed, other.overridesUsed),
        cooldownUntil = later(cooldownUntil, other.cooldownUntil),
        breakUntil = later(breakUntil, other.breakUntil),
        session = listOfNotNull(session, other.session).maxByOrNull { it.allowedUntil },
    )
}

private fun later(a: Instant?, b: Instant?): Instant? = listOfNotNull(a, b).maxOrNull()

@JvmName("sumLong")
private fun Map<Surface, Long>.sum(other: Map<Surface, Long>) =
    (keys + other.keys).associateWith { (this[it] ?: 0L) + (other[it] ?: 0L) }

@JvmName("sumInt")
private fun Map<Surface, Int>.sum(other: Map<Surface, Int>) =
    (keys + other.keys).associateWith { (this[it] ?: 0) + (other[it] ?: 0) }
