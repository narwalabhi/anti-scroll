package com.abhishek.antiscroll.rules.engine

import com.abhishek.antiscroll.rules.events.EngineEvent
import com.abhishek.antiscroll.rules.events.EventSink
import com.abhishek.antiscroll.rules.time.Clock
import com.abhishek.antiscroll.rules.unlock.ChallengePrompt
import com.abhishek.antiscroll.rules.usage.DayStats
import com.abhishek.antiscroll.rules.usage.DayUsage
import com.abhishek.antiscroll.rules.usage.StatsStore
import com.abhishek.antiscroll.rules.usage.UsageStore
import com.abhishek.antiscroll.rules.usage.UsageTracker
import java.time.Duration
import java.time.Instant

/**
 * Feeds every event to [UsageTracker] (counting always runs) and, while [enforcementEnabled],
 * to [GuardEngine], handing its effects to [effects]. Holds the counters and the engine state in
 * memory, publishes [EngineEvent]s, and saves through [UsageStore]: every [SAVE_INTERVAL], on
 * close, before a day reset and on [flush]. Ticks are never saved one by one.
 */
class GuardRunner(
    private val tracker: UsageTracker,
    engine: GuardEngine,
    private val store: UsageStore,
    private val statsStore: StatsStore,
    private val effects: EffectHandler,
    private val sinks: List<EventSink>,
    private val clock: Clock,
) {
    /** Swappable, for the debug "short timers"; the current state carries over. */
    var engine: GuardEngine = engine

    private var state: UsageTracker.State
    private var lastSavedAt: Instant

    init {
        val now = clock.now()
        state = tracker.start(now, store.load(tracker.dayOf(now)), statsStore.load(tracker.dateOf(now)))
        lastSavedAt = now
    }

    /** What the engine is doing: a saved session comes back as Away, so a restart doesn't lose it. */
    var guardState: GuardState = engine.restore(state.usage)
        private set

    /** The limit window's counters, 4 AM to 4 AM: what the budgets use. */
    val usage: DayUsage get() = state.usage

    /** Today's calendar-date counters, midnight to midnight: what the status screen shows as "Today". */
    val stats: DayStats get() = state.stats
    val isOpen: Boolean get() = state.isOpen

    /**
     * Off: only counting. Turning it off takes down any screen that's up; turning it on takes
     * effect from the next time Instagram opens.
     */
    var enforcementEnabled: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (!value) {
                if (guardState.showsScreen()) effects.handle(Effect.Dismiss)
                guardState = GuardState.Idle
                if (state.usage.session != null) {
                    state = state.copy(usage = state.usage.copy(session = null))
                    save(clock.now())
                }
            }
        }

    fun handle(event: GuardEvent) {
        val at = when (event) {
            is GuardEvent.InstagramOpened -> event.at
            is GuardEvent.Tick -> event.at
            is GuardEvent.IntentionChosen -> event.at
            is GuardEvent.OverrideRequested -> event.at
            is GuardEvent.ChallengeSubmitted -> event.at
            is GuardEvent.ChallengeCancelled -> event.at
            is GuardEvent.EndSessionRequested -> event.at
            else -> clock.now()
        }
        count(event, at)
        if (enforcementEnabled) decide(event, at)
        countOpenOnEntry()
    }

    /**
     * Brings things up to now while Instagram is closed: a new day starts at the reset hour, and a
     * session that ran out (or ended while the app was down) ends, so its break and cooldown apply
     * before Instagram is reopened. The status screen calls it every second.
     */
    fun catchUp() {
        if (!isOpen) handle(GuardEvent.Tick(clock.now()))
    }

    /**
     * Ends today's cooldown and break now, and takes down any screen, so the next open starts
     * fresh. The override count stays. For testing (a debug command); nothing in the app offers it.
     */
    fun clearCooldownAndBreak() {
        state = state.copy(usage = state.usage.copy(cooldownUntil = null, breakUntil = null))
        save(clock.now())
        if (guardState.showsScreen()) effects.handle(Effect.Dismiss)
        if (guardState !is GuardState.Running) guardState = GuardState.Idle
    }

    /**
     * Puts restored counters in place of today's (docs/LLD.md, Backup folder): the caller has merged
     * them with what was counted since. Only today's window and date are held here; a saved session
     * comes back as Away, as after a restart, unless a screen or session is already in progress.
     */
    fun replaceToday(usage: DayUsage, stats: DayStats) {
        state = state.copy(
            usage = if (usage.day == state.usage.day) usage else state.usage,
            stats = if (stats.date == state.stats.date) stats else state.stats,
        )
        if (guardState == GuardState.Idle) guardState = engine.restore(state.usage)
        save(clock.now())
    }

    /** Events that don't come from Instagram, such as the service starting or a heartbeat. */
    fun record(event: EngineEvent) = publish(event)

    /** Saves now: screen off, service stopping. */
    fun flush() = save(clock.now())

    private fun count(event: GuardEvent, at: Instant) {
        val before = state
        // With guarding on, an open counts only once Instagram can be used (see countOpenOnEntry).
        state = tracker.next(before, event, countOpen = !enforcementEnabled)

        if (state.usage.day.isAfter(before.usage.day)) {
            store.save(before.usage) // the old day's final counters
            publish(EngineEvent.DayReset(at, state.usage.day))
        }
        if (state.stats.date.isAfter(before.stats.date)) statsStore.save(before.stats) // the old date's final stats
        // Marks the date as a guarding day for the export's baseline-versus-guarding comparison.
        if (enforcementEnabled && state.isOpen && !state.stats.guarded) state = state.copy(stats = state.stats.copy(guarded = true))
        if (!before.isOpen && state.isOpen) publish(EngineEvent.Opened(at))
        val surface = state.surface
        if (surface != null && surface != before.surface) publish(EngineEvent.SurfaceEntered(at, surface))

        if (before.isOpen && !state.isOpen) {
            publish(EngineEvent.Closed(at))
            save(at)
        } else if (at.isBefore(lastSavedAt) || Duration.between(lastSavedAt, at) >= SAVE_INTERVAL) {
            save(at)
        }
    }

    /**
     * With guarding on, a visit counts as an open when the user gets into Instagram (a session starts
     * or resumes), not when they meet the pause or a block and turn back. Once per visit.
     */
    private fun countOpenOnEntry() {
        if (enforcementEnabled && guardState is GuardState.Running && state.isOpen && !state.openCounted) {
            state = tracker.countOpen(state)
        }
    }

    private fun decide(event: GuardEvent, at: Instant) {
        val before = guardState
        val transition = engine.next(before, state.usage, state.surface, event, at)
        guardState = transition.state
        // The engine changes the counters when a session ends or an override is granted; keep them.
        if (transition.usage != state.usage) {
            state = state.copy(usage = transition.usage)
            save(at)
        }
        publishDecisions(before, transition, event, at)
        transition.effects.forEach(effects::handle)
    }

    private fun publishDecisions(before: GuardState, transition: GuardEngine.Transition, event: GuardEvent, at: Instant) {
        val after = transition.state
        if (before == GuardState.Intention && event is GuardEvent.IntentionChosen && after is GuardState.Running) {
            publish(EngineEvent.SessionStarted(at, event.purpose, event.minutes))
        }
        // Logged when it bites, during a session; reopening while blocked is already visible as an open.
        if (after is GuardState.Blocked && (before is GuardState.Running || before is GuardState.Away)) {
            when (val reason = after.reason) {
                BlockReason.SessionTimeUp -> if (before is GuardState.Running) publish(EngineEvent.SessionTimeUp(at))
                BlockReason.DailyTotal -> publish(EngineEvent.DailyLimitReached(at))
                is BlockReason.SurfaceBudget -> publish(EngineEvent.SurfaceLimitReached(at, reason.surface))
            }
        }
        if (before is GuardState.Challenging && after is GuardState.Running) {
            publish(EngineEvent.OverrideGranted(at, before.number, before.step.grantMinutes))
        }
        if (before is GuardState.Challenging && after is GuardState.Challenging && before.run.prompt is ChallengePrompt.Typing &&
            after.run.prompt is ChallengePrompt.Typing && after.run.prompt != before.run.prompt
        ) {
            publish(EngineEvent.TypingFailed(at))
        }
        if (event is GuardEvent.EndSessionRequested && (before is GuardState.Running || before is GuardState.Away)) {
            publish(EngineEvent.SessionEndedEarly(at))
        }
        // "Not now" or Leave on any block, whatever the intervention made of it.
        if (event == GuardEvent.LeaveChosen && (before is GuardState.Pause || before == GuardState.Intention || before is GuardState.Blocked)) {
            publish(EngineEvent.LeftByChoice(at))
        }
    }

    private fun GuardState.showsScreen() =
        this is GuardState.Pause || this == GuardState.Intention || this is GuardState.Blocked ||
            this is GuardState.Challenging

    private fun save(at: Instant) {
        store.save(state.usage)
        statsStore.save(state.stats)
        lastSavedAt = at
    }

    private fun publish(event: EngineEvent) = sinks.forEach { it.onEvent(event) }

    companion object {
        val SAVE_INTERVAL: Duration = Duration.ofSeconds(30)
    }
}
