package com.abhishek.antiscroll.rules.engine

import com.abhishek.antiscroll.rules.engine.GuardEvent.ChallengeCancelled
import com.abhishek.antiscroll.rules.engine.GuardEvent.ChallengeSubmitted
import com.abhishek.antiscroll.rules.engine.GuardEvent.InstagramClosed
import com.abhishek.antiscroll.rules.engine.GuardEvent.InstagramOpened
import com.abhishek.antiscroll.rules.engine.GuardEvent.IntentionChosen
import com.abhishek.antiscroll.rules.engine.GuardEvent.LeaveChosen
import com.abhishek.antiscroll.rules.engine.GuardEvent.OverrideRequested
import com.abhishek.antiscroll.rules.engine.GuardEvent.PauseFinished
import com.abhishek.antiscroll.rules.engine.GuardEvent.SurfaceChanged
import com.abhishek.antiscroll.rules.engine.GuardEvent.Tick
import com.abhishek.antiscroll.rules.budget.Budgets
import com.abhishek.antiscroll.rules.budget.DailyTotalBudgetRule
import com.abhishek.antiscroll.rules.budget.SurfaceBudgetRule
import com.abhishek.antiscroll.rules.escalation.CountLadderPolicy
import com.abhishek.antiscroll.rules.escalation.LadderSettings
import com.abhishek.antiscroll.rules.intervention.GoHomeIntervention
import com.abhishek.antiscroll.rules.intervention.StepBackIntervention
import com.abhishek.antiscroll.rules.profile.Surface
import com.abhishek.antiscroll.rules.settings.LaunchSettings
import com.abhishek.antiscroll.rules.unlock.ChallengeAnswer
import com.abhishek.antiscroll.rules.unlock.ChallengeFactory
import com.abhishek.antiscroll.rules.unlock.ChallengePrompt
import com.abhishek.antiscroll.rules.unlock.TypingSentences
import com.abhishek.antiscroll.rules.usage.DayUsage
import com.abhishek.antiscroll.rules.usage.Session
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** One test or more per row of the transition table in docs/LLD.md (milestones 2 and 3). */
class GuardEngineTest {

    private val launch = LaunchSettings()
    private val ladder = LadderSettings()
    private val budgets = Budgets(dailyTotal = Duration.ofMinutes(90), perSurface = mapOf(Surface.REELS to Duration.ofMinutes(30)))
    private val engine = GuardEngine(
        launch, ladder, CountLadderPolicy(ladder), ChallengeFactory(TypingSentences(Random(1))),
        budgets, listOf(DailyTotalBudgetRule(budgets), SurfaceBudgetRule(budgets)),
        StepBackIntervention(fallback = GoHomeIntervention()),
    )

    private val t0 = Instant.parse("2026-10-29T17:00:00Z")
    private fun at(seconds: Long): Instant = t0.plusSeconds(seconds)
    private fun minutes(m: Long): Duration = Duration.ofMinutes(m)

    private val fresh = DayUsage(LocalDate.parse("2026-10-29"))
    private val session = Session("Just browsing", allowedUntil = at(600))
    private val showPause = Effect.ShowPause(5)
    private val showIntention = Effect.ShowIntention(launch.purposes, listOf(5, 10, 15, 20), restOfToday = null, minutesLeftToday = 90)
    private val firstOverride = OverrideAvailability.Available(1, ladder.steps[0])

    private fun next(state: GuardState, event: GuardEvent, usage: DayUsage = fresh, surface: Surface? = null) =
        engine.next(state, usage, surface, event, timeOf(event))

    /** The runner passes each event's time; events without one happen "now", t0 in these tests. */
    private fun timeOf(event: GuardEvent): Instant = when (event) {
        is InstagramOpened -> event.at
        is Tick -> event.at
        is IntentionChosen -> event.at
        is OverrideRequested -> event.at
        is ChallengeSubmitted -> event.at
        is ChallengeCancelled -> event.at
        is GuardEvent.EndSessionRequested -> event.at
        else -> t0
    }

    private fun assertTransition(
        from: GuardState,
        event: GuardEvent,
        to: GuardState,
        vararg effects: Effect,
        usage: DayUsage = fresh,
    ) = assertEquals(GuardEngine.Transition(to, effects.toList(), usage), next(from, event, usage))

    private fun typingText(state: GuardState) = ((state as GuardState.Challenging).run.prompt as ChallengePrompt.Typing).text

