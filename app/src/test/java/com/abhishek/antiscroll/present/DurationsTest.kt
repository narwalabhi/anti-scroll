package com.abhishek.antiscroll.present

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationsTest {

    @Test
    fun `hours and minutes as the design writes them`() {
        assertEquals("0m", Durations.hm(0))
        assertEquals("45s", Durations.hm(45_999))
        assertEquals("58m", Durations.hm(58 * 60_000L + 59_000))
        assertEquals("2h", Durations.hm(120 * 60_000L))
        assertEquals("1h 47m", Durations.hm(107 * 60_000L))
    }

    @Test
    fun `running text rounds minutes up`() {
        assertEquals("1 min", Durations.minutesText(1_000))
        assertEquals("17 min", Durations.minutesText(17 * 60_000L))
        assertEquals("1h 5m", Durations.minutesText(65 * 60_000L))
    }

    @Test
    fun `timers round seconds up`() {
        assertEquals("0:00", Durations.clock(0))
        assertEquals("0:42", Durations.clock(41_200))
        assertEquals("6:12", Durations.clock(372_000))
        assertEquals("1:02:03", Durations.clock(3_723_000))
    }

    @Test
    fun `spoken durations`() {
        assertEquals("30 seconds", Durations.spoken(30_000))
        assertEquals("1 minute", Durations.spoken(60_000))
        assertEquals("1 hour 47 minutes", Durations.spoken(107 * 60_000L))
        assertEquals("2 hours", Durations.spoken(120 * 60_000L))
    }
}
