package com.abhishek.antiscroll.data

import com.abhishek.antiscroll.rules.profile.Surface
import com.abhishek.antiscroll.rules.usage.DayStats
import com.abhishek.antiscroll.rules.usage.DayUsage
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DayStatsJsonTest {

    @Test
    fun `round trip keeps every field`() {
        val stats = DayStats(
            LocalDate.parse("2026-10-30"),
            opens = 4,
            millisBySurface = mapOf(Surface.REELS to 90_000L, Surface.DMS to 12_500L),
            swipesBySurface = mapOf(Surface.REELS to 17),
        )
        assertEquals(stats, DayStatsJson.decode(DayStatsJson.encode(stats)))
    }

    @Test
    fun `an empty date round trips`() {
        val stats = DayStats(LocalDate.parse("2026-10-30"))
        assertEquals(stats, DayStatsJson.decode(DayStatsJson.encode(stats)))
    }

    @Test
    fun `a day from before calendar stats is shown from its limit-window counters`() {
        val usage = DayUsage(LocalDate.parse("2026-10-29"), opens = 62, millisBySurface = mapOf(Surface.FEED to 1_000L))
        assertEquals(DayStats(LocalDate.parse("2026-10-29"), 62, mapOf(Surface.FEED to 1_000L)), DayStats.from(usage))
    }
}
