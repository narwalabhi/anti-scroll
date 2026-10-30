package com.abhishek.antiscroll.rules.unlock

import java.time.Instant

/**
 * Several challenges, one after another, behaving as one: a ladder step's wait then typing.
 * A part that's passed stays passed, so a typing retry keeps the finished wait.
 */
class SequenceChallenge(private val parts: List<UnlockChallenge>) : UnlockChallenge {

    init {
        require(parts.isNotEmpty()) { "a sequence needs at least one challenge" }
    }

    override fun begin(now: Instant): ChallengeRun = Run(index = 0, current = parts[0].begin(now))

    private inner class Run(private val index: Int, private val current: ChallengeRun) : ChallengeRun {
        override val prompt: ChallengePrompt get() = current.prompt

        override fun submit(answer: ChallengeAnswer, now: Instant): ChallengeStep {
            val step = current.submit(answer, now)
            return when {
                step.status == ChallengeStatus.InProgress -> ChallengeStep(Run(index, step.run), ChallengeStatus.InProgress)
                index == parts.lastIndex -> ChallengeStep(Run(index, step.run), ChallengeStatus.Passed)
                // The next part starts now: its wait counts from when the previous part was passed.
                else -> ChallengeStep(Run(index + 1, parts[index + 1].begin(now)), ChallengeStatus.InProgress)
            }
        }
    }
}
