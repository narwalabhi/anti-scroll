package com.abhishek.antiscroll.rules.unlock

import java.time.Instant

/** A way to earn an override: a wait, a typing test, or several in a row. */
interface UnlockChallenge {
    fun begin(now: Instant): ChallengeRun
}

/**
 * One attempt in progress. Immutable: [submit] returns the next run, so the engine that holds
 * it stays a pure function.
 */
interface ChallengeRun {
    /** What the overlay should show. */
    val prompt: ChallengePrompt

    fun submit(answer: ChallengeAnswer, now: Instant): ChallengeStep
}

data class ChallengeStep(val run: ChallengeRun, val status: ChallengeStatus)

sealed interface ChallengePrompt {
    data class Wait(val seconds: Int) : ChallengePrompt
    data class Typing(val text: String, val minAccuracy: Double) : ChallengePrompt
}

/** What the screen reports. The run decides whether it's enough; the screen never does. */
sealed interface ChallengeAnswer {
    data object WaitDone : ChallengeAnswer
    data class Typed(val text: String) : ChallengeAnswer
}

sealed interface ChallengeStatus {
    /** A later part follows, or a failed typing attempt got new text. */
    data object InProgress : ChallengeStatus
    data object Passed : ChallengeStatus
}
