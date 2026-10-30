package com.abhishek.antiscroll.rules.unlock

import java.time.Instant

/**
 * Wait [seconds]. The screen counts down and reports [ChallengeAnswer.WaitDone], but the run checks
 * the time itself: a report that comes too early changes nothing.
 */
class WaitChallenge(private val seconds: Int) : UnlockChallenge {

    init {
        require(seconds >= 0) { "seconds must not be negative" }
    }

    override fun begin(now: Instant): ChallengeRun = Run(seconds, endsAt = now.plusSeconds(seconds.toLong()))

    private data class Run(val seconds: Int, val endsAt: Instant) : ChallengeRun {
        override val prompt = ChallengePrompt.Wait(seconds)

        override fun submit(answer: ChallengeAnswer, now: Instant): ChallengeStep {
            val done = answer == ChallengeAnswer.WaitDone && !now.isBefore(endsAt)
            return ChallengeStep(this, if (done) ChallengeStatus.Passed else ChallengeStatus.InProgress)
        }
    }
}