    /** From time's up: request the override, finish the wait and type the text, all at [start]. */
    private fun passOverride(blocked: GuardState.Blocked, usage: DayUsage, start: Instant): GuardEngine.Transition {
        val challenging = next(blocked, OverrideRequested(start), usage).state as GuardState.Challenging
        val waitSeconds = (challenging.run.prompt as ChallengePrompt.Wait).seconds.toLong()
        val typing = next(challenging, ChallengeSubmitted(ChallengeAnswer.WaitDone, start.plusSeconds(waitSeconds)), usage).state
        return next(typing, ChallengeSubmitted(ChallengeAnswer.Typed(typingText(typing)), start.plusSeconds(waitSeconds + 20)), usage)
    }

    // Idle

    @Test
    fun `opening Instagram shows the pause`() =
        assertTransition(GuardState.Idle, InstagramOpened(t0), GuardState.Pause(at(5)), showPause)

    @Test
    fun `other events while idle do nothing`() {
        assertTransition(GuardState.Idle, Tick(t0), GuardState.Idle)
        assertTransition(GuardState.Idle, InstagramClosed, GuardState.Idle)
        assertTransition(GuardState.Idle, PauseFinished, GuardState.Idle)
    }

    @Test
    fun `opening during the break shows time's up with the ladder, not the pause`() {
        val onBreak = fresh.copy(breakUntil = at(900))
        assertTransition(
            GuardState.Idle,
            InstagramOpened(at(899)),
            GuardState.Blocked(BlockReason.SessionTimeUp, null),
            Effect.ShowBlock(BlockReason.SessionTimeUp, firstOverride),
            usage = onBreak,
        )
    }

    @Test
    fun `opening once the break is over shows the pause`() =
        assertTransition(GuardState.Idle, InstagramOpened(at(900)), GuardState.Pause(at(905)), showPause, usage = fresh.copy(breakUntil = at(900)))

    // Pause and Intention

    @Test
    fun `the pause ends in the intention prompt`() =
        assertTransition(GuardState.Pause(t0), PauseFinished, GuardState.Intention, showIntention)

    @Test
    fun `leaving during the pause or prompt dismisses it`() {
        assertTransition(GuardState.Pause(at(5)), InstagramClosed, GuardState.Interrupted(at(5), pauseDone = false, leftAt = t0), Effect.Dismiss)
        assertTransition(GuardState.Intention, InstagramClosed, GuardState.Interrupted(t0, pauseDone = true, leftAt = t0), Effect.Dismiss)
    }

    @Test
    fun `choosing an intention starts a session of that length, and saves it`() {
        val chosen = Session("Reply to messages", allowedUntil = at(8 + 600), startedAt = at(8))
        assertEquals(
            GuardEngine.Transition(GuardState.Running(chosen), listOf(Effect.Dismiss), fresh.copy(session = chosen)),
            next(GuardState.Intention, IntentionChosen("Reply to messages", 10, at(8))),
        )
    }

    @Test
    fun `minutes outside the choices are clamped`() {
        assertEquals(
            GuardState.Running(Session("Just browsing", allowedUntil = at(20 * 60), startedAt = t0)),
            next(GuardState.Intention, IntentionChosen("Just browsing", 60, t0)).state,
        )
        assertEquals(
            GuardState.Running(Session("Just browsing", allowedUntil = at(60), startedAt = t0)),
            next(GuardState.Intention, IntentionChosen("Just browsing", 0, t0)).state,
        )
    }

    @Test
    fun `not now goes home`() =
        assertTransition(GuardState.Intention, LeaveChosen, GuardState.Idle, Effect.Dismiss, Effect.GoHome)

    // Running

    @Test
    fun `ticks before the end keep the session running`() {
        assertTransition(GuardState.Running(session), Tick(at(599)), GuardState.Running(session))
        assertTransition(GuardState.Running(session), SurfaceChanged(Surface.REELS), GuardState.Running(session))
    }

    @Test
    fun `time up shows the ladder and starts the break`() {
        val result = next(GuardState.Running(session), Tick(at(600)))
        assertEquals(GuardState.Blocked(BlockReason.SessionTimeUp, session), result.state)
        assertEquals(listOf(Effect.ShowBlock(BlockReason.SessionTimeUp, firstOverride)), result.effects)
        assertEquals(fresh.copy(breakUntil = at(600).plus(minutes(15))), result.usage)
    }

