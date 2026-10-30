package com.abhishek.antiscroll.rules.time

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DayClockTest {

    private val newYork = ZoneId.of("America/New_York")
    private val dayClock = DayClock(resetHour = 4)

    private fun local(text: String, zone: ZoneId = newYork): Instant =
        LocalDateTime.parse(text).atZone(zone).toInstant()

    @Test
    fun `3_59 AM belongs to the previous day`() {
        assertEquals(LocalDate.parse("2026-10-28"), dayClock.dayOf(local("2026-10-29T03:59:59"), newYork))
    }

    @Test
    fun `4 AM starts a new day`() {
        assertEquals(LocalDate.parse("2026-10-29"), dayClock.dayOf(local("2026-10-29T04:00:00"), newYork))
    }

    @Test
    fun `midnight is still the previous day`() {
        assertEquals(LocalDate.parse("2026-10-28"), dayClock.dayOf(local("2026-10-29T00:00:00"), newYork))
    }

    @Test
    fun `late evening is the same day`() {
        assertEquals(LocalDate.parse("2026-10-29"), dayClock.dayOf(local("2026-10-29T23:59:00"), newYork))
    }

    @Test
    fun `spring forward does not move the boundary`() {
        // 2026-03-08: clocks jump from 2:00 to 3:00 in New York.
        assertEquals(LocalDate.parse("2026-03-07"), dayClock.dayOf(local("2026-03-08T03:59:00"), newYork))
        assertEquals(LocalDate.parse("2026-03-08"), dayClock.dayOf(local("2026-03-08T04:00:00"), newYork))
    }

    @Test
    fun `fall back does not move the boundary`() {
        // 2026-11-01: 1:00 to 2:00 happens twice in New York.
        assertEquals(LocalDate.parse("2026-10-31"), dayClock.dayOf(local("2026-11-01T03:59:00"), newYork))
        assertEquals(LocalDate.parse("2026-11-01"), dayClock.dayOf(local("2026-11-01T04:00:00"), newYork))
    }

    @Test
    fun `the boundary follows the local zone`() {
        val kolkata = ZoneId.of("Asia/Kolkata")
        val at = local("2026-10-29T04:30:00", kolkata)
        assertEquals(LocalDate.parse("2026-10-29"), dayClock.dayOf(at, kolkata))
        // The same moment is 7 PM the evening before in New York.
        assertEquals(LocalDate.parse("2026-10-28"), dayClock.dayOf(at, newYork))
    }

    @Test
    fun `reset hour comes from settings`() {
        val midnightReset = DayClock(resetHour = 0)
        assertEquals(LocalDate.parse("2026-10-29"), midnightReset.dayOf(local("2026-10-29T00:00:00"), newYork))
    }

    @Test
    fun `reset hour outside 0 to 23 is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { DayClock(resetHour = 24) }
    }

    @Test
    fun `fake clock moves forward`() {
        val clock = FakeClock.at("2026-10-29T03:59:00")
        assertEquals(LocalDate.parse("2026-10-28"), dayClock.dayOf(clock.now(), clock.zone))
        clock.advance(java.time.Duration.ofMinutes(1))
        assertEquals(LocalDate.parse("2026-10-29"), dayClock.dayOf(clock.now(), clock.zone))
    }

    @Test
    fun `the next reset is 4 AM tomorrow, or today before 4 AM`() {
        assertEquals(local("2026-10-30T04:00:00"), dayClock.nextReset(local("2026-10-29T15:00:00"), newYork))
        assertEquals(local("2026-10-29T04:00:00"), dayClock.nextReset(local("2026-10-29T01:00:00"), newYork))
    }
}
