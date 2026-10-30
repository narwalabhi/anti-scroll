package com.abhishek.antiscroll.rules.usage

import java.time.Instant

/**
 * A stretch of Instagram the user chose (or earned with an override), ending at [allowedUntil] in
 * real time. Saved with the day's counters, so it survives the app restarting.
 */
data class Session(
    val purpose: String,
    val allowedUntil: Instant,
    val byOverride: Boolean = false,
    /** When it began, for showing progress; null for sessions saved before this existed. */
    val startedAt: Instant? = null,
)