    @Test
    fun `time up clears the saved session`() {
        val result = next(GuardState.Running(session), Tick(at(600)), fresh.copy(session = session))
        assertEquals(null, result.usage.session)
    }

    @Test
    fun `leaving mid-session keeps the session`() =
        assertTransition(GuardState.Running(session), InstagramClosed, GuardState.Away(session))

    // Away

    @Test
    fun `coming back with time left resumes without a pause`() =
        assertTransition(GuardState.Away(session), InstagramOpened(at(300)), GuardState.Running(session))

    @Test
    fun `coming back soon after the session ran out is the break`() {
        val result = next(GuardState.Away(session), InstagramOpened(at(600 + 10 * 60)))
        assertEquals(GuardState.Blocked(BlockReason.SessionTimeUp, null), result.state)
        assertEquals(at(600).plus(minutes(15)), result.usage.breakUntil)
    }

    @Test
    fun `a session running out while away ends then and there`() {
        val result = next(GuardState.Away(session), Tick(at(601)), fresh.copy(session = session))
        assertEquals(GuardState.Idle, result.state)
        assertEquals(emptyList<Effect>(), result.effects)
        assertEquals(fresh.copy(breakUntil = at(600).plus(minutes(15))), result.usage)
    }

    @Test
    fun `while away with time left, ticks change nothing`() =
        assertTransition(GuardState.Away(session), Tick(at(599)), GuardState.Away(session))

    @Test
    fun `restore picks up a saved session as away`() {
        assertEquals(GuardState.Away(session), engine.restore(fresh.copy(session = session)))
        assertEquals(GuardState.Idle, engine.restore(fresh))
    }

    @Test
    fun `coming back after the break shows the pause`() {
        val result = next(GuardState.Away(session), InstagramOpened(at(600 + 15 * 60)))
        assertEquals(GuardState.Pause(at(600 + 15 * 60 + 5)), result.state)
        assertEquals(listOf<Effect>(showPause), result.effects)
    }

    // Blocked

    @Test
    fun `override starts the first step's wait`() {
        val result = next(GuardState.Blocked(BlockReason.SessionTimeUp, session), OverrideRequested(at(700)))
        val state = result.state as GuardState.Challenging
        assertEquals(1, state.number)
        assertEquals(ladder.steps[0], state.step)
        assertEquals(listOf<Effect>(Effect.ShowChallenge(ChallengePrompt.Wait(30))), result.effects)
    }

    @Test
    fun `leave after time up goes home`() =
        assertTransition(
            GuardState.Blocked(BlockReason.SessionTimeUp, session),
            LeaveChosen,
            GuardState.Idle,
            Effect.Dismiss,
            Effect.GoHome,
        )

    @Test
    fun `leaving Instagram while blocked dismisses the screen`() =
        assertTransition(GuardState.Blocked(BlockReason.SessionTimeUp, session), InstagramClosed, GuardState.Idle, Effect.Dismiss)

    // Challenging

    @Test
    fun `a wait reported too early changes nothing`() {
        val challenging = next(GuardState.Blocked(BlockReason.SessionTimeUp, session), OverrideRequested(t0)).state
        val result = next(challenging, ChallengeSubmitted(ChallengeAnswer.WaitDone, at(29)))
        // Runs aren't data classes, so compare what matters: still the same wait, and no new screen.
        assertEquals(ChallengePrompt.Wait(30), (result.state as GuardState.Challenging).run.prompt)
        assertEquals(emptyList<Effect>(), result.effects)
    }

    @Test
    fun `after the wait comes the typing`() {
        val challenging = next(GuardState.Blocked(BlockReason.SessionTimeUp, session), OverrideRequested(t0)).state
        val result = next(challenging, ChallengeSubmitted(ChallengeAnswer.WaitDone, at(30)))
        val prompt = (result.effects.single() as Effect.ShowChallenge).prompt as ChallengePrompt.Typing
        assertEquals(0.95, prompt.minAccuracy, 0.0)
        assertTrue(result.state is GuardState.Challenging)
    }

