package com.abhishek.antiscroll.data

import com.abhishek.antiscroll.rules.health.TrackingGap
import com.abhishek.antiscroll.rules.settings.AppSettings
import com.abhishek.antiscroll.rules.usage.DayStats
import com.abhishek.antiscroll.rules.usage.DayUsage
import java.time.Instant
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

/**
 * The file a user shares (docs/SPEC.md, Sharing with friends): every calendar day since install,
 * the limits, tracking gaps and the device. Counts and timestamps only, never screen text.
 */
object HistoryExport {

    const val FORMAT_VERSION = 1

    data class Device(
        val appVersion: String,
        val manufacturer: String,
        val model: String,
        val androidSdk: Int,
        val instagramVersion: String?,
    )

    /** [days] oldest first; [usageByDay] the limit window's counters, for each day's overrides. */
    fun build(
        days: List<DayStats>,
        usageByDay: Map<LocalDate, DayUsage>,
        settings: AppSettings,
        gaps: List<TrackingGap>,
        device: Device,
        exportedAt: Instant,
    ): String = JSONObject().apply {
        put("format", FORMAT_VERSION)
        put("exportedAt", exportedAt.toString())
        put(
            "device",
            JSONObject().apply {
                put("appVersion", device.appVersion)
                put("manufacturer", device.manufacturer)
                put("model", device.model)
                put("androidSdk", device.androidSdk)
                put("instagramVersion", device.instagramVersion ?: JSONObject.NULL)
            },
        )
        put(
            "settings",
            JSONObject().apply {
                put("guarding", settings.enforcementEnabled)
                put("dailyTotalMinutes", settings.dailyTotalMinutes)
                put("surfaceMinutes", JSONObject().apply { settings.surfaceMinutes.forEach { (s, m) -> put(s.name, m) } })
            },
        )
        put(
            "days",
            JSONArray().apply {
                days.forEach { day ->
                    put(
                        JSONObject().apply {
                            put("date", day.date.toString())
                            put("guarded", day.guarded)
                            put("opens", day.opens)
                            put("totalSeconds", day.totalMillis / 1000)
                            put("secondsBySurface", JSONObject().apply { day.millisBySurface.forEach { (s, ms) -> put(s.name, ms / 1000) } })
                            put("swipesBySurface", JSONObject().apply { day.swipesBySurface.forEach { (s, n) -> put(s.name, n) } })
                            put("longestVisitSeconds", day.longestVisitMillis / 1000)
                            put("overridesUsed", usageByDay[day.date]?.overridesUsed ?: 0)
                        },
                    )
                }
            },
        )
        put(
            "gaps",
            JSONArray().apply {
                gaps.forEach { gap ->
                    put(
                        JSONObject().apply {
                            put("start", gap.start.toString())
                            put("end", gap.end?.toString() ?: JSONObject.NULL)
                            put("cause", gap.cause.name)
                        },
                    )
                }
            },
        )
    }.toString(2)
}
