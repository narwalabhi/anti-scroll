package com.abhishek.antiscroll.rules.unlock

import java.time.Instant

/**
 * Type [sentenceCount] random sentences with at least [minAccuracy] (see [TypingAccuracy]). A
 * failed attempt gets different text straight away.
 */
class TypingChallenge(
    private val sentenceCount: Int,
    private val minAccuracy: Double,
    private val sentences: TypingSentences,
) : UnlockChallenge {

    init {
        require(minAccuracy in 0.0..1.0) { "minAccuracy must be between 0 and 1" }
    }

    override fun begin(now: Instant): ChallengeRun = Run(sentences.pick(sentenceCount))

    private inner class Run(private val text: String) : ChallengeRun {
        override val prompt = ChallengePrompt.Typing(text, minAccuracy)

        override fun submit(answer: ChallengeAnswer, now: Instant): ChallengeStep = when (answer) {
            is ChallengeAnswer.Typed ->
                if (TypingAccuracy.score(answer.text, text) >= minAccuracy) {
                    ChallengeStep(this, ChallengeStatus.Passed)
                } else {
                    ChallengeStep(Run(sentences.pick(sentenceCount, notThis = text)), ChallengeStatus.InProgress)
                }
            ChallengeAnswer.WaitDone -> ChallengeStep(this, ChallengeStatus.InProgress)
        }
    }
}