    @Test
    fun `failed typing gets new text and keeps the wait`() {
        val challenging = next(GuardState.Blocked(BlockReason.SessionTimeUp, session), OverrideRequested(t0)).state
        val typing = next(challenging, ChallengeSubmitted(ChallengeAnswer.WaitDone, at(30))).state
        val retry = next(typing, ChallengeSubmitted(ChallengeAnswer.Typed("nope"), at(40)))
        assertTrue(retry.state is GuardState.Challenging)
        assertNotEquals(typingText(typing), typingText(retry.state))
        assertTrue((retry.effects.single() as Effect.ShowChallenge).prompt is ChallengePrompt.Typing)
    }

    @Test
    fun `passing grants the step's minutes and counts the override`() {
        val result = passOverride(GuardState.Blocked(BlockReason.SessionTimeUp, session), fresh, at(700))
        val granted = Session("Just browsing", allowedUntil = at(700 + 30 + 20).plus(minutes(10)), byOverride = true, startedAt = at(700 + 30 + 20))
        assertEquals(GuardState.Running(granted), result.state)
        assertEquals(listOf<Effect>(Effect.Dismiss), result.effects)
        assertEquals(1, result.usage.overridesUsed)
        assertEquals(granted, result.usage.session)
    }

    @Test
    fun `an override reached during the break gets a generic purpose`() {
        val result = passOverride(GuardState.Blocked(BlockReason.SessionTimeUp, null), fresh, t0)
        assertEquals(GuardEngine.OVERRIDE_PURPOSE, (result.state as GuardState.Running).session.purpose)
    }

    @Test
    fun `cancel returns to time's up`() {
        val challenging = next(GuardState.Blocked(BlockReason.SessionTimeUp, session), OverrideRequested(t0)).state
        assertTransition(
            challenging,
            ChallengeCancelled(at(10)),
            GuardState.Blocked(BlockReason.SessionTimeUp, session),
            Effect.ShowBlock(BlockReason.SessionTimeUp, firstOverride),
        )
    }

    @Test
    fun `leaving during a challenge loses it`() {
        val challenging = next(GuardState.Blocked(BlockReason.SessionTimeUp, session), OverrideRequested(t0)).state
        assertTransition(challenging, InstagramClosed, GuardState.Idle, Effect.Dismiss)
    }

    // Cooldown and cap

    @Test
    fun `an override session ending starts the cooldown and the break`() {
        val overrideSession = Session("Just browsing", allowedUntil = at(600), byOverride = true)
        val oneUsed = fresh.copy(overridesUsed = 1)
        val result = next(GuardState.Running(overrideSession), Tick(at(600)), oneUsed)
        val cooldownUntil = at(600).plus(minutes(20))
        assertEquals(oneUsed.copy(cooldownUntil = cooldownUntil, breakUntil = at(600).plus(minutes(15))), result.usage)
        assertEquals(
            listOf<Effect>(Effect.ShowBlock(BlockReason.SessionTimeUp, OverrideAvailability.CoolingDown(cooldownUntil))),
            result.effects,
        )
    }

    @Test
    fun `the cooldown grows by 10 minutes per earlier override`() {
        val overrideSession = Session("Just browsing", allowedUntil = at(600), byOverride = true)
        val result = next(GuardState.Running(overrideSession), Tick(at(600)), fresh.copy(overridesUsed = 3))
        assertEquals(at(600).plus(minutes(40)), result.usage.cooldownUntil)
    }

    @Test
    fun `a normal session ending starts no cooldown`() {
        assertEquals(null, next(GuardState.Running(session), Tick(at(600))).usage.cooldownUntil)
    }

    @Test
    fun `no override while cooling down`() {
        val cooling = fresh.copy(overridesUsed = 1, cooldownUntil = at(1200))
        assertTransition(
            GuardState.Blocked(BlockReason.SessionTimeUp, session),
            OverrideRequested(at(1199)),
            GuardState.Blocked(BlockReason.SessionTimeUp, session),
            usage = cooling,
        )
    }

    @Test
    fun `after the cooldown the next step is offered`() {
        val cooled = fresh.copy(overridesUsed = 1, cooldownUntil = at(1200))
        val result = next(GuardState.Idle, InstagramOpened(at(1200)), cooled.copy(breakUntil = at(1300)))
        assertEquals(
            listOf<Effect>(Effect.ShowBlock(BlockReason.SessionTimeUp, OverrideAvailability.Available(2, ladder.steps[1]))),
            result.effects,
        )
    }

