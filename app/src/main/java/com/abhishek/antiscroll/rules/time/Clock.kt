package com.abhishek.antiscroll.rules.time

import java.time.Instant
import java.time.ZoneId

/** Current time and time zone. The app wraps the system clock; tests use a clock they can move. */
interface Clock {
    fun now(): Instant
    val zone: ZoneId
}
