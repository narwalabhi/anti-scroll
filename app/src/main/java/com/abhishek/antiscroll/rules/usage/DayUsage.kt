package com.abhishek.antiscroll.rules.usage

import com.abhishek.antiscroll.rules.profile.Surface
import java.time.Instant
import java.time.LocalDate

/** One day's counters, the day running from the reset hour to the next. Counts and timestamps only. */
data class DayUsage(
    val day: LocalDate,
    val opens: Int = 0,
    val millisBySurface: Map<Surface, Long> = emptyMap(),
    val swipesBySurface: Map<Surface, Int> = emptyMap(),
    val overridesUsed: Int = 0,
    val cooldownUntil: Instant? = null,
    /** Reopening Instagram before this shows the time's-up screen and the ladder, not a new session. */
    val breakUntil: Instant? = null,
    /** The session in progress, if any, so it survives the app restarting; null once it ends. */
    val session: Session? = null,
) {
    val totalMillis: Long get() = millisBySurface.values.sum()
}
