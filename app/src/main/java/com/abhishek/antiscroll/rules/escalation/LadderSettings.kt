package com.abhishek.antiscroll.rules.escalation

import com.abhishek.antiscroll.rules.unlock.ChallengeSpec
import com.abhishek.antiscroll.rules.unlock.TypingSentences

/** One rung: what an override costs ([challenges], in order) and the minutes it grants. */
data class LadderStep(val challenges: List<ChallengeSpec>, val grantMinutes: Int) {
    init {
        require(challenges.isNotEmpty()) { "a step needs at least one challenge" }
        require(grantMinutes > 0) { "grantMinutes must be positive" }
    }
}

/**
 * The override ladder (docs/SPEC.md). The daily cap is the number of [steps]. After an override
 * session ends, overrides lock for [cooldownBaseMinutes] plus [cooldownStepMinutes] for each earlier
 * override that day. After any session ends, reopening within [breakMinutes] goes to the ladder
 * instead of a new session. Constants in AppGraph until settings are editable (milestone 6).
 */
data class LadderSettings(
    val steps: List<LadderStep> = DEFAULT_STEPS,
    val cooldownBaseMinutes: Int = 20,
    val cooldownStepMinutes: Int = 10,
    val breakMinutes: Int = 15,
) {
    init {
        require(steps.isNotEmpty()) { "the ladder needs at least one step" }
        require(cooldownBaseMinutes >= 0 && cooldownStepMinutes >= 0 && breakMinutes >= 0) {
            "minutes must not be negative"
        }
    }

    val dailyCap: Int get() = steps.size

    companion object {
        private const val NORMAL = 0.95
        private const val NEAR_PERFECT = 0.99
        private const val PARAGRAPH = TypingSentences.PARAGRAPH_SENTENCES

        val DEFAULT_STEPS = listOf(
            LadderStep(listOf(ChallengeSpec.Wait(30), ChallengeSpec.Typing(1, NORMAL)), grantMinutes = 10),
            LadderStep(listOf(ChallengeSpec.Wait(60), ChallengeSpec.Typing(2, NORMAL)), grantMinutes = 5),
            LadderStep(listOf(ChallengeSpec.Wait(120), ChallengeSpec.Typing(PARAGRAPH, NORMAL)), grantMinutes = 3),
            LadderStep(listOf(ChallengeSpec.Wait(300), ChallengeSpec.Typing(PARAGRAPH, NEAR_PERFECT)), grantMinutes = 2),
            LadderStep(listOf(ChallengeSpec.Wait(300), ChallengeSpec.Typing(PARAGRAPH, NEAR_PERFECT)), grantMinutes = 2),
        )

        /**
         * Debug "short timers": the same steps and typing, with 5-second waits, 1-minute grants,
         * a 1-minute cooldown and a 1-minute break, so the whole ladder can be tried in minutes.
         */
        fun shortForTesting() = LadderSettings(
            steps = DEFAULT_STEPS.map { step ->
                LadderStep(
                    challenges = step.challenges.map { if (it is ChallengeSpec.Wait) ChallengeSpec.Wait(5) else it },
                    grantMinutes = 1,
                )
            },
            cooldownBaseMinutes = 1,
            cooldownStepMinutes = 0,
            breakMinutes = 1,
        )
    }
}
