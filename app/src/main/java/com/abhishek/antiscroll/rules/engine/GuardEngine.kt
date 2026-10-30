package com.abhishek.antiscroll.rules.engine

import com.abhishek.antiscroll.rules.budget.BudgetRule
import com.abhishek.antiscroll.rules.budget.BudgetVerdict
import com.abhishek.antiscroll.rules.budget.Budgets
import com.abhishek.antiscroll.rules.budget.check
import com.abhishek.antiscroll.rules.escalation.EscalationPolicy
import com.abhishek.antiscroll.rules.escalation.LadderSettings
import com.abhishek.antiscroll.rules.intervention.Intervention
import com.abhishek.antiscroll.rules.intervention.LeavePlan
import com.abhishek.antiscroll.rules.profile.Surface
import com.abhishek.antiscroll.rules.settings.LaunchSettings
import com.abhishek.antiscroll.rules.unlock.ChallengeFactory
import com.abhishek.antiscroll.rules.unlock.ChallengeStatus
import com.abhishek.antiscroll.rules.usage.DayUsage
import com.abhishek.antiscroll.rules.usage.Session
import java.time.Duration
import java.time.Instant

/**
 * The state machine from docs/LLD.md: a pure function of a state, the day's counters and an event.
 * It returns the next state, the effects to carry out and the counters (changed when a session
 * ends or an override is granted), and does nothing else. Events carry the time; it reads no clock.
 */
