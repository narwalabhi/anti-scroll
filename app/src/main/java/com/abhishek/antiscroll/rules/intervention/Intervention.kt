package com.abhishek.antiscroll.rules.intervention

import com.abhishek.antiscroll.rules.engine.BlockReason
import com.abhishek.antiscroll.rules.engine.Effect
import com.abhishek.antiscroll.rules.usage.Session

/**
 * What Leave does on a block (docs/LLD.md, Extension points). A different behaviour is a new class
 * wired in AppGraph; the engine only carries out the plan.
 */
interface Intervention {
    fun onLeave(reason: BlockReason, session: Session?): LeavePlan
}

sealed interface LeavePlan {
    /** The visit ends: the engine goes idle, the cover goes, then [effects] run. */
    data class EndVisit(val effects: List<Effect>) : LeavePlan

    /**
     * The user stays in Instagram with their session: the cover stays ("Going back…") while
     * [effects] move them off what's blocked, and lifts once the block no longer applies.
     * Only for a block that keeps the session.
     */
    data class StayInInstagram(val effects: List<Effect>) : LeavePlan
}

/** Leave ends the visit: the phone's home screen. */
class GoHomeIntervention : Intervention {
    override fun onLeave(reason: BlockReason, session: Session?): LeavePlan = LeavePlan.EndVisit(listOf(Effect.GoHome))
}

/**
 * For a block that leaves the rest of Instagram usable (a surface block), Leave is one screen back,
 * with Android's own Back: no app-specific navigation to break. Anything else goes to [fallback].
 */
class StepBackIntervention(private val fallback: Intervention) : Intervention {
    override fun onLeave(reason: BlockReason, session: Session?): LeavePlan =
        if (reason.keepsSession && session != null) {
            LeavePlan.StayInInstagram(listOf(Effect.GoBack))
        } else {
            fallback.onLeave(reason, session)
        }
}
