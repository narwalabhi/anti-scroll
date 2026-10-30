package com.abhishek.antiscroll.data

import com.abhishek.antiscroll.rules.profile.Surface
import com.abhishek.antiscroll.rules.usage.DayUsage
import com.abhishek.antiscroll.rules.usage.Session
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DayUsageJsonTest {

    @Test
    fun `round trip keeps every field`() {
        val usage = DayUsage(
            day = LocalDate.parse("2026-10-29"),
            opens = 6,
            millisBySurface = mapOf(Surface.REELS to 126_000L, Surface.DMS to 41_500L),
            swipesBySurface = mapOf(Surface.REELS to 12),
            overridesUsed = 2,
            cooldownUntil = Instant.parse("2026-10-29T17:30:00Z"),
            breakUntil = Instant.parse("2026-10-29T17:25:00Z"),
            session = Session("Reply to messages", Instant.parse("2026-10-29T17:40:00Z"), byOverride = true),
        )
        assertEquals(usage, DayUsageJson.decode(DayUsageJson.encode(usage)))
    }

    @Test
    fun `round trip of an empty day`() {
        val usage = DayUsage(LocalDate.parse("2026-10-29"))
        assertEquals(usage, DayUsageJson.decode(DayUsageJson.encode(usage)))
    }

    @Test
    fun `surfaces that no longer exist are dropped`() {
        val json = """{"day":"2026-10-29","opens":1,"millisBySurface":{"REELS":1000,"SHORTS":5000}}"""
        assertEquals(mapOf(Surface.REELS to 1_000L), DayUsageJson.decode(json).millisBySurface)
    }

    @Test
    fun `fields added later are optional`() {
        val json = """{"day":"2026-10-29","opens":3}"""
        assertEquals(DayUsage(LocalDate.parse("2026-10-29"), opens = 3), DayUsageJson.decode(json))
    }
}
