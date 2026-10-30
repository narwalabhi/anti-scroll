package com.abhishek.antiscroll.data

import com.abhishek.antiscroll.rules.health.TrackingGap
import com.abhishek.antiscroll.rules.settings.AppSettings
import com.abhishek.antiscroll.rules.settings.ChangeSource
import com.abhishek.antiscroll.rules.usage.DayStats
import com.abhishek.antiscroll.rules.usage.DayUsage
import java.time.Instant
import java.time.LocalDate

/**
 * The fields sent to the developer's copy (docs/SPEC.md, Cloud copy): the same as the export file.
 * Counts and timestamps only. Plain values (numbers, text, lists, maps) any cloud store accepts.
 */
object CloudDocuments {

    fun day(stats: DayStats, usage: DayUsage?): Map<String, Any?> = mapOf(
        "date" to stats.date.toString(),
        "guarded" to stats.guarded,
        "opens" to stats.opens,
        "totalSeconds" to stats.totalMillis / 1000,
        "secondsBySurface" to stats.millisBySurface.entries.associate { it.key.name to it.value / 1000 },
        "swipesBySurface" to stats.swipesBySurface.entries.associate { it.key.name to it.value },
        "longestVisitSeconds" to stats.longestVisitMillis / 1000,
        "overridesUsed" to (usage?.overridesUsed ?: 0),
    )

    /** One entry in the history of settings changes. */
    fun settingsChange(from: AppSettings, to: AppSettings, source: ChangeSource, at: Instant): Map<String, Any?> = mapOf(
        "at" to at.toString(),
        "source" to source.name,
        "from" to settings(from),
        "to" to settings(to),
    )

    private fun settings(settings: AppSettings): Map<String, Any?> = mapOf(
        "guarding" to settings.enforcementEnabled,
        "dailyTotalMinutes" to settings.dailyTotalMinutes,
        "surfaceMinutes" to settings.surfaceMinutes.entries.associate { it.key.name to it.value },
    )

    fun install(
        settings: AppSettings,
        gaps: List<TrackingGap>,
        device: HistoryExport.Device,
        firstDay: LocalDate,
        updatedAt: Instant,
    ): Map<String, Any?> = mapOf(
        "updatedAt" to updatedAt.toString(),
        "firstDay" to firstDay.toString(),
        "appVersion" to device.appVersion,
        "manufacturer" to device.manufacturer,
        "model" to device.model,
        "androidSdk" to device.androidSdk,
        "instagramVersion" to device.instagramVersion,
        "guarding" to settings.enforcementEnabled,
        "dailyTotalMinutes" to settings.dailyTotalMinutes,
        "surfaceMinutes" to settings.surfaceMinutes.entries.associate { it.key.name to it.value },
        "gaps" to gaps.map { mapOf("start" to it.start.toString(), "end" to it.end?.toString(), "cause" to it.cause.name) },
    )
}
