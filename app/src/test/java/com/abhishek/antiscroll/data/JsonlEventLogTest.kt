package com.abhishek.antiscroll.data

import com.abhishek.antiscroll.rules.events.EngineEvent
import com.abhishek.antiscroll.rules.profile.Surface
import java.io.File
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class JsonlEventLogTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `appends one line per event, in order`() {
        val file = File(folder.root, "events.jsonl")
        val log = JsonlEventLog(file, writer = { it.run() })
        val at = Instant.parse("2026-10-29T17:00:00Z")

        log.onEvent(EngineEvent.Opened(at))
        log.onEvent(EngineEvent.SurfaceEntered(at, Surface.DMS))
        log.onEvent(EngineEvent.Closed(at))

        val lines = file.readLines()
        assertEquals(3, lines.size)
        assertEquals(EngineEventJson.encode(EngineEvent.Opened(at)), lines[0])
        assertEquals(EngineEventJson.encode(EngineEvent.Closed(at)), lines[2])
    }
}