    @Test
    fun `after 5 overrides there are no more today`() {
        val capped = fresh.copy(overridesUsed = 5, breakUntil = at(900))
        val result = next(GuardState.Idle, InstagramOpened(t0), capped)
        assertEquals(listOf<Effect>(Effect.ShowBlock(BlockReason.SessionTimeUp, OverrideAvailability.CapReached)), result.effects)
        assertTransition(
            GuardState.Blocked(BlockReason.SessionTimeUp, null),
            OverrideRequested(t0),
            GuardState.Blocked(BlockReason.SessionTimeUp, null),
            usage = capped,
        )
    }

    @Test
    fun `a new day's counters offer the first step again`() {
        // The runner resets the counters at 4 AM; the engine only sees the fresh day.
        val newDay = DayUsage(LocalDate.parse("2026-10-30"))
        val result = next(GuardState.Blocked(BlockReason.SessionTimeUp, null), OverrideRequested(t0), newDay)
        assertEquals(1, (result.state as GuardState.Challenging).number)
    }

    // Daily total

    /** A day with [minutes] of Instagram so far. */
    private fun used(minutes: Long, plusSeconds: Long = 0) =
        fresh.copy(millisBySurface = mapOf(Surface.FEED to (minutes * 60 + plusSeconds) * 1_000))

    private val limitBlock = Effect.ShowBlock(BlockReason.DailyTotal, firstOverride)

    @Test
    fun `opening with the total spent goes straight to the limit screen`() =
        assertTransition(
            GuardState.Idle,
            InstagramOpened(t0),
            GuardState.Blocked(BlockReason.DailyTotal, null),
            limitBlock,
            usage = used(90),
        )

    @Test
    fun `the spent total wins over the break`() {
        val result = next(GuardState.Idle, InstagramOpened(t0), used(95).copy(breakUntil = at(900)))
        assertEquals(GuardState.Blocked(BlockReason.DailyTotal, null), result.state)
    }

    @Test
    fun `the total running out mid-session ends the session at once`() {
        val running = used(90).copy(session = session)
        val result = next(GuardState.Running(session), Tick(at(100)), running)
        assertEquals(GuardState.Blocked(BlockReason.DailyTotal, session), result.state)
        assertEquals(listOf<Effect>(limitBlock), result.effects)
        assertEquals(null, result.usage.session)
    }

    @Test
    fun `under the total, a session runs on`() =
        assertTransition(GuardState.Running(session), Tick(at(100)), GuardState.Running(session), usage = used(89, 59))

    @Test
    fun `a session ending with the total spent shows the limit, not time up`() {
        val result = next(GuardState.Running(session), Tick(at(600)), used(91).copy(session = session))
        assertEquals(GuardState.Blocked(BlockReason.DailyTotal, session), result.state)
        assertEquals(at(600).plus(minutes(15)), result.usage.breakUntil)
    }

    @Test
    fun `an override session runs past the total`() {
        val overrideSession = Session("Override", allowedUntil = at(600), byOverride = true)
        assertTransition(GuardState.Running(overrideSession), Tick(at(100)), GuardState.Running(overrideSession), usage = used(120))
    }

    @Test
    fun `when an override session ends past the total, the limit screen comes back with the cooldown`() {
        val overrideSession = Session("Override", allowedUntil = at(600), byOverride = true)
        val result = next(GuardState.Running(overrideSession), Tick(at(600)), used(120).copy(overridesUsed = 1))
        assertEquals(GuardState.Blocked(BlockReason.DailyTotal, overrideSession), result.state)
        assertEquals(
            listOf<Effect>(Effect.ShowBlock(BlockReason.DailyTotal, OverrideAvailability.CoolingDown(at(600).plus(minutes(20))))),
            result.effects,
        )
    }

    @Test
    fun `coming back to a normal session with the total spent shows the limit`() {
        val result = next(GuardState.Away(session), InstagramOpened(at(300)), used(90).copy(session = session))
        assertEquals(GuardState.Blocked(BlockReason.DailyTotal, session), result.state)
        assertEquals(null, result.usage.session)
    }

    @Test
    fun `coming back to an override session resumes it past the total`() {
        val overrideSession = Session("Override", allowedUntil = at(600), byOverride = true)
        assertTransition(GuardState.Away(overrideSession), InstagramOpened(at(300)), GuardState.Running(overrideSession), usage = used(120))
    }

    @Test
    fun `an override from the limit screen grants a session past the total`() {
        val result = passOverride(GuardState.Blocked(BlockReason.DailyTotal, null), used(95), t0)
        val granted = (result.state as GuardState.Running).session
        assertTrue(granted.byOverride)
        assertEquals(1, result.usage.overridesUsed)
    }

