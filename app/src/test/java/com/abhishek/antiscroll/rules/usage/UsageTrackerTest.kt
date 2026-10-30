package com.abhishek.antiscroll.rules.usage

import com.abhishek.antiscroll.rules.engine.GuardEvent.InstagramClosed
import com.abhishek.antiscroll.rules.engine.GuardEvent.InstagramOpened
import com.abhishek.antiscroll.rules.engine.GuardEvent.SurfaceChanged
import com.abhishek.antiscroll.rules.engine.GuardEvent.Tick
import com.abhishek.antiscroll.rules.engine.GuardEvent
import com.abhishek.antiscroll.rules.profile.Surface
import com.abhishek.antiscroll.rules.time.DayClock
import com.abhishek.antiscroll.rules.time.FakeClock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageTrackerTest {

    private val clock = FakeClock.at("2026-10-29T12:00:00")
    private val tracker = UsageTracker(DayClock(resetHour = 4), clock)

    private fun at(local: String): Instant = LocalDateTime.parse(local).atZone(clock.zone).toInstant()

    private fun run(vararg events: GuardEvent, start: String = "2026-10-29T12:00:00"): UsageTracker.State =
        events.fold(tracker.start(at(start))) { state, event -> tracker.next(state, event) }

    /** One tick a second from [from] for [seconds] seconds, as the service sends them. */
    private fun ticks(from: String, seconds: Int): Array<GuardEvent> {
        val start = at(from)
        return Array(seconds) { Tick(start.plusSeconds(it + 1L)) }
    }

    @Test
    fun `opening counts one open`() {
        val state = run(InstagramOpened(at("2026-10-29T12:00:00")))
        assertEquals(1, state.usage.opens)
        assertTrue(state.isOpen)
    }

    @Test
    fun `an open event while already open is not counted again`() {
        val state = run(InstagramOpened(at("2026-10-29T12:00:00")), InstagramOpened(at("2026-10-29T12:00:05")))
        assertEquals(1, state.usage.opens)
    }

    @Test
    fun `opening again after closing counts again`() {
        val state = run(
            InstagramOpened(at("2026-10-29T12:00:00")),
            InstagramClosed,
            InstagramOpened(at("2026-10-29T12:05:00")),
        )
        assertEquals(2, state.usage.opens)
    }

    @Test
    fun `ticks add time to the current surface`() {
        val state = run(
            InstagramOpened(at("2026-10-29T12:00:00")),
            SurfaceChanged(Surface.REELS),
            *ticks("2026-10-29T12:00:00", 10),
            SurfaceChanged(Surface.DMS),
            *ticks("2026-10-29T12:00:10", 4),
        )
        assertEquals(10_000L, state.usage.millisBySurface[Surface.REELS])
        assertEquals(4_000L, state.usage.millisBySurface[Surface.DMS])
        assertEquals(14_000L, state.usage.totalMillis)
    }

    @Test
    fun `uneven ticks lose no time`() {
        val opened = at("2026-10-29T12:00:00")
        val state = run(
            InstagramOpened(opened),
            SurfaceChanged(Surface.FEED),
            Tick(opened.plusMillis(980)),
            Tick(opened.plusMillis(2_010)),
            Tick(opened.plusMillis(3_000)),
        )
        assertEquals(3_000L, state.usage.millisBySurface[Surface.FEED])
    }

    @Test
    fun `time before the surface is known counts as Other`() {
        val state = run(InstagramOpened(at("2026-10-29T12:00:00")), *ticks("2026-10-29T12:00:00", 2))
        assertEquals(2_000L, state.usage.millisBySurface[Surface.OTHER])
    }

    @Test
    fun `swipes are counted per surface while open`() {
        val state = run(
            InstagramOpened(at("2026-10-29T12:00:00")),
            GuardEvent.Swiped(Surface.REELS),
            GuardEvent.Swiped(Surface.REELS),
            InstagramClosed,
            GuardEvent.Swiped(Surface.REELS),
        )
        assertEquals(mapOf(Surface.REELS to 2), state.usage.swipesBySurface)
    }

    @Test
    fun `nothing is counted while closed`() {
        val state = run(
            InstagramOpened(at("2026-10-29T12:00:00")),
            SurfaceChanged(Surface.REELS),
            *ticks("2026-10-29T12:00:00", 3),
            InstagramClosed,
            *ticks("2026-10-29T12:00:03", 30),
        )
        assertEquals(3_000L, state.usage.totalMillis)
        assertFalse(state.isOpen)
    }

    @Test
    fun `reopening does not count the time it was closed`() {
        val state = run(
            InstagramOpened(at("2026-10-29T12:00:00")),
            SurfaceChanged(Surface.FEED),
            *ticks("2026-10-29T12:00:00", 2),
            InstagramClosed,
            InstagramOpened(at("2026-10-29T12:30:00")),
            *ticks("2026-10-29T12:30:00", 2),
        )
        assertEquals(4_000L, state.usage.totalMillis)
    }

    @Test
    fun `closing forgets the surface`() {
        val state = run(
            InstagramOpened(at("2026-10-29T12:00:00")),
            SurfaceChanged(Surface.DMS),
            InstagramClosed,
            InstagramOpened(at("2026-10-29T12:05:00")),
            *ticks("2026-10-29T12:05:00", 1),
        )
        assertEquals(null, state.surface)
        assertEquals(1_000L, state.usage.millisBySurface[Surface.OTHER])
    }

    @Test
    fun `a long gap between ticks is capped`() {
        val state = run(
            InstagramOpened(at("2026-10-29T12:00:00")),
            SurfaceChanged(Surface.REELS),
            Tick(at("2026-10-29T12:10:00")),
        )
        assertEquals(UsageTracker.MAX_TICK_MILLIS, state.usage.totalMillis)
    }

    @Test
    fun `the 4 AM reset starts a new day mid-session`() {
        val state = run(
            InstagramOpened(at("2026-10-30T03:59:50")),
            SurfaceChanged(Surface.REELS),
            *ticks("2026-10-30T03:59:50", 20),
            start = "2026-10-30T03:59:00",
        )
        assertEquals(LocalDate.parse("2026-10-30"), state.usage.day)
        // The session carries over as one open. Ticks at 4:00:00 to 4:00:10 are in the new day; the
        // 4:00:00 tick brings the second that crossed the reset with it.
        assertEquals(1, state.usage.opens)
        assertEquals(11_000L, state.usage.millisBySurface[Surface.REELS])
    }

    @Test
    fun `the 4 AM reset keeps a session in progress but clears the ladder counters`() {
        val session = Session("Just browsing", allowedUntil = at("2026-10-30T04:05:00"))
        val saved = DayUsage(
            LocalDate.parse("2026-10-29"),
            overridesUsed = 3,
            cooldownUntil = at("2026-10-30T04:30:00"),
            breakUntil = at("2026-10-30T04:10:00"),
            session = session,
        )
        val state = listOf<GuardEvent>(InstagramOpened(at("2026-10-30T03:59:00")), *ticks("2026-10-30T03:59:00", 70))
            .fold(tracker.start(at("2026-10-30T03:59:00"), saved)) { s, e -> tracker.next(s, e) }
        assertEquals(LocalDate.parse("2026-10-30"), state.usage.day)
        assertEquals(session, state.usage.session)
        assertEquals(0, state.usage.overridesUsed)
        assertEquals(null, state.usage.cooldownUntil)
        assertEquals(null, state.usage.breakUntil)
    }

    @Test
    fun `calendar stats roll over at midnight, the limit window at 4 AM`() {
        val state = run(
            InstagramOpened(at("2026-10-29T23:59:00")),
            SurfaceChanged(Surface.REELS),
            *ticks("2026-10-29T23:59:00", 120),
            start = "2026-10-29T23:59:00",
        )
        assertEquals(LocalDate.parse("2026-10-30"), state.stats.date)
        // As at 4 AM, the tick at 00:00:00 brings the second that crossed midnight with it.
        assertEquals(61_000L, state.stats.millisBySurface[Surface.REELS])
        assertEquals(1, state.stats.opens)
        assertEquals(LocalDate.parse("2026-10-29"), state.usage.day)
        assertEquals(120_000L, state.usage.millisBySurface[Surface.REELS])
        assertEquals(1, state.usage.opens)
    }

    @Test
    fun `opens and swipes count in both`() {
        val state = run(InstagramOpened(at("2026-10-29T12:00:00")), GuardEvent.Swiped(Surface.REELS))
        assertEquals(1, state.stats.opens)
        assertEquals(1, state.usage.opens)
        assertEquals(mapOf(Surface.REELS to 1), state.stats.swipesBySurface)
        assertEquals(mapOf(Surface.REELS to 1), state.usage.swipesBySurface)
    }

    @Test
    fun `start keeps saved stats from today's date only`() {
        val saved = DayStats(LocalDate.parse("2026-10-29"), opens = 3)
        assertEquals(saved, tracker.start(at("2026-10-29T18:00:00"), savedStats = saved).stats)
        assertEquals(DayStats(LocalDate.parse("2026-10-30")), tracker.start(at("2026-10-30T01:00:00"), savedStats = saved).stats)
    }

    @Test
    fun `a tick while closed starts the new day on time`() {
        val state = run(
            InstagramOpened(at("2026-10-30T01:00:00")),
            *ticks("2026-10-30T01:00:00", 5),
            InstagramClosed,
            Tick(at("2026-10-30T09:00:00")),
            start = "2026-10-30T01:00:00",
        )
        assertEquals(LocalDate.parse("2026-10-30"), state.usage.day)
        assertEquals(0, state.usage.opens)
        assertEquals(0L, state.usage.totalMillis)
    }

    @Test
    fun `a tick while closed before the reset keeps the day and counts nothing`() {
        val state = run(
            InstagramOpened(at("2026-10-30T01:00:00")),
            *ticks("2026-10-30T01:00:00", 5),
            InstagramClosed,
            Tick(at("2026-10-30T03:00:00")),
            start = "2026-10-30T01:00:00",
        )
        assertEquals(LocalDate.parse("2026-10-29"), state.usage.day)
        assertEquals(5_000L, state.usage.totalMillis)
    }

    @Test
    fun `opening after the reset starts a new day`() {
        val state = run(
            InstagramOpened(at("2026-10-30T01:00:00")),
            *ticks("2026-10-30T01:00:00", 5),
            InstagramClosed,
            InstagramOpened(at("2026-10-30T09:00:00")),
            start = "2026-10-30T01:00:00",
        )
        assertEquals(LocalDate.parse("2026-10-30"), state.usage.day)
        assertEquals(1, state.usage.opens)
        assertEquals(0L, state.usage.totalMillis)
    }

    @Test
    fun `a clock moved back past 4 AM does not go back a day`() {
        val state = run(
            InstagramOpened(at("2026-10-30T04:30:00")),
            SurfaceChanged(Surface.FEED),
            *ticks("2026-10-30T04:30:00", 3),
            // The clock is moved back an hour, into the previous day.
            *ticks("2026-10-30T03:30:00", 3),
            start = "2026-10-30T04:30:00",
        )
        assertEquals(LocalDate.parse("2026-10-30"), state.usage.day)
        assertEquals(1, state.usage.opens)
        // The backward jump itself adds nothing; the ticks after it count normally.
        assertEquals(5_000L, state.usage.totalMillis)
    }

    @Test
    fun `start keeps saved counters from the same day`() {
        val saved = DayUsage(LocalDate.parse("2026-10-29"), opens = 4, millisBySurface = mapOf(Surface.FEED to 60_000L))
        val state = tracker.start(at("2026-10-29T18:00:00"), saved)
        assertEquals(saved, state.usage)
    }

    @Test
    fun `start drops saved counters from an earlier day`() {
        val saved = DayUsage(LocalDate.parse("2026-10-28"), opens = 4)
        val state = tracker.start(at("2026-10-29T18:00:00"), saved)
        assertEquals(DayUsage(LocalDate.parse("2026-10-29")), state.usage)
    }

    @Test
    fun `the longest visit is kept across visits`() {
        val state = run(
            InstagramOpened(at("2026-10-29T12:00:00")),
            *ticks("2026-10-29T12:00:00", 90),
            InstagramClosed,
            InstagramOpened(at("2026-10-29T13:00:00")),
            *ticks("2026-10-29T13:00:00", 30),
        )
        assertEquals(90_000L, state.stats.longestVisitMillis)
    }

    @Test
    fun `time is bucketed by local hour`() {
        val state = run(
            InstagramOpened(at("2026-10-29T21:59:30")),
            *ticks("2026-10-29T21:59:30", 60),
            start = "2026-10-29T21:59:00",
        )
        val hours = state.stats.hours()
        // The tick that crosses into 22:00 counts there, as at every boundary.
        assertEquals(29_000L, hours[21])
        assertEquals(31_000L, hours[22])
        assertEquals(60_000L, hours.sum())
    }

    @Test
    fun `a visit across midnight starts a new longest stretch on the new date`() {
        val state = run(
            InstagramOpened(at("2026-10-29T23:59:00")),
            *ticks("2026-10-29T23:59:00", 120),
            start = "2026-10-29T23:58:00",
        )
        assertEquals(LocalDate.parse("2026-10-30"), state.stats.date)
        assertEquals(61_000L, state.stats.longestVisitMillis)
    }
}
