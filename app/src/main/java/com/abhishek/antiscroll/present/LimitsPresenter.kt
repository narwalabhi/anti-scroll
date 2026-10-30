package com.abhishek.antiscroll.present

import com.abhishek.antiscroll.rules.escalation.LadderSettings
import com.abhishek.antiscroll.rules.profile.Surface
import com.abhishek.antiscroll.rules.settings.AppSettings
import com.abhishek.antiscroll.rules.unlock.ChallengeSpec
import com.abhishek.antiscroll.rules.usage.DayStats
import kotlin.math.roundToInt

/** What a limit applies to: all of Instagram, or one surface. */
sealed interface LimitTarget {
    data object Total : LimitTarget
    data class OneSurface(val surface: Surface) : LimitTarget
}

/** The Limits tab (docs/UI.md, Limits). */
data class LimitsUiState(
    val guarding: Boolean,
    val baselineDay: Int?,
    val rows: List<Row>,
    val rungs: List<Rung>,
    val cap: Int,
    val cooldownBaseMinutes: Int,
    val cooldownStepMinutes: Int,
    val typingAccuracyPercent: Int,
) {
    data class Row(val target: LimitTarget, val limitMinutes: Int, val reference: Reference?)

    /** The user's real number to set a limit against: today during the baseline, then the baseline's average. */
    sealed interface Reference {
        val millis: Long
        data class BaselineDay(val day: Int, override val millis: Long) : Reference
        data class BaselineAverage(override val millis: Long) : Reference
    }

    data class Rung(val number: Int, val waitSeconds: Int, val grantMinutes: Int)
}

/** The edit sheet's bounds and shortcuts. */
object LimitEditing {
    const val STEP_MINUTES = 5
    const val MIN_MINUTES = 5
    const val MAX_MINUTES = 360

    fun presets(target: LimitTarget): List<Int> = when (target) {
        LimitTarget.Total -> listOf(45, 60, 90, 120)
        is LimitTarget.OneSurface -> listOf(10, 15, 20, 30)
    }

    fun clamp(minutes: Int): Int = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)

    /** [settings] with [target]'s limit set to [minutes]. */
    fun apply(settings: AppSettings, target: LimitTarget, minutes: Int): AppSettings = when (target) {
        LimitTarget.Total -> settings.copy(dailyTotalMinutes = clamp(minutes))
        is LimitTarget.OneSurface -> settings.copy(surfaceMinutes = settings.surfaceMinutes + (target.surface to clamp(minutes)))
    }

    fun current(settings: AppSettings, target: LimitTarget): Int? = when (target) {
        LimitTarget.Total -> settings.dailyTotalMinutes
        is LimitTarget.OneSurface -> settings.surfaceMinutes[target.surface]
    }
}

class LimitsPresenter(private val baselineDays: Int = TodayPresenter.BASELINE_DAYS) {

    /** [baselineDay]: today's day number from the first counted day; [baseline]: the baseline week's stats so far. */
    fun present(
        settings: AppSettings,
        ladder: LadderSettings,
        today: DayStats,
        baseline: List<DayStats>,
        baselineDay: Int,
    ): LimitsUiState {
        val inBaseline = !settings.enforcementEnabled && baselineDay in 1..baselineDays
        fun reference(millisOf: (DayStats) -> Long): LimitsUiState.Reference? = when {
            baselineDay in 1..baselineDays -> LimitsUiState.Reference.BaselineDay(baselineDay, millisOf(today))
            baseline.isNotEmpty() -> LimitsUiState.Reference.BaselineAverage(baseline.sumOf(millisOf) / baseline.size)
            else -> null
        }
        val rows = listOf(LimitsUiState.Row(LimitTarget.Total, settings.dailyTotalMinutes, reference { it.totalMillis })) +
            settings.surfaceMinutes.entries.sortedBy { it.key.ordinal }.map { (surface, minutes) ->
                LimitsUiState.Row(LimitTarget.OneSurface(surface), minutes, reference { it.millisBySurface[surface] ?: 0L })
            }
        val accuracy = ladder.steps.flatMap { it.challenges }.filterIsInstance<ChallengeSpec.Typing>()
            .minOfOrNull { it.minAccuracy } ?: 0.0
        return LimitsUiState(
            guarding = settings.enforcementEnabled,
            baselineDay = if (inBaseline) baselineDay else null,
            rows = rows,
            rungs = ladder.steps.mapIndexed { i, step ->
                LimitsUiState.Rung(
                    number = i + 1,
                    waitSeconds = step.challenges.filterIsInstance<ChallengeSpec.Wait>().sumOf { it.seconds },
                    grantMinutes = step.grantMinutes,
                )
            },
            cap = ladder.dailyCap,
            cooldownBaseMinutes = ladder.cooldownBaseMinutes,
            cooldownStepMinutes = ladder.cooldownStepMinutes,
            typingAccuracyPercent = (accuracy * 100).roundToInt(),
        )
    }
}
