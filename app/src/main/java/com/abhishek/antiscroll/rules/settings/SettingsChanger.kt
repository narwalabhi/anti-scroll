package com.abhishek.antiscroll.rules.settings

import com.abhishek.antiscroll.rules.escalation.LadderSettings
import com.abhishek.antiscroll.rules.unlock.ChallengeFactory
import com.abhishek.antiscroll.rules.unlock.ChallengeRun
import com.abhishek.antiscroll.rules.unlock.ChallengeSpec
import com.abhishek.antiscroll.rules.unlock.TypingSentences
import com.abhishek.antiscroll.rules.usage.DayUsage
import java.time.Instant

/**
 * Decides what a requested settings change costs (docs/SPEC.md, Self-protection): tightening
 * applies at once; loosening needs a wait and a typing test, and isn't allowed at all once today's
 * overrides are used up. Pure: the settings screen drives the challenge and applies the change.
 */
class SettingsChanger(
    private val challenges: ChallengeFactory,
    private val ladder: LadderSettings,
    private val waitSeconds: Int = LOOSEN_WAIT_SECONDS,
) {

    sealed interface Answer {
        data class Apply(val settings: AppSettings) : Answer
        data class Refused(val reason: RefusedBecause) : Answer
        data class Challenge(val wanted: AppSettings, val run: ChallengeRun) : Answer
    }

    enum class RefusedBecause { OVERRIDES_USED_UP }

    fun request(current: AppSettings, wanted: AppSettings, usage: DayUsage, now: Instant): Answer = when {
        !loosens(current, wanted) -> Answer.Apply(wanted)
        usage.overridesUsed >= ladder.dailyCap -> Answer.Refused(RefusedBecause.OVERRIDES_USED_UP)
        else -> Answer.Challenge(wanted, challenge(now))
    }

    /** The loosening cost on its own, for other changes that would weaken the friction (erasing today's counters). */
    fun challenge(now: Instant): ChallengeRun = challenges.build(cost()).begin(now)

    /**
     * True when [wanted] relaxes anything: enforcement off, the daily total raised, or any surface
     * limit raised or removed. Adding a surface limit tightens.
     */
    fun loosens(current: AppSettings, wanted: AppSettings): Boolean =
        (current.enforcementEnabled && !wanted.enforcementEnabled) ||
            wanted.dailyTotalMinutes > current.dailyTotalMinutes ||
            current.surfaceMinutes.any { (surface, minutes) ->
                val now = wanted.surfaceMinutes[surface]
                now == null || now > minutes
            }

    private fun cost() = listOf(
        ChallengeSpec.Wait(waitSeconds),
        ChallengeSpec.Typing(TypingSentences.PARAGRAPH_SENTENCES, LOOSEN_ACCURACY),
    )

    companion object {
        const val LOOSEN_WAIT_SECONDS = 600
        const val LOOSEN_ACCURACY = 0.95

        /** Debug "short timers": a 5-second wait; the typing stays. */
        const val SHORT_WAIT_SECONDS = 5
    }
}
