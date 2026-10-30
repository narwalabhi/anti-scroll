package com.abhishek.antiscroll.present

import com.abhishek.antiscroll.rules.escalation.CountLadderPolicy
import com.abhishek.antiscroll.rules.escalation.LadderSettings
import com.abhishek.antiscroll.rules.budget.Budgets
import com.abhishek.antiscroll.rules.profile.Surface
import com.abhishek.antiscroll.rules.settings.AppSettings
import com.abhishek.antiscroll.rules.usage.DayStats
import com.abhishek.antiscroll.rules.usage.DayUsage
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LimitsAndHealthPresenterTest {

    private val today = DayStats(LocalDate.parse("2026-10-30"), millisBySurface = mapOf(Surface.FEED to 6_000_000L, Surface.REELS to 2_400_000L))
    private val ladder = LadderSettings()

    @Test
    fun `during the baseline each limit shows today's number`() {
        val state = LimitsPresenter().present(AppSettings(enforcementEnabled = false), ladder, today, listOf(today), baselineDay = 2)
        assertEquals(2, state.baselineDay)
        assertEquals(LimitTarget.Total, state.rows[0].target)
        assertEquals(LimitsUiState.Reference.BaselineDay(2, 8_400_000L), state.rows[0].reference)
        assertEquals(LimitsUiState.Reference.BaselineDay(2, 2_400_000L), state.rows[1].reference)
    }

    @Test
    fun `after the baseline the reference is its average`() {
        val state = LimitsPresenter().present(AppSettings(enforcementEnabled = true), ladder, today, listOf(today, DayStats(today.date.minusDays(1))), 9)
        assertNull(state.baselineDay)
        assertEquals(LimitsUiState.Reference.BaselineAverage(4_200_000L), state.rows[0].reference)
    }

    @Test
    fun `the ladder table reads the settings`() {
        val state = LimitsPresenter().present(AppSettings(), ladder, today, emptyList(), 1)
        assertEquals(listOf(30, 60, 120, 300, 300), state.rungs.map { it.waitSeconds })
        assertEquals(listOf(10, 5, 3, 2, 2), state.rungs.map { it.grantMinutes })
        assertEquals(95, state.typingAccuracyPercent)
        assertEquals(5, state.cap)
    }

    @Test
    fun `editing clamps and sets the right limit`() {
        val settings = AppSettings()
        assertEquals(360, LimitEditing.apply(settings, LimitTarget.Total, 999).dailyTotalMinutes)
        assertEquals(mapOf(Surface.REELS to 20), LimitEditing.apply(settings, LimitTarget.OneSurface(Surface.REELS), 20).surfaceMinutes)
    }

    @Test
    fun `only accessibility and battery need fixing`() {
        val state = HealthPresenter().present(HealthChecks(accessibility = false, overlay = false, notifications = false, battery = false, usageAccess = false))
        assertEquals(2, state.toFix)
        assertEquals(HealthUiState.Status.SET_UP, state.rows.first { it.item == HealthUiState.Item.USAGE_ACCESS }.status)
    }

    @Test
    fun `overlay status counts overrides left and the wait after the next`() {
        val usage = DayUsage(today.date, opens = 41, millisBySurface = mapOf(Surface.FEED to 3_480_000L), overridesUsed = 1)
        val status = OverlayStatus.from(usage, Budgets(Duration.ofMinutes(90)), CountLadderPolicy(ladder), 5, Instant.EPOCH)
        assertEquals(4, status.overridesLeft)
        assertEquals(120, status.waitAfterNextSeconds)
        assertEquals(1_920_000L, status.leftMillis)
    }
}
