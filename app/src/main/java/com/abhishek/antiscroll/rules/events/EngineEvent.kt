package com.abhishek.antiscroll.rules.events

import com.abhishek.antiscroll.rules.profile.Surface
import com.abhishek.antiscroll.rules.settings.AppSettings
import com.abhishek.antiscroll.rules.settings.ChangeSource
import java.time.Instant
import java.time.LocalDate

/**
 * What happened, for logs and later summaries. Counts, timestamps and our own labels (such as
 * the purpose picked), never Instagram's screen text.
 */
sealed interface EngineEvent {
    val at: Instant

    data class Opened(override val at: Instant) : EngineEvent
    data class Closed(override val at: Instant) : EngineEvent
    data class SurfaceEntered(override val at: Instant, val surface: Surface) : EngineEvent
    data class DayReset(override val at: Instant, val newDay: LocalDate) : EngineEvent
    data class SessionStarted(override val at: Instant, val purpose: String, val minutes: Int) : EngineEvent
    data class SessionTimeUp(override val at: Instant) : EngineEvent
    data class DailyLimitReached(override val at: Instant) : EngineEvent
    data class SurfaceLimitReached(override val at: Instant, val surface: Surface) : EngineEvent
    data class LeftByChoice(override val at: Instant) : EngineEvent // "Not now" or "Leave": sent home or to messages by a screen
    data class OverrideGranted(override val at: Instant, val number: Int, val minutes: Int) : EngineEvent
    data class TypingFailed(override val at: Instant) : EngineEvent
    data class SessionEndedEarly(override val at: Instant) : EngineEvent // "End now" on the status screen

    /** The limits or the guarding switch changed: recorded every time, with what it was before. */
    data class SettingsChanged(
        override val at: Instant,
        val from: AppSettings,
        val to: AppSettings,
        val source: ChangeSource,
    ) : EngineEvent
    data class Heartbeat(override val at: Instant) : EngineEvent // every 5 min while the service lives
    data class ServiceStarted(override val at: Instant) : EngineEvent
    data class ServiceStopped(override val at: Instant) : EngineEvent
}
