package com.abhishek.antiscroll.rules.budget

import com.abhishek.antiscroll.rules.profile.Surface
import com.abhishek.antiscroll.rules.usage.DayUsage
import java.time.Duration

/**
 * How much Instagram a day allows: [dailyTotal] across every surface, plus optional per-surface
 * limits. Built from the saved [com.abhishek.antiscroll.rules.settings.AppSettings].
 */
data class Budgets(
    val dailyTotal: Duration,
    val perSurface: Map<Surface, Duration> = emptyMap(),
) {
    init {
        require(!dailyTotal.isNegative && !dailyTotal.isZero) { "dailyTotal must be positive" }
    }

    /** What's left of today's total; zero once it's spent. */
    fun dailyLeft(usage: DayUsage): Duration =
        dailyTotal.minusMillis(usage.totalMillis).let { if (it.isNegative) Duration.ZERO else it }

    companion object {
        /** Debug "short timers": a 5-minute day and 1-minute surface limits, so blocks can be tried any time. */
        fun shortForTesting(budgets: Budgets) = budgets.copy(
            dailyTotal = Duration.ofMinutes(5),
            perSurface = budgets.perSurface.mapValues { Duration.ofMinutes(1) },
        )
    }
}
