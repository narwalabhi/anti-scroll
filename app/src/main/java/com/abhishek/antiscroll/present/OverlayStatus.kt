package com.abhishek.antiscroll.present

import com.abhishek.antiscroll.rules.budget.Budgets
import com.abhishek.antiscroll.rules.escalation.EscalationPolicy
import com.abhishek.antiscroll.rules.profile.Surface
import com.abhishek.antiscroll.rules.unlock.ChallengeSpec
import com.abhishek.antiscroll.rules.usage.DayUsage
import java.time.Instant

/**
 * The numbers the screens over Instagram show around the engine's decision: opens and time today,
 * the limits, overrides used and left, when the day resets. Effects carry only the decision.
 */
data class OverlayStatus(
    val opens: Int,
    val usedMillis: Long,
    val limitMillis: Long,
    val surfaceUsedMillis: Map<Surface, Long>,
    val surfaceLimitMillis: Map<Surface, Long>,
    val overridesUsed: Int,
    val cap: Int,
    val nextResetAt: Instant,
    /** The wait of the override after the next one, for "Next override waits 2 min"; null when there is none. */
    val waitAfterNextSeconds: Int?,
) {
    val overridesLeft: Int get() = (cap - overridesUsed).coerceAtLeast(0)
    val overMillis: Long get() = (usedMillis - limitMillis).coerceAtLeast(0L)
    val leftMillis: Long get() = (limitMillis - usedMillis).coerceAtLeast(0L)

    companion object {
        fun from(usage: DayUsage, budgets: Budgets, policy: EscalationPolicy, cap: Int, nextResetAt: Instant) = OverlayStatus(
            opens = usage.opens,
            usedMillis = usage.totalMillis,
            limitMillis = budgets.dailyTotal.toMillis(),
            surfaceUsedMillis = usage.millisBySurface,
            surfaceLimitMillis = budgets.perSurface.mapValues { it.value.toMillis() },
            overridesUsed = usage.overridesUsed,
            cap = cap,
            nextResetAt = nextResetAt,
            waitAfterNextSeconds = policy.stepFor(usage.overridesUsed + 1)?.challenges
                ?.filterIsInstance<ChallengeSpec.Wait>()?.sumOf { it.seconds },
        )
    }
}
