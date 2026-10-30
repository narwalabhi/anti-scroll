package com.abhishek.antiscroll.rules.export

import java.time.LocalDate

/**
 * When to remind the user to share their numbers (docs/SPEC.md, Sharing with friends): once the
 * baseline week is done, and two weeks after guarding first ran. Each reminder shows once.
 */
object ExportReminders {

    enum class Kind { BASELINE_DONE, GUARDING_TWO_WEEKS }

    const val BASELINE_DAYS = 7L
    const val GUARDING_DAYS = 14L

    /** The reminder due on [today], if any; [firstGuardedDay] is null until guarding has run. */
    fun due(firstDay: LocalDate, firstGuardedDay: LocalDate?, today: LocalDate, shown: Set<Kind>): Kind? = when {
        Kind.BASELINE_DONE !in shown && !today.isBefore(firstDay.plusDays(BASELINE_DAYS)) -> Kind.BASELINE_DONE
        Kind.GUARDING_TWO_WEEKS !in shown && firstGuardedDay != null &&
            !today.isBefore(firstGuardedDay.plusDays(GUARDING_DAYS)) -> Kind.GUARDING_TWO_WEEKS
        else -> null
    }
}
