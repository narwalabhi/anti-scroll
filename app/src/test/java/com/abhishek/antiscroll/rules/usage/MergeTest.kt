package com.abhishek.antiscroll.rules.usage

import com.abhishek.antiscroll.rules.profile.Surface
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeTest {

    private val date = LocalDate.parse("2026-10-30")
    private val t = Instant.parse("2026-10-30T12:00:00Z")

    @Test
    fun `stats add time, opens and swipes and keep the longest visit`() {
        val backup = DayStats(date, opens = 3, millisBySurface = mapOf(Surface.FEED to 1_000L), longestVisitMillis = 900L, guarded = true)
        val since = DayStats(
            date, opens = 2, millisBySurface = mapOf(Surface.FEED to 500L, Surface.REELS to 200L),
            swipesBySurface = mapOf(Surface.REELS to 4), longestVisitMillis = 400L,
        )
        val merged = backup + since
        assertEquals(5, merged.opens)
        assertEquals(mapOf(Surface.FEED to 1_500L, Surface.REELS to 200L), merged.millisBySurface)
        assertEquals(mapOf(Surface.REELS to 4), merged.swipesBySurface)
        assertEquals(900L, merged.longestVisitMillis)
        assertTrue(merged.guarded)
    }

    @Test
    fun `usage keeps the larger override count and the later timers`() {
        val backup = DayUsage(date, overridesUsed = 3, cooldownUntil = t.plusSeconds(600), breakUntil = t)
        val since = DayUsage(date, overridesUsed = 1, cooldownUntil = t, breakUntil = t.plusSeconds(60))
        val merged = backup + since
        assertEquals(3, merged.overridesUsed)
        assertEquals(t.plusSeconds(600), merged.cooldownUntil)
        assertEquals(t.plusSeconds(60), merged.breakUntil)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `different dates don't merge`() {
        DayStats(date) + DayStats(date.plusDays(1))
    }
}
