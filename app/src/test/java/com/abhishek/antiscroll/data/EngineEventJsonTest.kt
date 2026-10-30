package com.abhishek.antiscroll.data

import com.abhishek.antiscroll.rules.events.EngineEvent
import com.abhishek.antiscroll.rules.profile.Surface
import java.time.Instant
import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class EngineEventJsonTest {

    private val at = Instant.parse("2026-10-29T17:00:00Z")

    private fun encode(event: EngineEvent) = JSONObject(EngineEventJson.encode(event))

    @Test
    fun `every event has a type and a time`() {
        val types = listOf(
            EngineEvent.Opened(at),
            EngineEvent.Closed(at),
            EngineEvent.SurfaceEntered(at, Surface.REELS),
            EngineEvent.DayReset(at, LocalDate.parse("2026-10-30")),
            EngineEvent.SessionStarted(at, "Just browsing", 10),
            EngineEvent.SessionTimeUp(at),
            EngineEvent.DailyLimitReached(at),
            EngineEvent.SurfaceLimitReached(at, Surface.REELS),
            EngineEvent.LeftByChoice(at),
            EngineEvent.OverrideGranted(at, 2, 5),
            EngineEvent.TypingFailed(at),
            EngineEvent.Heartbeat(at),
            EngineEvent.ServiceStarted(at),
            EngineEvent.ServiceStopped(at),
        ).map { event ->
            val json = encode(event)
            assertEquals("2026-10-29T17:00:00Z", json.getString("at"))
            json.getString("type")
        }
        assertEquals(
            listOf(
                "opened", "closed", "surface", "dayReset", "sessionStarted", "sessionTimeUp", "dailyLimitReached",
                "surfaceLimitReached", "leftByChoice",
                "overrideGranted", "typingFailed", "heartbeat", "serviceStarted", "serviceStopped",
            ),
            types,
        )
    }

    @Test
    fun `surface and new day are included`() {
        assertEquals("REELS", encode(EngineEvent.SurfaceEntered(at, Surface.REELS)).getString("surface"))
        assertEquals("2026-10-30", encode(EngineEvent.DayReset(at, LocalDate.parse("2026-10-30"))).getString("newDay"))
    }

    @Test
    fun `session start includes purpose and minutes`() {
        val json = encode(EngineEvent.SessionStarted(at, "Reply to messages", 5))
        assertEquals("Reply to messages", json.getString("purpose"))
        assertEquals(5, json.getInt("minutes"))
    }

    @Test
    fun `a plain event has only its type and time`() {
        assertEquals(setOf("at", "type"), encode(EngineEvent.Opened(at)).keys().asSequence().toSet())
    }
}