    @Test
    fun `leave from the limit screen goes home`() =
        assertTransition(
            GuardState.Blocked(BlockReason.DailyTotal, null),
            LeaveChosen,
            GuardState.Idle,
            Effect.Dismiss,
            Effect.GoHome,
            usage = used(95),
        )

    // Reels limit

    /** A day with [reels] minutes of Reels (and nothing else), under the daily total. */
    private fun reels(reels: Long) = fresh.copy(millisBySurface = mapOf(Surface.REELS to reels * 60_000))

    private val reelsBlocked = BlockReason.SurfaceBudget(Surface.REELS)
    private val reelsBlock = Effect.ShowBlock(reelsBlocked, firstOverride)

    @Test
    fun `reaching Reels with the Reels limit spent covers it at once, keeping the session`() {
        val day = reels(30).copy(session = session)
        assertTransition(
            GuardState.Running(session),
            SurfaceChanged(Surface.REELS),
            GuardState.Blocked(reelsBlocked, session),
            reelsBlock,
            usage = day,
        )
    }

    @Test
    fun `the Reels limit running out on Reels blocks on the next tick`() {
        val day = reels(30).copy(session = session)
        val result = next(GuardState.Running(session), Tick(at(100)), day, surface = Surface.REELS)
        assertEquals(GuardState.Blocked(reelsBlocked, session), result.state)
        assertEquals(session, result.usage.session)
    }

    @Test
    fun `elsewhere in Instagram the spent Reels limit changes nothing`() {
        assertTransition(GuardState.Running(session), SurfaceChanged(Surface.FEED), GuardState.Running(session), usage = reels(45))
        assertEquals(GuardState.Running(session), next(GuardState.Running(session), Tick(at(100)), reels(45), Surface.DMS).state)
    }

    private val reelsBlockedState = GuardState.Blocked(reelsBlocked, session)

    @Test
    fun `leave from the Reels block steps one screen back, keeping the cover up`() =
        assertTransition(reelsBlockedState, LeaveChosen, reelsBlockedState, Effect.ShowLeaving, Effect.GoBack, usage = reels(45))

    @Test
    fun `while going back, ticks on Reels don't block again`() =
        assertEquals(reelsBlockedState, next(reelsBlockedState, Tick(at(101)), reels(45), Surface.REELS).state)

    @Test
    fun `landing anywhere but Reels lifts the cover and the session carries on`() {
        assertTransition(reelsBlockedState, SurfaceChanged(Surface.FEED), GuardState.Running(session), Effect.Dismiss)
        assertTransition(reelsBlockedState, SurfaceChanged(Surface.DMS), GuardState.Running(session), Effect.Dismiss)
        assertTransition(reelsBlockedState, SurfaceChanged(Surface.EXPLORE), GuardState.Running(session), Effect.Dismiss)
    }

    @Test
    fun `if one step back still lands on Reels, the block stays and leave steps back again`() {
        assertTransition(reelsBlockedState, SurfaceChanged(Surface.REELS), reelsBlockedState)
        assertTransition(reelsBlockedState, LeaveChosen, reelsBlockedState, Effect.ShowLeaving, Effect.GoBack, usage = reels(45))
    }

    @Test
    fun `closing Instagram on the Reels block keeps the session for later`() =
        assertTransition(reelsBlockedState, InstagramClosed, GuardState.Away(session), Effect.Dismiss)

    @Test
    fun `the other blocks never lift by moving around`() =
        assertTransition(
            GuardState.Blocked(BlockReason.DailyTotal, null),
            SurfaceChanged(Surface.FEED),
            GuardState.Blocked(BlockReason.DailyTotal, null),
        )

    @Test
    fun `the engine carries out whatever the intervention plans`() {
        val homeOnly = GuardEngine(
            launch, ladder, CountLadderPolicy(ladder), ChallengeFactory(TypingSentences(Random(1))),
            budgets, listOf(DailyTotalBudgetRule(budgets), SurfaceBudgetRule(budgets)),
            GoHomeIntervention(),
        )
        assertEquals(
            GuardEngine.Transition(GuardState.Idle, listOf(Effect.Dismiss, Effect.GoHome), reels(45)),
            homeOnly.next(reelsBlockedState, reels(45), Surface.REELS, LeaveChosen, t0),
        )
    }

