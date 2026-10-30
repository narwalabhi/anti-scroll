package com.abhishek.antiscroll.rules.engine

import com.abhishek.antiscroll.rules.escalation.LadderStep
import com.abhishek.antiscroll.rules.profile.Surface
import com.abhishek.antiscroll.rules.unlock.ChallengeRun
import com.abhishek.antiscroll.rules.usage.Session
import java.time.Instant

/** What the engine knows. Instagram is usable only while [Running]; every other state shows a screen or nothing. */
sealed interface GuardState {
    data object Idle : GuardState
    data object Pause : GuardState
    data object Intention : GuardState
    data class Running(val session: Session) : GuardState
    data class Away(val session: Session) : GuardState // left mid-session; back before allowedUntil resumes it
    data class Blocked(val reason: BlockReason, val session: Session?) : GuardState

    /** Earning the [number]th override of the day by passing [step]'s challenges. */
    data class Challenging(
        val reason: BlockReason,
        val session: Session?,
        val run: ChallengeRun,
        val number: Int,
        val step: LadderStep,
    ) : GuardState
}

/** Why Instagram is covered, and how that block behaves, so the engine never asks which kind it is. */
sealed interface BlockReason {
    /** True when the rest of Instagram still works: Leave and closing keep the session. */
    val keepsSession: Boolean get() = false

    /** True when being on [surface] means this block no longer applies. */
    fun liftsOn(surface: Surface): Boolean = false

    data object SessionTimeUp : BlockReason
    data object DailyTotal : BlockReason

    /** One surface's limit is used; the rest of Instagram still works. */
    data class SurfaceBudget(val surface: Surface) : BlockReason {
        override val keepsSession: Boolean get() = true
        override fun liftsOn(surface: Surface): Boolean = surface != this.surface
    }
}

/** What the block screen can offer. */
sealed interface OverrideAvailability {
    /** The [number]th override today, costing [step]. */
    data class Available(val number: Int, val step: LadderStep) : OverrideAvailability
    data class CoolingDown(val until: Instant) : OverrideAvailability
    data object CapReached : OverrideAvailability
}
