package com.abhishek.antiscroll.rules.unlock

/** One part of a ladder step, as settings describe it. */
sealed interface ChallengeSpec {
    data class Wait(val seconds: Int) : ChallengeSpec
    data class Typing(val sentences: Int, val minAccuracy: Double) : ChallengeSpec
}

/** Turns a ladder step's specs into one challenge that runs them in order. */
class ChallengeFactory(private val sentences: TypingSentences) {

    fun build(specs: List<ChallengeSpec>): UnlockChallenge = SequenceChallenge(
        specs.map { spec ->
            when (spec) {
                is ChallengeSpec.Wait -> WaitChallenge(spec.seconds)
                is ChallengeSpec.Typing -> TypingChallenge(spec.sentences, spec.minAccuracy, sentences)
            }
        },
    )
}
