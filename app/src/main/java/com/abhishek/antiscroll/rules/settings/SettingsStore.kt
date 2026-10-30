package com.abhishek.antiscroll.rules.settings

import com.abhishek.antiscroll.rules.budget.Budgets
import com.abhishek.antiscroll.rules.profile.Surface
import java.time.Duration

/**
 * Settings the user can change, saved across restarts: the enforcement switch, the daily total,
 * and a limit for any surface (Reels by default; a surface without one has no limit of its own).
 * Loosening any of them costs a wait and a typing test ([SettingsChanger]); tightening is instant.
 */
data class AppSettings(
    val enforcementEnabled: Boolean = true, // on from the first open (decided 2026-10-30)
    val dailyTotalMinutes: Int = 90,
    val surfaceMinutes: Map<Surface, Int> = mapOf(Surface.REELS to 15),
) {
    init {
        require(dailyTotalMinutes in DAILY_TOTAL_RANGE) { "dailyTotalMinutes must be in $DAILY_TOTAL_RANGE" }
        surfaceMinutes.forEach { (surface, minutes) ->
            require(minutes in SURFACE_RANGE) { "$surface minutes must be in $SURFACE_RANGE" }
        }
    }

    fun budgets() = Budgets(
        dailyTotal = Duration.ofMinutes(dailyTotalMinutes.toLong()),
        perSurface = surfaceMinutes.mapValues { Duration.ofMinutes(it.value.toLong()) },
    )

    companion object {
        val DAILY_TOTAL_RANGE = 5..600
        val SURFACE_RANGE = 0..600 // 0: none of that surface at all
    }
}

/** Where a settings change came from, recorded with it (docs/SPEC.md, Cloud copy). */
enum class ChangeSource { USER, RESTORE, DEBUG }

/** Reads and writes [AppSettings]. Kept apart from the usage counters on purpose (docs/LLD.md, Ports). */
interface SettingsStore {
    fun load(): AppSettings
    fun save(settings: AppSettings)
}
