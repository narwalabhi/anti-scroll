package com.abhishek.antiscroll.present

import com.abhishek.antiscroll.rules.budget.Budgets
import com.abhishek.antiscroll.rules.engine.GuardState
import com.abhishek.antiscroll.rules.health.GapCause
import com.abhishek.antiscroll.rules.health.HealthRecord
import com.abhishek.antiscroll.rules.profile.Surface
import com.abhishek.antiscroll.rules.usage.DayStats
import com.abhishek.antiscroll.rules.usage.DayUsage
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** The parts of Instagram the breakdown shows, each with its own colour in the view. */
enum class Section { FEED, REELS, MESSAGES, STORIES, EXPLORE, OTHER;

    companion object {
        fun of(surface: Surface): Section = when (surface) {
            Surface.FEED -> FEED
            Surface.REELS -> REELS
            Surface.DMS -> MESSAGES
            Surface.STORIES -> STORIES
            Surface.EXPLORE, Surface.SEARCH -> EXPLORE
            Surface.PROFILE, Surface.OTHER -> OTHER
        }
    }
}

/** Today's screen, ready to draw (docs/UI.md, Today). The view formats dates and words; numbers are decided here. */
data class TodayUiState(
    val date: LocalDate,
    val mode: Mode,
    val baseline: Baseline?,
    val alert: Alert?,
    val session: SessionCard?,
    val hero: Hero,
    val surfaceLimits: List<LimitRow>,
    val breakdown: List<SectionRow>,
    val opens: Int,
    val averageOpenMillis: Long,
    val third: ThirdStat,
    val week: Week,
    val gaps: List<GapRow>,
    val settingsNeedsFix: Boolean,
) {
    enum class Mode { COUNTING, GUARDING, NOT_WATCHING }

    data class Baseline(val day: Int, val of: Int)

    /** Not watching: since when, if known. */
    data class Alert(val offSince: Instant?)

    /** [overrideNumber] is null for a session the user chose; [totalMillis] null when its start isn't known. */
    data class SessionCard(val leftMillis: Long, val totalMillis: Long?, val overrideNumber: Int?, val cap: Int)

    /**
     * The big number is today's calendar total; the gauge and pill compare the limit window (4 AM to
     * 4 AM), which [windowMillis] holds when it differs from the calendar total (after midnight).
     */
    data class Hero(
        val totalMillis: Long,
        val windowMillis: Long?,
        val usedMillis: Long,
        val limitMillis: Long,
        val pausedAt: Instant?,
        val unknownTail: Boolean,
    ) {
        val overMillis: Long get() = (usedMillis - limitMillis).coerceAtLeast(0L)
        val leftMillis: Long get() = (limitMillis - usedMillis).coerceAtLeast(0L)
    }

    data class LimitRow(val surface: Surface, val usedMillis: Long, val limitMillis: Long) {
        val overMillis: Long get() = (usedMillis - limitMillis).coerceAtLeast(0L)
        val leftMillis: Long get() = (limitMillis - usedMillis).coerceAtLeast(0L)
    }

    data class SectionRow(val section: Section, val millis: Long, val percent: Int)

    sealed interface ThirdStat {
        data class LongestStretch(val millis: Long) : ThirdStat
        data class Overrides(val used: Int, val cap: Int) : ThirdStat
    }

    /** [days] oldest first; a future day (baseline week) is a ghost stub. */
    data class Week(val thisWeek: Boolean, val days: List<Day>, val limitMillis: Long) {
        data class Day(val date: LocalDate, val millis: Long, val isToday: Boolean, val isFuture: Boolean)
    }

    data class GapRow(val start: Instant, val end: Instant?, val cause: GapCause)
}

/**
 * Builds [TodayUiState] from the counters and the guard (docs/LLD.md, UI redesign). Pure: every
 * input is passed in, so it's tested without Android.
 */
class TodayPresenter(private val baselineDays: Int = BASELINE_DAYS) {

    data class Input(
        val stats: DayStats,
        val usage: DayUsage,
        /** The calendar dates before today, oldest first, as many as are kept (6 is enough). */
        val past: List<DayStats>,
        val budgets: Budgets,
        val enforcementEnabled: Boolean,
        val guardState: GuardState,
        val overrideCap: Int,
        val health: HealthRecord,
        val checks: HealthChecks,
        val firstDay: LocalDate,
        val now: Instant,
    )

