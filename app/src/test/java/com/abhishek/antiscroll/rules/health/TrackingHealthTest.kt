package com.abhishek.antiscroll.rules.health

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingHealthTest {

    private val health = TrackingHealth()
    private val t0: Instant = Instant.parse("2026-10-30T10:00:00Z")

    private fun min(m: Long): Instant = t0.plusSeconds(m * 60)

    @Test
    fun `first start adds no gap`() {
        val record = health.started(HealthRecord(), t0)
        assertTrue(record.running)
        assertEquals(t0, record.lastAliveAt)
        assertTrue(record.gaps.isEmpty())
    }

    @Test
    fun `turning off opens a gap and turning on closes it`() {
        val off = health.stopped(health.started(HealthRecord(), t0), min(10))
        assertFalse(off.running)
        assertEquals(TrackingGap(min(10), null, GapCause.SERVICE_OFF), off.ongoingGap)

        val on = health.started(off, min(40))
        assertNull(on.ongoingGap)
        assertEquals(listOf(TrackingGap(min(10), min(40), GapCause.SERVICE_OFF)), on.gaps)
    }

    @Test
    fun `a start while still marked running is a death without goodbye`() {
        val alive = health.alive(health.started(HealthRecord(), t0), min(5))
        val restarted = health.started(alive, min(35))
        assertEquals(listOf(TrackingGap(min(5), min(35), GapCause.STOPPED_BY_SYSTEM)), restarted.gaps)
    }

    @Test
    fun `gaps under a minute are dropped`() {
        val off = health.stopped(health.started(HealthRecord(), t0), min(10))
        val on = health.started(off, min(10).plusSeconds(30))
        assertTrue(on.gaps.isEmpty())
    }

    @Test
    fun `a clock moved back adds no gap`() {
        val alive = health.alive(health.started(HealthRecord(), t0), min(30))
        assertTrue(health.started(alive, min(10)).gaps.isEmpty())
    }

    @Test
    fun `stopping twice keeps the first gap`() {
        val off = health.stopped(health.started(HealthRecord(), t0), min(10))
        assertEquals(off.gaps, health.stopped(off, min(20)).gaps)
    }

    @Test
    fun `gaps older than a week are pruned but an ongoing one stays`() {
        val old = HealthRecord(
            running = false,
            lastAliveAt = t0,
            gaps = listOf(TrackingGap(t0, min(30), GapCause.SERVICE_OFF), TrackingGap(min(60), null, GapCause.SERVICE_OFF)),
        )
        val later = t0.plusSeconds(8L * 24 * 3600)
        val on = health.started(old, later)
        assertEquals(listOf(TrackingGap(min(60), later, GapCause.SERVICE_OFF)), on.gaps)
    }

    @Test
    fun `the recorder saves each change`() {
        val store = object : HealthStore {
            var saved: HealthRecord? = null
            override fun load() = HealthRecord()
            override fun save(record: HealthRecord) { saved = record }
        }
        val recorder = HealthRecorder(health, store)
        recorder.started(t0)
        recorder.stopped(min(10))
        assertEquals(recorder.record, store.saved)
        assertTrue(recorder.record.ongoingGap != null)
        recorder.clearGaps()
        assertTrue(store.saved!!.gaps.isEmpty())
    }

    @Test
    fun `a restart after an app update is not a gap`() {
        val alive = health.alive(health.started(HealthRecord(), t0), min(5))
        assertTrue(health.started(alive, min(8), appUpdatedAt = min(7)).gaps.isEmpty())
        // An update long before the last sign of life doesn't excuse a later death.
        assertEquals(1, health.started(alive, min(35), appUpdatedAt = t0.minusSeconds(60)).gaps.size)
    }
}
