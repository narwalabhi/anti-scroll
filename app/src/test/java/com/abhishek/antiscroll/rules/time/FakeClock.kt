package com.abhishek.antiscroll.rules.time

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class FakeClock(
    private var current: Instant,
    override val zone: ZoneId = ZoneId.of("America/New_York"),
) : Clock {

    override fun now(): Instant = current

    fun advance(by: Duration) {
        current = current.plus(by)
    }

    fun set(to: Instant) {
        current = to
    }

    companion object {
        fun at(local: String, zone: ZoneId = ZoneId.of("America/New_York")) =
            FakeClock(LocalDateTime.parse(local).atZone(zone).toInstant(), zone)
    }
}