    fun present(input: Input): TodayUiState {
        val stats = input.stats
        val today = stats.date
        val mode = when {
            !input.checks.accessibility -> TodayUiState.Mode.NOT_WATCHING
            input.enforcementEnabled -> TodayUiState.Mode.GUARDING
            else -> TodayUiState.Mode.COUNTING
        }
        val baselineDay = ChronoUnit.DAYS.between(input.firstDay, today).toInt() + 1
        val inBaseline = !input.enforcementEnabled && baselineDay in 1..baselineDays
        val pausedAt = input.health.ongoingGap?.start
        val limit = input.budgets.dailyTotal.toMillis()
        val windowMillis = input.usage.totalMillis

        return TodayUiState(
            date = today,
            mode = mode,
            baseline = if (inBaseline && mode != TodayUiState.Mode.NOT_WATCHING) TodayUiState.Baseline(baselineDay, baselineDays) else null,
            alert = if (mode == TodayUiState.Mode.NOT_WATCHING) TodayUiState.Alert(pausedAt) else null,
            session = sessionCard(input),
            hero = TodayUiState.Hero(
                totalMillis = stats.totalMillis,
                windowMillis = windowMillis.takeIf { it != stats.totalMillis },
                usedMillis = windowMillis,
                limitMillis = limit,
                pausedAt = if (mode == TodayUiState.Mode.NOT_WATCHING) pausedAt else null,
                unknownTail = mode == TodayUiState.Mode.NOT_WATCHING,
            ),
            surfaceLimits = input.budgets.perSurface.map { (surface, limit) ->
                TodayUiState.LimitRow(surface, input.usage.millisBySurface[surface] ?: 0L, limit.toMillis())
            },
            breakdown = breakdown(stats),
            opens = stats.opens,
            averageOpenMillis = if (stats.opens == 0) 0L else stats.totalMillis / stats.opens,
            third = if (input.enforcementEnabled) {
                TodayUiState.ThirdStat.Overrides(input.usage.overridesUsed, input.overrideCap)
            } else {
                TodayUiState.ThirdStat.LongestStretch(stats.longestVisitMillis)
            },
            week = week(input, inBaseline, limit),
            gaps = input.health.gaps
                .filter { it.isOngoing || !it.end!!.isBefore(input.now.minus(Duration.ofDays(7))) }
                .sortedByDescending { it.start }
                .map { TodayUiState.GapRow(it.start, it.end, it.cause) },
            settingsNeedsFix = HealthPresenter().present(input.checks).toFix > 0,
        )
    }

    /** Shown while a session has time left, guarding only (counting-only has no sessions). */
    private fun sessionCard(input: Input): TodayUiState.SessionCard? {
        if (!input.enforcementEnabled) return null
        val session = when (val state = input.guardState) {
            is GuardState.Running -> state.session
            is GuardState.Away -> state.session
            else -> null
        } ?: return null
        val left = Duration.between(input.now, session.allowedUntil).toMillis()
        if (left <= 0) return null
        return TodayUiState.SessionCard(
            leftMillis = left,
            totalMillis = session.startedAt?.let { Duration.between(it, session.allowedUntil).toMillis() },
            overrideNumber = if (session.byOverride) input.usage.overridesUsed else null,
            cap = input.overrideCap,
        )
    }

    /** Sections by time, largest first, zero rows hidden; percents of today's total. */
    private fun breakdown(stats: DayStats): List<TodayUiState.SectionRow> {
        val bySection = stats.millisBySurface.entries
            .groupBy({ Section.of(it.key) }, { it.value })
            .mapValues { it.value.sum() }
            .filterValues { it > 0L }
        val total = bySection.values.sum().coerceAtLeast(1L)
        return bySection.entries
            .sortedByDescending { it.value }
            .map { TodayUiState.SectionRow(it.key, it.value, ((it.value * 100.0) / total).let(Math::round).toInt()) }
    }

    /**
     * During the baseline: the baseline's own 7 days, the ones still to come as ghost stubs.
     * Otherwise the last 7 days ending today.
     */
    private fun week(input: Input, inBaseline: Boolean, limit: Long): TodayUiState.Week {
        val today = input.stats.date
        val byDate = (input.past + input.stats).associateBy { it.date }
        val first = if (inBaseline) input.firstDay else today.minusDays(6)
        val days = (0L until 7L).map { offset ->
            val date = first.plusDays(offset)
            TodayUiState.Week.Day(
                date = date,
                millis = byDate[date]?.totalMillis ?: 0L,
                isToday = date == today,
                isFuture = date.isAfter(today),
            )
        }
        return TodayUiState.Week(thisWeek = inBaseline, days = days, limitMillis = limit)
    }

    companion object {
        const val BASELINE_DAYS = 7
    }
}
