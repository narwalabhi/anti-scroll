package com.abhishek.antiscroll.present

import com.abhishek.antiscroll.rules.profile.Surface
import com.abhishek.antiscroll.rules.usage.DayStats
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryPresenterTest {

    private val presenter = HistoryPresenter(resetHour = 4)
    private val today = LocalDate.parse("2026-11-12")
    private fun min(m: Long) = m * 60_000L

    private fun day(back: Long, feed: Long, reels: Long = 0, hours: Map<Int, Long> = emptyMap()) = DayStats(
        today.minusDays(back),
        millisBySurface = mapOf(Surface.FEED to min(feed), Surface.REELS to min(reels)),
        millisByHour = List(24) { min(hours[it] ?: 0) },
    )

    @Test
    fun `average, days under and lowest over the counted days`() {
        val days = listOf(day(2, 100), day(1, 80), day(0, 30))
        val state = presenter.present(days, baseline = days, firstDay = today.minusDays(20), limitMillis = min(90))
        assertEquals(min(70), state.averageMillis)
        assertEquals(2, state.daysUnder)
        assertEquals(HistoryUiState.Lowest(today, min(30), true), state.lowest)
    }

    @Test
    fun `days before the first counted day are not averaged as zeros`() {
        val days = listOf(day(2, 0), day(1, 60), day(0, 60))
        val state = presenter.present(days, baseline = emptyList(), firstDay = today.minusDays(1), limitMillis = min(90))
        assertEquals(min(60), state.averageMillis)
        assertEquals(2, state.daysCounted)
    }

    @Test
    fun `hours start at the day start and late means 9 PM to then`() {
        val days = listOf(day(0, 10, hours = mapOf(23 to 6L, 10 to 4L)))
        val state = presenter.present(days, baseline = emptyList(), firstDay = today, limitMillis = min(90))
        assertEquals(4, state.hours.first().hour)
        assertEquals(3, state.hours.last().hour)
        assertEquals(listOf(21, 22, 23, 0, 1, 2, 3), state.hours.filter { it.late }.map { it.hour })
        assertEquals(60, state.lateSharePercent)
        assertEquals(23, state.peakHour)
    }

    @Test
    fun `reels share compares the baseline with the last 7 days`() {
        val baseline = listOf(day(20, 60, 40))
        val days = listOf(day(1, 80, 20), day(0, 80, 20))
        val state = presenter.present(days, baseline, firstDay = today.minusDays(20), limitMillis = min(90))
        assertEquals(40, state.reelsShareBaseline)
        assertEquals(20, state.reelsShareRecent)
    }

    @Test
    fun `no recent reels share while still in the baseline`() {
        val days = listOf(day(0, 80, 20))
        assertNull(presenter.present(days, days, firstDay = today, limitMillis = min(90)).reelsShareRecent)
    }
}
