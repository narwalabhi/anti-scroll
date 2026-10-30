package com.abhishek.antiscroll.rules.engine

import com.abhishek.antiscroll.rules.unlock.ChallengePrompt

/**
 * What the engine asks for, carried out by an [EffectHandler]. Each screen effect carries what the
 * screen needs, so the overlay only draws.
 */
sealed interface Effect {
    data class ShowPause(val seconds: Int) : Effect
    /**
     * [minuteChoices] are the standard lengths that fit what's left today; [restOfToday] is offered
     * too when what's left ([minutesLeftToday]) is below the largest standard length.
     */
    data class ShowIntention(
        val purposes: List<String>,
        val minuteChoices: List<Int>,
        val restOfToday: Int?,
        val minutesLeftToday: Int,
    ) : Effect
    data class ShowBlock(val reason: BlockReason, val override: OverrideAvailability) : Effect
    data class ShowChallenge(val prompt: ChallengePrompt) : Effect
    data object Dismiss : Effect
    data object GoHome : Effect
    /** Android's Back, once: the system action, so no app's layout can break it. */
    data object GoBack : Effect

    /** The block screen says the user is being taken off what's blocked; the cover stays. */
    data object ShowLeaving : Effect
}
