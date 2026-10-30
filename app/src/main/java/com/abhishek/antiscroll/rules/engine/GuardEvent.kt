package com.abhishek.antiscroll.rules.engine

import com.abhishek.antiscroll.rules.profile.Surface
import com.abhishek.antiscroll.rules.unlock.ChallengeAnswer
import java.time.Instant

/** What the service and the overlay screens report. Events the engine times carry the time. */
sealed interface GuardEvent {
    data class InstagramOpened(val at: Instant) : GuardEvent
    data object InstagramClosed : GuardEvent
    data class SurfaceChanged(val surface: Surface) : GuardEvent
    data class Tick(val at: Instant) : GuardEvent // once a second while Instagram is in front
    data class Swiped(val surface: Surface) : GuardEvent // a Reel swiped to
    data object PauseFinished : GuardEvent
    data class IntentionChosen(val purpose: String, val minutes: Int, val at: Instant) : GuardEvent
    data object LeaveChosen : GuardEvent
    data class OverrideRequested(val at: Instant) : GuardEvent

    /** What the user did on a challenge screen; the run judges it, never the screen. */
    data class ChallengeSubmitted(val answer: ChallengeAnswer, val at: Instant) : GuardEvent
    data class ChallengeCancelled(val at: Instant) : GuardEvent

    /** "End now" on the status screen: the session in progress ends at [at], as if its time were up. */
    data class EndSessionRequested(val at: Instant) : GuardEvent
}