    @Test
    fun `moving off Reels underneath the block lifts it`() {
        assertTransition(GuardState.Blocked(reelsBlocked, session), SurfaceChanged(Surface.FEED), GuardState.Running(session), Effect.Dismiss)
        assertTransition(GuardState.Blocked(reelsBlocked, session), SurfaceChanged(Surface.REELS), GuardState.Blocked(reelsBlocked, session))
    }

    @Test
    fun `closing Instagram on the Reels block keeps the session`() {
        assertTransition(GuardState.Blocked(reelsBlocked, session), InstagramClosed, GuardState.Away(session), Effect.Dismiss)
        val challenging = next(GuardState.Blocked(reelsBlocked, session), OverrideRequested(t0)).state
        assertTransition(challenging, InstagramClosed, GuardState.Away(session), Effect.Dismiss)
    }

    @Test
    fun `a spent daily total wins over the Reels limit`() {
        val both = fresh.copy(millisBySurface = mapOf(Surface.REELS to 45 * 60_000L, Surface.FEED to 50 * 60_000L))
        val result = next(GuardState.Running(session), Tick(at(100)), both, surface = Surface.REELS)
        assertEquals(GuardState.Blocked(BlockReason.DailyTotal, session), result.state)
    }

    @Test
    fun `an override session watches Reels past the limit`() {
        val overrideSession = Session("Just browsing", allowedUntil = at(600), byOverride = true)
        assertTransition(GuardState.Running(overrideSession), SurfaceChanged(Surface.REELS), GuardState.Running(overrideSession), usage = reels(45))
        assertEquals(
            GuardState.Running(overrideSession),
            next(GuardState.Running(overrideSession), Tick(at(100)), reels(45), Surface.REELS).state,
        )
    }

    @Test
    fun `an override from the Reels block grants an override session`() {
        val result = passOverride(GuardState.Blocked(reelsBlocked, session), reels(45), t0)
        assertTrue((result.state as GuardState.Running).session.byOverride)
    }

    @Test
    fun `a session ending on Reels with the Reels limit spent is time's up, not a Reels block`() {
        val result = next(GuardState.Running(session), Tick(at(600)), reels(45).copy(session = session), Surface.REELS)
        assertEquals(GuardState.Blocked(BlockReason.SessionTimeUp, session), result.state)
    }

    // The prompt and what's left today

    @Test
    fun `the prompt offers only lengths that fit, plus rest of today`() {
        assertEquals(
            Effect.ShowIntention(launch.purposes, listOf(5), restOfToday = 8, minutesLeftToday = 8),
            next(GuardState.Pause(t0), PauseFinished, used(82)).effects.single(),
        )
        assertEquals(
            Effect.ShowIntention(launch.purposes, listOf(5, 10), restOfToday = 12, minutesLeftToday = 12),
            next(GuardState.Pause(t0), PauseFinished, used(78)).effects.single(),
        )
        assertEquals(
            Effect.ShowIntention(launch.purposes, listOf(5, 10), restOfToday = null, minutesLeftToday = 10),
            next(GuardState.Pause(t0), PauseFinished, used(80)).effects.single(),
        )
        assertEquals(
            Effect.ShowIntention(launch.purposes, listOf(5, 10, 15, 20), restOfToday = null, minutesLeftToday = 20),
            next(GuardState.Pause(t0), PauseFinished, used(70)).effects.single(),
        )
    }

    @Test
    fun `seconds left still read as a minute`() {
        assertEquals(
            Effect.ShowIntention(launch.purposes, emptyList(), restOfToday = 1, minutesLeftToday = 1),
            next(GuardState.Pause(t0), PauseFinished, used(89, 30)).effects.single(),
        )
    }

    @Test
    fun `rest of today keeps its length, and the daily total ends it when it runs out`() {
        // Offered "Rest of today (2 min)" with 61 s left; chosen 11 s later, it's still 2 minutes.
        val chosen = next(GuardState.Intention, IntentionChosen("Just browsing", 2, t0), used(89, 10))
        val session = Session("Just browsing", allowedUntil = at(120), startedAt = t0)
        assertEquals(GuardState.Running(session), chosen.state)
        // A minute later the total is spent: the limit screen, not time's up.
        val ended = next(chosen.state, Tick(at(60)), used(90).copy(session = session))
        assertEquals(GuardState.Blocked(BlockReason.DailyTotal, session), ended.state)
    }

