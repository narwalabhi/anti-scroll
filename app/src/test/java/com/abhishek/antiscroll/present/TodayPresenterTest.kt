package com.abhishek.antiscroll.present

import com.abhishek.antiscroll.rules.budget.Budgets
import com.abhishek.antiscroll.rules.engine.GuardState
import com.abhishek.antiscroll.rules.health.GapCause
import com.abhishek.antiscroll.rules.health.HealthRecord
import com.abhishek.antiscroll.rules.health.TrackingGap
import com.abhishek.antiscroll.rules.profile.Surface
import com.abhishek.antiscroll.rules.usage.DayStats
import com.abhishek.antiscroll.rules.usage.DayUsage
import com.abhishek.antiscroll.rules.usage.Session
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayPresenterTest {

    private val presenter = TodayPresenter()
    private val today = LocalDate.parse("2026-10-30")
    private val now = Instant.parse("2026-10-30T18:00:00Z")
    private fun min(m: Long) = m * 60_000L
    private val allGranted = HealthChecks(accessibility = true, overlay = true, notifications = true, battery = true, usageAccess = true)
    private val budgets = Budgets(Duration.ofMinutes(90), mapOf(Surface.REELS to Duration.ofMinutes(15)))

    private val stats = DayStats(
        today,
        opens = 10,
        millisBySurface = mapOf(Surface.FEED to min(54), Surface.REELS to min(39), Surface.SEARCH to min(2), Surface.EXPLORE to min(1), Surface.DMS to 0L),
        longestVisitMillis = min(14),
    )
    private val usage = DayUsage(today, opens = 10, millisBySurface = stats.millisBySurface)

    private fun input(
        enforcement: Boolean = false,
        checks: HealthChecks = allGranted,
        guardState: GuardState = GuardState.Idle,
        firstDay: LocalDate = today,
        usage: DayUsage = this.usage,
        health: HealthRecord = HealthRecord(),
    ) = TodayPresenter.Input(
        stats = stats, usage = usage, past = emptyList(), budgets = budgets, enforcementEnabled = enforcement,
        guardState = guardState, overrideCap = 5, health = health, checks = checks, firstDay = firstDay, now = now,
    )

    @Test
    fun `counting during the baseline shows the baseline day and this week with future stubs`() {
        val state = presenter.present(input(firstDay = today.minusDays(2)))
        assertEquals(TodayUiState.Mode.COUNTING, state.mode)
        assertEquals(TodayUiState.Baseline(3, 7), state.baseline)
        assertTrue(state.week.thisWeek)
        assertEquals(today.minusDays(2), state.week.days.first().date)
        assertEquals(listOf(false, false, false, true, true, true, true), state.week.days.map { it.isFuture })
        assertEquals(TodayUiState.ThirdStat.LongestStretch(min(14)), state.third)
    }

    @Test
    fun `the hero compares the limit window and says how far over`() {
        val hero = presenter.present(input()).hero
        assertEquals(min(96), hero.totalMillis)
        assertNull(hero.windowMillis)
        assertEquals(min(6), hero.overMillis)
    }

    @Test
    fun `after midnight the window total is shown apart from the calendar total`() {
        val window = usage.copy(day = today.minusDays(1), millisBySurface = mapOf(Surface.FEED to min(120)))
        val hero = presenter.present(input(usage = window)).hero
        assertEquals(min(120), hero.windowMillis)
        assertEquals(min(120), hero.usedMillis)
    }

    @Test
    fun `the breakdown merges search into explore, hides zeros and sorts by time`() {
        val rows = presenter.present(input()).breakdown
        assertEquals(listOf(Section.FEED, Section.REELS, Section.EXPLORE), rows.map { it.section })
        assertEquals(min(3), rows.last().millis)
        assertEquals(56, rows.first().percent)
    }

    @Test
    fun `guarding shows overrides used and the session running`() {
        val session = Session("Override", now.plusSeconds(372), byOverride = true, startedAt = now.minusSeconds(228))
        val state = presenter.present(input(enforcement = true, guardState = GuardState.Away(session), usage = usage.copy(overridesUsed = 2)))
        assertEquals(TodayUiState.Mode.GUARDING, state.mode)
        assertNull(state.baseline)
        assertEquals(TodayUiState.ThirdStat.Overrides(2, 5), state.third)
        assertEquals(TodayUiState.SessionCard(372_000, 600_000, 2, 5), state.session)
        assertFalse(state.week.thisWeek)
        assertTrue(state.week.days.last().isToday)
    }

    @Test
    fun `not watching raises the alert with the time it stopped`() {
        val off = Instant.parse("2026-10-30T15:12:00Z")
        val health = HealthRecord(running = false, gaps = listOf(TrackingGap(off, null, GapCause.SERVICE_OFF)))
        val state = presenter.present(input(checks = allGranted.copy(accessibility = false), health = health))
        assertEquals(TodayUiState.Mode.NOT_WATCHING, state.mode)
        assertEquals(TodayUiState.Alert(off), state.alert)
        assertTrue(state.hero.unknownTail)
        assertEquals(off, state.hero.pausedAt)
        assertEquals(1, state.gaps.size)
        assertTrue(state.settingsNeedsFix)
    }

    @Test
    fun `average open is the total over opens`() {
        assertEquals(min(96) / 10, presenter.present(input()).averageOpenMillis)
    }
}
