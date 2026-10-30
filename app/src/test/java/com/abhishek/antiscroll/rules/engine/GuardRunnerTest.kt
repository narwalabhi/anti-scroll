package com.abhishek.antiscroll.rules.engine

import com.abhishek.antiscroll.rules.engine.GuardEvent.InstagramClosed
import com.abhishek.antiscroll.rules.engine.GuardEvent.InstagramOpened
import com.abhishek.antiscroll.rules.engine.GuardEvent.SurfaceChanged
import com.abhishek.antiscroll.rules.engine.GuardEvent.Tick
import com.abhishek.antiscroll.rules.budget.Budgets
import com.abhishek.antiscroll.rules.budget.DailyTotalBudgetRule
import com.abhishek.antiscroll.rules.budget.SurfaceBudgetRule
import com.abhishek.antiscroll.rules.escalation.CountLadderPolicy
import com.abhishek.antiscroll.rules.escalation.LadderSettings
import com.abhishek.antiscroll.rules.intervention.GoHomeIntervention
import com.abhishek.antiscroll.rules.intervention.StepBackIntervention
import com.abhishek.antiscroll.rules.events.EngineEvent
import com.abhishek.antiscroll.rules.events.EventSink
import com.abhishek.antiscroll.rules.profile.Surface
import com.abhishek.antiscroll.rules.settings.LaunchSettings
import com.abhishek.antiscroll.rules.time.DayClock
import com.abhishek.antiscroll.rules.time.FakeClock
import com.abhishek.antiscroll.rules.unlock.ChallengeAnswer
import com.abhishek.antiscroll.rules.unlock.ChallengeFactory
import com.abhishek.antiscroll.rules.unlock.ChallengePrompt
import com.abhishek.antiscroll.rules.unlock.TypingSentences
import com.abhishek.antiscroll.rules.usage.DayStats
import com.abhishek.antiscroll.rules.usage.DayUsage
import com.abhishek.antiscroll.rules.usage.InMemoryStatsStore
import com.abhishek.antiscroll.rules.usage.InMemoryUsageStore
import com.abhishek.antiscroll.rules.usage.UsageStore
import com.abhishek.antiscroll.rules.usage.UsageTracker
import java.time.Duration
import java.time.LocalDate
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardRunnerTest {

    private class RecordingSink : EventSink {
        val events = mutableListOf<EngineEvent>()
        override fun onEvent(event: EngineEvent) {
            events += event
        }
    }

    /** Counts saves, so tests can tell a save happened even when the counters didn't change. */
    private class CountingStore(private val inner: UsageStore = InMemoryUsageStore()) : UsageStore by inner {
        var saves = 0
        override fun save(usage: DayUsage) {
            saves++
            inner.save(usage)
        }
    }

    private class RecordingEffects : EffectHandler {
        val effects = mutableListOf<Effect>()
        override fun handle(effect: Effect) {
            effects += effect
        }
    }

    private val clock = FakeClock.at("2026-10-29T12:00:00")
    private val tracker = UsageTracker(DayClock(resetHour = 4), clock)
    private val ladder = LadderSettings()
    private val budgets = Budgets(dailyTotal = Duration.ofMinutes(90), perSurface = mapOf(Surface.REELS to Duration.ofMinutes(30)))
    private val engine = GuardEngine(
        LaunchSettings(), ladder, CountLadderPolicy(ladder), ChallengeFactory(TypingSentences(Random(1))),
        budgets, listOf(DailyTotalBudgetRule(budgets), SurfaceBudgetRule(budgets)),
        StepBackIntervention(fallback = GoHomeIntervention()),
    )
    private val store = CountingStore()
    private val sink = RecordingSink()
    private val effects = RecordingEffects()

    private val statsStore = InMemoryStatsStore()

    private fun runner() = GuardRunner(tracker, engine, store, statsStore, effects, listOf(sink), clock)

    private fun enforcingRunner() = runner().apply { enforcementEnabled = true }

    /** Moves the clock one second and ticks, as the service does. */
    private fun GuardRunner.tickSeconds(seconds: Int) = repeat(seconds) {
        clock.advance(Duration.ofSeconds(1))
        handle(Tick(clock.now()))
    }

    @Test
    fun `publishes open, surface and close`() {
        val runner = runner()
        runner.handle(InstagramOpened(clock.now()))
        runner.handle(SurfaceChanged(Surface.REELS))
        runner.handle(SurfaceChanged(Surface.REELS))
        runner.handle(InstagramClosed)
        assertEquals(
            listOf(
                EngineEvent.Opened::class,
                EngineEvent.SurfaceEntered::class,
                EngineEvent.Closed::class,
            ),
            sink.events.map { it::class },
        )
        assertEquals(Surface.REELS, (sink.events[1] as EngineEvent.SurfaceEntered).surface)
    }

    @Test
    fun `each session publishes its surface, even the same one as before`() {
        val runner = runner()
        runner.handle(InstagramOpened(clock.now()))
        runner.handle(SurfaceChanged(Surface.DMS))
        runner.handle(InstagramClosed)
        runner.handle(InstagramOpened(clock.now()))
        runner.handle(SurfaceChanged(Surface.DMS))
        assertEquals(2, sink.events.filterIsInstance<EngineEvent.SurfaceEntered>().size)
    }

    @Test
    fun `saves on close`() {
        val runner = runner()
        runner.handle(InstagramOpened(clock.now()))
        runner.handle(SurfaceChanged(Surface.FEED))
        runner.tickSeconds(5)
        assertEquals(0, store.saves)
        runner.handle(InstagramClosed)
        assertEquals(1, store.saves)
        assertEquals(5_000L, store.load(LocalDate.parse("2026-10-29"))?.totalMillis)
    }

    @Test
    fun `saves every 30 seconds, not every tick`() {
        val runner = runner()
        runner.handle(InstagramOpened(clock.now()))
        runner.tickSeconds(29)
        assertEquals(0, store.saves)
        runner.tickSeconds(1)
        assertEquals(1, store.saves)
        runner.tickSeconds(30)
        assertEquals(2, store.saves)
    }

    @Test
    fun `flush saves now`() {
        val runner = runner()
        runner.handle(InstagramOpened(clock.now()))
        runner.tickSeconds(3)
        runner.flush()
        assertEquals(3_000L, store.load(LocalDate.parse("2026-10-29"))?.totalMillis)
    }

    @Test
    fun `day reset saves the old day first and publishes DayReset`() {
        clock.set(FakeClock.at("2026-10-30T03:59:55").now())
        val runner = runner()
        runner.handle(InstagramOpened(clock.now()))
        runner.handle(SurfaceChanged(Surface.REELS))
        runner.tickSeconds(10)

        assertEquals(4_000L, store.load(LocalDate.parse("2026-10-29"))?.totalMillis)
        assertEquals(LocalDate.parse("2026-10-30"), runner.usage.day)
        val reset = sink.events.filterIsInstance<EngineEvent.DayReset>().single()
        assertEquals(LocalDate.parse("2026-10-30"), reset.newDay)
    }

    @Test
    fun `starts from the saved counters of the same day`() {
        store.save(DayUsage(LocalDate.parse("2026-10-29"), opens = 3, millisBySurface = mapOf(Surface.DMS to 9_000L)))
        val runner = runner()
        assertEquals(3, runner.usage.opens)
        runner.handle(InstagramOpened(clock.now()))
        assertEquals(4, runner.usage.opens)
    }

    @Test
    fun `starts fresh when the saved counters are from an earlier day`() {
        store.save(DayUsage(LocalDate.parse("2026-10-28"), opens = 3))
        assertEquals(0, runner().usage.opens)
        assertNull(store.load(LocalDate.parse("2026-10-29")))
    }

    // Enforcement

    @Test
    fun `with enforcement off there are no screens, but counting runs`() {
        val runner = runner()
        runner.handle(InstagramOpened(clock.now()))
        runner.handle(SurfaceChanged(Surface.REELS))
        runner.tickSeconds(3)
        assertEquals(emptyList<Effect>(), effects.effects)
        assertEquals(GuardState.Idle, runner.guardState)
        assertEquals(3_000L, runner.usage.millisBySurface[Surface.REELS])
    }

    @Test
    fun `with enforcement on, opening shows the pause and counting still runs`() {
        val runner = enforcingRunner()
        runner.handle(InstagramOpened(clock.now()))
        runner.tickSeconds(2)
        assertEquals(listOf<Effect>(Effect.ShowPause(5)), effects.effects)
        assertEquals(GuardState.Pause, runner.guardState)
        assertEquals(2_000L, runner.usage.totalMillis)
    }

    @Test
    fun `a whole session publishes its start, time up and leaving`() {
        val runner = enforcingRunner()
        runner.handle(InstagramOpened(clock.now()))
        runner.handle(GuardEvent.PauseFinished)
        runner.handle(GuardEvent.IntentionChosen("Reply to messages", 5, clock.now()))
        runner.tickSeconds(5 * 60)
        runner.handle(GuardEvent.LeaveChosen)

        val engineEvents = sink.events.filter {
            it is EngineEvent.SessionStarted || it is EngineEvent.SessionTimeUp || it is EngineEvent.LeftByChoice
        }
        assertEquals(
            listOf(EngineEvent.SessionStarted::class, EngineEvent.SessionTimeUp::class, EngineEvent.LeftByChoice::class),
            engineEvents.map { it::class },
        )
        val started = engineEvents.first() as EngineEvent.SessionStarted
        assertEquals("Reply to messages", started.purpose)
        assertEquals(5, started.minutes)
        assertEquals(Effect.GoHome, effects.effects.last())
        assertEquals(GuardState.Idle, runner.guardState)
    }

    @Test
    fun `time up saves the break, and an override is counted, saved and published`() {
        val runner = enforcingRunner()
        runner.handle(InstagramOpened(clock.now()))
        runner.handle(GuardEvent.PauseFinished)
        runner.handle(GuardEvent.IntentionChosen("Just browsing", 5, clock.now()))
        runner.tickSeconds(5 * 60)
        val breakUntil = runner.usage.breakUntil
        assertEquals(clock.now().plus(Duration.ofMinutes(15)), breakUntil)
        assertEquals(breakUntil, store.load(LocalDate.parse("2026-10-29"))?.breakUntil)

        runner.handle(GuardEvent.OverrideRequested(clock.now()))
        clock.advance(Duration.ofSeconds(30))
        runner.handle(GuardEvent.ChallengeSubmitted(ChallengeAnswer.WaitDone, clock.now()))
        runner.handle(GuardEvent.ChallengeSubmitted(ChallengeAnswer.Typed("wrong"), clock.now()))
        val text = ((runner.guardState as GuardState.Challenging).run.prompt as ChallengePrompt.Typing).text
        runner.handle(GuardEvent.ChallengeSubmitted(ChallengeAnswer.Typed(text), clock.now()))

        assertEquals(1, runner.usage.overridesUsed)
        assertEquals(1, store.load(LocalDate.parse("2026-10-29"))?.overridesUsed)
        assertEquals(1, sink.events.filterIsInstance<EngineEvent.TypingFailed>().size)
        val granted = sink.events.filterIsInstance<EngineEvent.OverrideGranted>().single()
        assertEquals(1, granted.number)
        assertEquals(10, granted.minutes)
    }

    @Test
    fun `the daily limit biting mid-session is logged once`() {
        store.save(DayUsage(LocalDate.parse("2026-10-29"), millisBySurface = mapOf(Surface.FEED to 89 * 60_000L)))
        val runner = enforcingRunner()
        startSession(runner, 5)
        runner.tickSeconds(70)
        assertEquals(BlockReason.DailyTotal, (runner.guardState as GuardState.Blocked).reason)
        runner.handle(GuardEvent.LeaveChosen)
        runner.handle(InstagramClosed)
        runner.handle(InstagramOpened(clock.now()))
        assertEquals(1, sink.events.filterIsInstance<EngineEvent.DailyLimitReached>().size)
    }

    @Test
    fun `the Reels limit covers Reels as soon as it's reached, and leave steps one screen back`() {
        store.save(DayUsage(LocalDate.parse("2026-10-29"), millisBySurface = mapOf(Surface.REELS to 29 * 60_000L + 58_000)))
        val runner = enforcingRunner()
        startSession(runner, 10)
        runner.handle(SurfaceChanged(Surface.REELS))
        runner.tickSeconds(3)
        assertEquals(BlockReason.SurfaceBudget(Surface.REELS), (runner.guardState as GuardState.Blocked).reason)
        assertEquals(Surface.REELS, sink.events.filterIsInstance<EngineEvent.SurfaceLimitReached>().single().surface)

        runner.handle(GuardEvent.LeaveChosen)
        assertEquals(listOf(Effect.ShowLeaving, Effect.GoBack), effects.effects.takeLast(2))
        assertEquals(1, sink.events.filterIsInstance<EngineEvent.LeftByChoice>().size)
        runner.tickSeconds(2) // still on Reels underneath: stays covered, no new block
        assertEquals(1, sink.events.filterIsInstance<EngineEvent.SurfaceLimitReached>().size)

        // Off Reels (here, the feed): the cover lifts and the session carries on.
        runner.handle(SurfaceChanged(Surface.FEED))
        assertTrue(runner.guardState is GuardState.Running)
        assertEquals(Effect.Dismiss, effects.effects.last())

        // Reels again: covered again at once.
        runner.handle(SurfaceChanged(Surface.REELS))
        assertTrue(runner.guardState is GuardState.Blocked)
    }

    @Test
    fun `swipes reach the counters`() {
        val runner = runner()
        runner.handle(InstagramOpened(clock.now()))
        runner.handle(GuardEvent.Swiped(Surface.REELS))
        assertEquals(mapOf(Surface.REELS to 1), runner.usage.swipesBySurface)
    }

    @Test
    fun `clearing the cooldown and break keeps the override count and takes the screen down`() {
        store.save(DayUsage(LocalDate.parse("2026-10-29"), overridesUsed = 4, cooldownUntil = clock.now().plusSeconds(3000), breakUntil = clock.now().plusSeconds(900)))
        val runner = enforcingRunner()
        runner.handle(InstagramOpened(clock.now()))
        assertTrue(runner.guardState is GuardState.Blocked) // the break

        runner.clearCooldownAndBreak()
        assertEquals(GuardState.Idle, runner.guardState)
        assertEquals(Effect.Dismiss, effects.effects.last())
        assertEquals(4, runner.usage.overridesUsed)
        assertNull(store.load(LocalDate.parse("2026-10-29"))?.cooldownUntil)
        assertNull(store.load(LocalDate.parse("2026-10-29"))?.breakUntil)
    }

    // Restarts

    private fun startSession(runner: GuardRunner, minutes: Int) {
        runner.handle(InstagramOpened(clock.now()))
        runner.handle(GuardEvent.PauseFinished)
        runner.handle(GuardEvent.IntentionChosen("Just browsing", minutes, clock.now()))
    }

    @Test
    fun `a session survives the app restarting`() {
        val first = enforcingRunner()
        startSession(first, 10)
        first.handle(InstagramClosed)

        clock.advance(Duration.ofMinutes(3))
        val restarted = enforcingRunner() // a new runner over the same store, as after a restart
        assertEquals(GuardState.Away(first.usage.session!!), restarted.guardState)
        effects.effects.clear()
        restarted.handle(InstagramOpened(clock.now()))
        assertEquals(emptyList<Effect>(), effects.effects) // resumed: no pause
        assertTrue(restarted.guardState is GuardState.Running)
    }

    @Test
    fun `a session that ended while the app was down starts its break on catching up`() {
        val first = enforcingRunner()
        startSession(first, 5)
        val endsAt = first.usage.session!!.allowedUntil
        first.handle(InstagramClosed)

        clock.advance(Duration.ofMinutes(7))
        val restarted = enforcingRunner()
        restarted.catchUp()
        assertEquals(GuardState.Idle, restarted.guardState)
        assertEquals(endsAt.plus(Duration.ofMinutes(15)), restarted.usage.breakUntil)
        assertNull(restarted.usage.session)
        assertNull(store.load(LocalDate.parse("2026-10-29"))?.session)
    }

    @Test
    fun `after midnight, Today is the new date while the limits keep counting until 4 AM`() {
        clock.set(FakeClock.at("2026-10-29T23:59:00").now())
        val runner = runner()
        runner.handle(InstagramOpened(clock.now()))
        runner.tickSeconds(120) // to 00:01
        runner.handle(InstagramClosed)

        assertEquals(LocalDate.parse("2026-10-30"), runner.stats.date)
        // The minute after midnight, plus the second the 00:00:00 tick brings across (as at 4 AM).
        assertEquals(61_000L, runner.stats.totalMillis)
        assertEquals(1, runner.stats.opens) // the visit carried over midnight
        assertEquals(LocalDate.parse("2026-10-29"), runner.usage.day) // the limit window runs to 4 AM
        assertEquals(120_000L, runner.usage.totalMillis)
        assertEquals(59_000L, statsStore.load(LocalDate.parse("2026-10-29"))?.totalMillis) // the 29th kept
        assertEquals(61_000L, statsStore.load(LocalDate.parse("2026-10-30"))?.totalMillis)
    }

    @Test
    fun `a restart picks up today's calendar stats`() {
        statsStore.save(DayStats(LocalDate.parse("2026-10-29"), opens = 7, millisBySurface = mapOf(Surface.FEED to 5_000L)))
        assertEquals(7, runner().stats.opens)
    }

    @Test
    fun `catching up with enforcement off changes no screens`() {
        val runner = runner()
        runner.catchUp()
        assertEquals(GuardState.Idle, runner.guardState)
        assertEquals(emptyList<Effect>(), effects.effects)
    }

    @Test
    fun `catching up after 4 AM starts the new day, even with enforcement off`() {
        clock.set(FakeClock.at("2026-10-30T00:04:00").now())
        val runner = runner()
        runner.handle(InstagramOpened(clock.now()))
        runner.tickSeconds(60)
        runner.handle(InstagramClosed)
        assertEquals(LocalDate.parse("2026-10-29"), runner.usage.day) // 00:04 still belongs to the 29th

        clock.set(FakeClock.at("2026-10-30T09:00:00").now())
        runner.catchUp()
        assertEquals(LocalDate.parse("2026-10-30"), runner.usage.day)
        assertEquals(0L, runner.usage.totalMillis)
        assertEquals(60_000L, store.load(LocalDate.parse("2026-10-29"))?.totalMillis) // yesterday kept
        assertEquals(LocalDate.parse("2026-10-30"), sink.events.filterIsInstance<EngineEvent.DayReset>().single().newDay)
    }

    @Test
    fun `turning enforcement off forgets the saved session`() {
        val runner = enforcingRunner()
        startSession(runner, 10)
        runner.enforcementEnabled = false
        assertNull(runner.usage.session)
        assertNull(store.load(LocalDate.parse("2026-10-29"))?.session)
    }

    @Test
    fun `turning enforcement off takes down the screen`() {
        val runner = enforcingRunner()
        runner.handle(InstagramOpened(clock.now()))
        runner.enforcementEnabled = false
        assertEquals(listOf(Effect.ShowPause(5), Effect.Dismiss), effects.effects)
        assertEquals(GuardState.Idle, runner.guardState)
    }

    @Test
    fun `turning enforcement off mid-session ends it without a screen to take down`() {
        val runner = enforcingRunner()
        runner.handle(InstagramOpened(clock.now()))
        runner.handle(GuardEvent.PauseFinished)
        runner.handle(GuardEvent.IntentionChosen("Just browsing", 10, clock.now()))
        effects.effects.clear()
        runner.enforcementEnabled = false
        assertEquals(emptyList<Effect>(), effects.effects)
        assertEquals(GuardState.Idle, runner.guardState)
    }

    @Test
    fun `turning enforcement on takes effect from the next open`() {
        val runner = runner()
        runner.handle(InstagramOpened(clock.now()))
        runner.enforcementEnabled = true
        runner.tickSeconds(2)
        assertEquals(emptyList<Effect>(), effects.effects)
        runner.handle(InstagramClosed)
        runner.handle(InstagramOpened(clock.now()))
        assertEquals(listOf<Effect>(Effect.ShowPause(5)), effects.effects)
    }

    @Test
    fun `record passes other events through`() {
        val runner = runner()
        runner.record(EngineEvent.ServiceStarted(clock.now()))
        assertEquals(listOf(EngineEvent.ServiceStarted(clock.now())), sink.events)
    }

    @Test
    fun `a date is marked guarded only when Instagram is used with enforcement on`() {
        val counting = runner()
        counting.handle(InstagramOpened(clock.now()))
        counting.tickSeconds(2)
        assertEquals(false, counting.stats.guarded)

        val guarding = enforcingRunner()
        guarding.handle(InstagramOpened(clock.now()))
        assertEquals(true, guarding.stats.guarded)
    }

    @Test
    fun `restored counters replace today's and are saved`() {
        val runner = runner()
        val today = runner.usage.day
        val restored = runner.usage.copy(opens = 9, overridesUsed = 2)
        val stats = runner.stats.copy(opens = 9)
        runner.replaceToday(restored, stats)
        assertEquals(9, runner.usage.opens)
        assertEquals(2, store.load(today)?.overridesUsed)
        assertEquals(9, statsStore.load(runner.stats.date)?.opens)
    }
}
