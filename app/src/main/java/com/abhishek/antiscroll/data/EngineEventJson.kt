package com.abhishek.antiscroll.data

import com.abhishek.antiscroll.rules.events.EngineEvent
import com.abhishek.antiscroll.rules.settings.AppSettings
import org.json.JSONObject

/**
 * One JSON line per event. Type names are spelled out rather than taken from class names,
 * which release builds shorten.
 */
object EngineEventJson {

    fun encode(event: EngineEvent): String {
        val json = JSONObject()
        json.put("at", event.at.toString())
        val type = when (event) {
            is EngineEvent.Opened -> "opened"
            is EngineEvent.Closed -> "closed"
            is EngineEvent.SurfaceEntered -> {
                json.put("surface", event.surface.name)
                "surface"
            }
            is EngineEvent.DayReset -> {
                json.put("newDay", event.newDay.toString())
                "dayReset"
            }
            is EngineEvent.SessionStarted -> {
                json.put("purpose", event.purpose)
                json.put("minutes", event.minutes)
                "sessionStarted"
            }
            is EngineEvent.SessionTimeUp -> "sessionTimeUp"
            is EngineEvent.DailyLimitReached -> "dailyLimitReached"
            is EngineEvent.SurfaceLimitReached -> {
                json.put("surface", event.surface.name)
                "surfaceLimitReached"
            }
            is EngineEvent.LeftByChoice -> "leftByChoice"
            is EngineEvent.OverrideGranted -> {
                json.put("number", event.number)
                json.put("minutes", event.minutes)
                "overrideGranted"
            }
            is EngineEvent.TypingFailed -> "typingFailed"
            is EngineEvent.SessionEndedEarly -> "sessionEndedEarly"
            is EngineEvent.SettingsChanged -> {
                json.put("from", settings(event.from))
                json.put("to", settings(event.to))
                json.put("source", event.source.name)
                "settingsChanged"
            }
            is EngineEvent.Heartbeat -> "heartbeat"
            is EngineEvent.ServiceStarted -> "serviceStarted"
            is EngineEvent.ServiceStopped -> "serviceStopped"
        }
        json.put("type", type)
        return json.toString()
    }

    private fun settings(settings: AppSettings) = JSONObject().apply {
        put("guarding", settings.enforcementEnabled)
        put("dailyTotalMinutes", settings.dailyTotalMinutes)
        put("surfaceMinutes", JSONObject().apply { settings.surfaceMinutes.forEach { (s, m) -> put(s.name, m) } })
    }
}