class GuardEngine(
    private val launch: LaunchSettings,
    private val ladder: LadderSettings,
    private val policy: EscalationPolicy,
    private val challenges: ChallengeFactory,
    private val budgets: Budgets,
    private val budgetRules: List<BudgetRule>,
    private val intervention: Intervention,
) {

    data class Transition(val state: GuardState, val effects: List<Effect>, val usage: DayUsage)

    /**
     * [surface] is what's on screen (null until known), for per-surface budgets. [now] is the event's
     * time, from the runner; timed events also carry it.
     */
    fun next(state: GuardState, usage: DayUsage, surface: Surface?, event: GuardEvent, now: Instant): Transition {
        fun stay() = Transition(state, emptyList(), usage)
        fun to(next: GuardState, vararg effects: Effect) = Transition(next, effects.toList(), usage)

        return when (state) {
            GuardState.Idle -> when (event) {
                is GuardEvent.InstagramOpened -> open(usage, event.at)
                else -> stay()
            }
            GuardState.Pause -> when (event) {
                GuardEvent.PauseFinished -> to(GuardState.Intention, intentionPrompt(usage))
                // "Close Instagram" on the pause.
                GuardEvent.LeaveChosen -> to(GuardState.Idle, Effect.Dismiss, Effect.GoHome)
                GuardEvent.InstagramClosed -> to(GuardState.Idle, Effect.Dismiss)
                else -> stay()
            }
            GuardState.Intention -> when (event) {
                is GuardEvent.IntentionChosen -> {
                    val session = sessionFor(event)
                    Transition(GuardState.Running(session), listOf(Effect.Dismiss), usage.copy(session = session))
                }
                GuardEvent.LeaveChosen -> to(GuardState.Idle, Effect.Dismiss, Effect.GoHome)
                GuardEvent.InstagramClosed -> to(GuardState.Idle, Effect.Dismiss)
                else -> stay()
            }
            is GuardState.Running -> when (event) {
                is GuardEvent.Tick -> tickRunning(state, usage, surface, event.at) ?: stay()
                // Checked at once, not on the next tick, so a limited surface is covered as soon as it's seen.
                is GuardEvent.SurfaceChanged -> budgetCheck(state.session, usage, event.surface, now) ?: stay()
                GuardEvent.InstagramClosed -> to(GuardState.Away(state.session))
                is GuardEvent.EndSessionRequested -> endNow(state.session, usage, event.at)
                else -> stay()
            }
            is GuardState.Away -> when (event) {
                // Real time: being away never adds minutes. A session that ran out meanwhile ended
                // when its time was up, so the break counts from then.
                is GuardEvent.InstagramOpened ->
                    if (isOver(state.session, event.at)) {
                        open(afterSession(state.session, usage), event.at)
                    } else {
                        // A spent daily total ends a normal session even before its time is up; an
                        // override session's minutes are the override, so it resumes regardless.
                        val spent = budgetBlock(state.session, usage, null, event.at)
                        if (spent != null) {
                            block(spent, state.session, usage.copy(session = null), event.at)
                        } else {
                            to(GuardState.Running(state.session))
                        }
                    }
                // While away (or after the app restarted), a session that runs out ends then and there,
                // so its break and cooldown show on the status screen without reopening Instagram.
                is GuardEvent.Tick ->
                    if (isOver(state.session, event.at)) {
                        Transition(GuardState.Idle, emptyList(), afterSession(state.session, usage))
                    } else {
                        stay()
                    }
                is GuardEvent.EndSessionRequested -> endNow(state.session, usage, event.at)
                else -> stay()
            }
            is GuardState.Blocked -> when (event) {
                is GuardEvent.OverrideRequested -> startOverride(state, usage, event.at) ?: stay()
                GuardEvent.LeaveChosen -> leave(state, usage)
                // A block lifts when the user reaches somewhere it doesn't apply: after Leave's step back,
                // or Back pressed underneath the screen. Until then the cover stays.
                is GuardEvent.SurfaceChanged -> {
                    val session = state.session
                    if (session != null && state.reason.liftsOn(event.surface)) {
                        to(GuardState.Running(session), Effect.Dismiss)
                    } else {
                        stay()
                    }
                }
                GuardEvent.InstagramClosed -> closeBlocked(state.reason, state.session, usage)
                else -> stay()
            }
            is GuardState.Challenging -> when (event) {
                is GuardEvent.ChallengeSubmitted -> submit(state, usage, event)
                is GuardEvent.ChallengeCancelled -> block(state.reason, state.session, usage, event.at)
                GuardEvent.InstagramClosed -> closeBlocked(state.reason, state.session, usage)
                else -> stay()
            }
        }
    }

    /**
     * Leave from a block, as the [Intervention] plans it. Staying in Instagram keeps the block (and
     * the cover) until the user is somewhere it doesn't apply, so nothing in between shows and no
     * tick can re-block on the way; it needs a session to carry on with.
     */
    private fun leave(state: GuardState.Blocked, usage: DayUsage): Transition {
        val session = state.session
        return when (val plan = intervention.onLeave(state.reason, session)) {
            is LeavePlan.StayInInstagram ->
                if (session != null) {
                    Transition(state, listOf(Effect.ShowLeaving) + plan.effects, usage)
                } else {
                    Transition(GuardState.Idle, listOf(Effect.Dismiss, Effect.GoHome), usage)
                }
            is LeavePlan.EndVisit -> Transition(GuardState.Idle, listOf(Effect.Dismiss) + plan.effects, usage)
        }
    }

    /** Leaving Instagram during a block: a block that keeps the session leaves it Away; the others drop it. */
    private fun closeBlocked(reason: BlockReason, session: Session?, usage: DayUsage): Transition {
        val next = if (reason.keepsSession && session != null) GuardState.Away(session) else GuardState.Idle
        return Transition(next, listOf(Effect.Dismiss), usage)
    }

    /**
     * The user ends the session early: it ends now, so the break (and after an override the
     * cooldown) count from now. Instagram is closed when this comes from the status screen; if it's
     * in front, the next open check covers it.
     */
    private fun endNow(session: Session, usage: DayUsage, at: Instant): Transition {
        val ended = session.copy(allowedUntil = minOf(at, session.allowedUntil))
        return Transition(GuardState.Idle, listOf(Effect.Dismiss), afterSession(ended, usage))
    }

    /** Where to pick up after the app starts: away from a saved session, or idle. */
    fun restore(usage: DayUsage): GuardState = usage.session?.let { GuardState.Away(it) } ?: GuardState.Idle

    /**
     * A fresh open: the daily total first (spent means the limit screen), then the break after the
     * last session, then the pause.
     */
    private fun open(usage: DayUsage, at: Instant): Transition {
        budgetBlock(null, usage, null, at)?.let { return block(it, null, usage, at) }
        val breakUntil = usage.breakUntil
        return if (breakUntil != null && at.isBefore(breakUntil)) {
            block(BlockReason.SessionTimeUp, null, usage, at)
        } else {
            Transition(GuardState.Pause, listOf(Effect.ShowPause(launch.pauseSeconds)), usage)
        }
    }

    /**
     * Null while the session simply runs on. Time up comes first; a session that ends with the
     * daily total spent is blocked for that reason instead. A normal session is also checked against
     * the budgets every tick; an override session only against its own end.
     */
    private fun tickRunning(state: GuardState.Running, usage: DayUsage, surface: Surface?, at: Instant): Transition? {
        val session = state.session
        if (isOver(session, at)) {
            // An ended session is time's up (or the daily limit), never a surface block: Leave from a
            // surface block resumes the session, which would only end again.
            val ended = afterSession(session, usage)
            return block(budgetBlock(null, ended, null, at) ?: BlockReason.SessionTimeUp, session, ended, at)
        }
        return budgetCheck(session, usage, surface, at)
    }

    /**
     * Null while every budget allows. A block that keeps the session (a surface limit) keeps it for
     * when the user moves on; the others (the daily total) end it.
     */
    private fun budgetCheck(session: Session, usage: DayUsage, surface: Surface?, at: Instant): Transition? {
        val spent = budgetBlock(session, usage, surface, at) ?: return null
        return block(spent, session, if (spent.keepsSession) usage else usage.copy(session = null), at)
    }

    /** The first budget that blocks, unless [session] is an override session, which they don't touch. */
    private fun budgetBlock(session: Session?, usage: DayUsage, surface: Surface?, at: Instant): BlockReason? {
        if (session?.byOverride == true) return null
        return (budgetRules.check(usage, surface, session, at) as? BudgetVerdict.Block)?.reason
    }

    /** Today's minutes left, the standard lengths that fit them, and "Rest of today" when it's needed. */
    private fun intentionPrompt(usage: DayUsage): Effect.ShowIntention {
        val left = minutesLeft(usage)
        val fitting = launch.minuteChoices.filter { it <= left }
        val restOfToday = left.takeIf { it < launch.maxMinutes && it !in fitting }
        return Effect.ShowIntention(launch.purposes, fitting, restOfToday, left)
    }

    /** Whole minutes left today, rounded up, so a few seconds still read as a minute. */
    private fun minutesLeft(usage: DayUsage): Int {
        val left = budgets.dailyLeft(usage)
        return ((left.toMillis() + 59_999) / 60_000).toInt()
    }

    /** The counters once [session] ended: the break, and after an override session the cooldown. */
    private fun afterSession(session: Session, usage: DayUsage): DayUsage {
        val ended = session.allowedUntil
        val cooldownUntil = if (session.byOverride) {
            ended.plus(Duration.ofMinutes(policy.cooldownMinutes(usage.overridesUsed).toLong()))
        } else {
            usage.cooldownUntil
        }
        return usage.copy(
            breakUntil = ended.plus(Duration.ofMinutes(ladder.breakMinutes.toLong())),
            cooldownUntil = cooldownUntil,
            session = null,
        )
    }

    private fun block(reason: BlockReason, session: Session?, usage: DayUsage, at: Instant) = Transition(
        GuardState.Blocked(reason, session),
        listOf(Effect.ShowBlock(reason, availability(usage, at))),
        usage,
    )

    private fun availability(usage: DayUsage, at: Instant): OverrideAvailability {
        val step = policy.stepFor(usage.overridesUsed) ?: return OverrideAvailability.CapReached
        val cooldownUntil = usage.cooldownUntil
        if (cooldownUntil != null && at.isBefore(cooldownUntil)) return OverrideAvailability.CoolingDown(cooldownUntil)
        return OverrideAvailability.Available(usage.overridesUsed + 1, step)
    }

    /** Null when no override is available: the block screen stays as it is. */
    private fun startOverride(state: GuardState.Blocked, usage: DayUsage, at: Instant): Transition? {
        val available = availability(usage, at) as? OverrideAvailability.Available ?: return null
        val run = challenges.build(available.step.challenges).begin(at)
        return Transition(
            GuardState.Challenging(state.reason, state.session, run, available.number, available.step),
            listOf(Effect.ShowChallenge(run.prompt)),
            usage,
        )
    }

    private fun submit(state: GuardState.Challenging, usage: DayUsage, event: GuardEvent.ChallengeSubmitted): Transition {
        val step = state.run.submit(event.answer, event.at)
        return when (step.status) {
            ChallengeStatus.Passed -> {
                val session = Session(
                    purpose = state.session?.purpose ?: OVERRIDE_PURPOSE,
                    allowedUntil = event.at.plus(Duration.ofMinutes(state.step.grantMinutes.toLong())),
                    byOverride = true,
                    startedAt = event.at,
                )
                Transition(
                    GuardState.Running(session),
                    listOf(Effect.Dismiss),
                    usage.copy(overridesUsed = usage.overridesUsed + 1, session = session),
                )
            }
            ChallengeStatus.InProgress ->
                if (step.run.prompt == state.run.prompt) {
                    Transition(state.copy(run = step.run), emptyList(), usage) // e.g. a wait reported too early
                } else {
                    Transition(state.copy(run = step.run), listOf(Effect.ShowChallenge(step.run.prompt)), usage)
                }
        }
    }

    /**
     * Minutes outside the standard range are clamped, so a session is never longer than the largest
     * choice. It isn't cut to what's left of today: the prompt already offered only what fits, and the
     * daily total ends the session the moment it runs out (recomputing here, seconds after the prompt,
     * once shortened "Rest of today (2 min)" to 1).
     */
    private fun sessionFor(event: GuardEvent.IntentionChosen): Session {
        val minutes = event.minutes.coerceIn(1, launch.maxMinutes)
        return Session(event.purpose, event.at.plus(Duration.ofMinutes(minutes.toLong())), startedAt = event.at)
    }

    private fun isOver(session: Session, at: Instant) = !at.isBefore(session.allowedUntil)

    companion object {
        /** The purpose of an override session reached without one, e.g. reopening during the break. */
        const val OVERRIDE_PURPOSE = "Override"
    }
}
