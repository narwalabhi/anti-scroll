package com.abhishek.antiscroll.rules.escalation

/** What the next override costs and grants, and how long overrides lock after one. */
interface EscalationPolicy {
    /** The step for the next override, given how many were used today; null once the daily cap is reached. */
    fun stepFor(overridesToday: Int): LadderStep?

    /** Minutes overrides stay locked after an override session ends; [overridesToday] includes that override. */
    fun cooldownMinutes(overridesToday: Int): Int
}

/** The ladder from [LadderSettings], by count: the nth override today uses the nth step. */
class CountLadderPolicy(private val settings: LadderSettings) : EscalationPolicy {

    override fun stepFor(overridesToday: Int): LadderStep? = settings.steps.getOrNull(overridesToday.coerceAtLeast(0))

    override fun cooldownMinutes(overridesToday: Int): Int =
        if (overridesToday <= 0) 0 else settings.cooldownBaseMinutes + settings.cooldownStepMinutes * (overridesToday - 1)
}
