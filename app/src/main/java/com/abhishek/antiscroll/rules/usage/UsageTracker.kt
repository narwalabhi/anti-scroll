package com.abhishek.antiscroll.rules.usage

import com.abhishek.antiscroll.rules.engine.GuardEvent
import com.abhishek.antiscroll.rules.profile.Surface
import com.abhishek.antiscroll.rules.time.Clock
import com.abhishek.antiscroll.rules.time.DayClock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Folds [GuardEvent]s into two sets of counters (docs/LLD.md, Calendar stats): the limit window
 * ([DayUsage], days from [dayClock]'s reset hour) and the calendar date ([DayStats], days from
 * [statsClock]'s, midnight). Every open, tick and swipe counts in both. Pure: it never reads the
 * time itself (events carry it) and never saves; [clock] is used only for the time zone.
 */
class UsageTracker(
    private val dayClock: DayClock,
    private val clock: Clock,
    private val statsClock: DayClock = DayClock(resetHour = 0),
) {

    /** Both sets of counters plus what's needed to count the next tick; [usage] and [stats] are saved. */
    data class State(
        val usage: DayUsage,
        val stats: DayStats,
        val isOpen: Boolean = false,
        val surface: Surface? = null,
        val lastTickAt: Instant? = null,
        /** Time counted in the current visit, on the current date. */
        val visitMillis: Long = 0L,
    )

    fun dayOf(at: Instant): LocalDate = dayClock.dayOf(at, clock.zone)

    fun dateOf(at: Instant): LocalDate = statsClock.dayOf(at, clock.zone)

    fun start(at: Instant, saved: DayUsage? = null, savedStats: DayStats? = null): State {
        val today = dayOf(at)
        val date = dateOf(at)
        return State(
            usage = saved?.takeIf { it.day == today } ?: DayUsage(today),
            stats = savedStats?.takeIf { it.date == date } ?: DayStats(date),
        )
    }

    fun next(state: State, event: GuardEvent): State = when (event) {
        is GuardEvent.InstagramOpened -> {
            val current = rollOver(state, event.at)
            if (current.isOpen) {
                current
            } else {
                current.copy(
                    usage = current.usage.copy(opens = current.usage.opens + 1),
                    stats = current.stats.copy(opens = current.stats.opens + 1),
                    isOpen = true,
                    lastTickAt = event.at,
                    visitMillis = 0L,
                )
            }
        }
        // The surface is forgotten too, so the next session reports its own surface from the start.
        GuardEvent.InstagramClosed -> state.copy(isOpen = false, surface = null, lastTickAt = null, visitMillis = 0L)
        is GuardEvent.SurfaceChanged -> state.copy(surface = event.surface)
        is GuardEvent.Swiped -> if (state.isOpen) countSwipe(state, event.surface) else state
        // While closed, a tick counts nothing but still starts a new day on time (the status screen sends one).
        is GuardEvent.Tick -> if (state.isOpen) tick(rollOver(state, event.at), event.at) else rollOver(state, event.at)
        // Screen choices change what the engine does, not what is counted.
        GuardEvent.PauseFinished,
        is GuardEvent.IntentionChosen,
        GuardEvent.LeaveChosen,
        is GuardEvent.OverrideRequested,
        is GuardEvent.ChallengeSubmitted,
        is GuardEvent.ChallengeCancelled,
        is GuardEvent.EndSessionRequested,
        -> state
    }

    private fun countSwipe(state: State, surface: Surface): State = state.copy(
        usage = state.usage.copy(swipesBySurface = state.usage.swipesBySurface.plusOne(surface)),
        stats = state.stats.copy(swipesBySurface = state.stats.swipesBySurface.plusOne(surface)),
    )

    private fun tick(state: State, at: Instant): State {
        val elapsed = state.lastTickAt?.let { Duration.between(it, at).toMillis() } ?: 0L
        // A stalled handler or a sleeping phone must not add a burst of time; a clock moved back adds none.
        val counted = elapsed.coerceIn(0L, MAX_TICK_MILLIS)
        val surface = state.surface ?: Surface.OTHER
        val visit = state.visitMillis + counted
        val hour = at.atZone(clock.zone).hour
        val hours = state.stats.hours().let { it.toMutableList().apply { this[hour] = it[hour] + counted } }
        return state.copy(
            usage = state.usage.copy(millisBySurface = state.usage.millisBySurface.plus(surface, counted)),
            stats = state.stats.copy(
                millisBySurface = state.stats.millisBySurface.plus(surface, counted),
                longestVisitMillis = maxOf(state.stats.longestVisitMillis, visit),
                millisByHour = hours,
            ),
            lastTickAt = at,
            visitMillis = visit,
        )
    }

    /**
     * Starts a fresh limit window once [at] is past the reset hour, and a fresh calendar date once
     * it's past midnight, each on its own. Both only ever move forward: a clock moved back keeps
     * the current counters. A session that spans either boundary counts as one open after it, and
     * the tick interval that crosses it counts after it. A session in progress is kept; the
     * override count, cooldown and break start over at the reset hour.
     */
    private fun rollOver(state: State, at: Instant): State {
        val opensCarried = if (state.isOpen) 1 else 0
        val day = dayOf(at)
        val usage = if (day.isAfter(state.usage.day)) {
            DayUsage(day, opens = opensCarried, session = state.usage.session)
        } else {
            state.usage
        }
        val date = dateOf(at)
        val newDate = date.isAfter(state.stats.date)
        val stats = if (newDate) DayStats(date, opens = opensCarried) else state.stats
        if (usage === state.usage && stats === state.stats) return state
        return state.copy(usage = usage, stats = stats, visitMillis = if (newDate) 0L else state.visitMillis)
    }

    private fun Map<Surface, Long>.plus(surface: Surface, millis: Long) = this + (surface to (this[surface] ?: 0L) + millis)

    private fun Map<Surface, Int>.plusOne(surface: Surface) = this + (surface to (this[surface] ?: 0) + 1)

    companion object {
        const val MAX_TICK_MILLIS = 5_000L
    }
}