    // A whole visit

    @Test
    fun `open, choose, run out, override, run out again, cool down`() {
        var usage = fresh
        var state: GuardState = GuardState.Idle
        fun go(event: GuardEvent) = next(state, event, usage).also { state = it.state; usage = it.usage }

        go(InstagramOpened(at(-5))) // the 5-second pause is over at t0, when PauseFinished comes
        go(PauseFinished)
        go(IntentionChosen("Look something up", 5, at(7)))
        go(Tick(at(7 + 300)))
        assertTrue(state is GuardState.Blocked)

        passOverride(state as GuardState.Blocked, usage, at(400)).also { state = it.state; usage = it.usage }
        val granted = (state as GuardState.Running).session
        assertEquals(1, usage.overridesUsed)

        val end = go(Tick(granted.allowedUntil))
        assertEquals(
            Effect.ShowBlock(BlockReason.SessionTimeUp, OverrideAvailability.CoolingDown(granted.allowedUntil.plus(minutes(20)))),
            end.effects.single(),
        )
        go(LeaveChosen)
        assertEquals(GuardState.Idle, state)
    }

    @Test
    fun `End now while away ends the session now, starting the break`() {
        val running = fresh.copy(session = session)
        val result = next(GuardState.Away(session), GuardEvent.EndSessionRequested(at(120)), running)
        assertEquals(GuardState.Idle, result.state)
        assertEquals(null, result.usage.session)
        assertEquals(at(120).plus(minutes(ladder.breakMinutes.toLong())), result.usage.breakUntil)
        assertEquals(null, result.usage.cooldownUntil)
    }

    @Test
    fun `End now on an override session starts its cooldown now`() {
        val override = Session("Override", allowedUntil = at(600), byOverride = true)
        val usage = fresh.copy(session = override, overridesUsed = 1)
        val result = next(GuardState.Running(override), GuardEvent.EndSessionRequested(at(60)), usage)
        assertEquals(GuardState.Idle, result.state)
        assertEquals(listOf<Effect>(Effect.Dismiss), result.effects)
        assertEquals(at(60).plus(minutes(ladder.cooldownBaseMinutes.toLong())), result.usage.cooldownUntil)
    }

    @Test
    fun `End now with no session does nothing`() {
        val result = next(GuardState.Idle, GuardEvent.EndSessionRequested(at(60)))
        assertEquals(GuardState.Idle, result.state)
        assertTrue(result.effects.isEmpty())
    }

    @Test
    fun `a chosen session records when it started`() {
        val result = next(GuardState.Intention, IntentionChosen("Just browsing", 10, at(30)))
        assertEquals(at(30), (result.state as GuardState.Running).session.startedAt)
    }

    @Test
    fun `Close Instagram on the pause goes home`() =
        assertTransition(GuardState.Pause(t0), LeaveChosen, GuardState.Idle, Effect.Dismiss, Effect.GoHome)

    @Test
    fun `the pause can't be skipped before its time`() {
        val result = next(GuardState.Pause(at(5)), PauseFinished)
        assertEquals(GuardState.Pause(at(5)), result.state)
        assertTrue(result.effects.isEmpty())
    }

    @Test
    fun `back within two minutes, the pause carries on with what's left of it`() {
        val away = GuardState.Interrupted(at(5), pauseDone = false, leftAt = at(2))
        val back = next(away, InstagramOpened(at(3)))
        assertEquals(GuardState.Pause(at(5)), back.state)
        assertEquals(listOf<Effect>(Effect.ShowPause(2)), back.effects)

        val later = next(away, InstagramOpened(at(60)))
        assertEquals(listOf<Effect>(Effect.ShowPause(0)), later.effects) // it ran out while away
    }

    @Test
    fun `back within two minutes from the prompt, the prompt comes straight back`() {
        val away = GuardState.Interrupted(at(5), pauseDone = true, leftAt = at(10))
        assertTransition(away, InstagramOpened(at(40)), GuardState.Intention, showIntention)
    }

    @Test
    fun `back after two minutes, a fresh pause`() {
        val away = GuardState.Interrupted(at(5), pauseDone = false, leftAt = at(2))
        assertTransition(away, InstagramOpened(at(200)), GuardState.Pause(at(205)), showPause)
        assertEquals(GuardState.Idle, next(away, Tick(at(200))).state)
    }
}
