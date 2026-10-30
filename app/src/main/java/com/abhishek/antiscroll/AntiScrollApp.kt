package com.abhishek.antiscroll

import android.app.Application
import android.content.Context
import com.abhishek.antiscroll.data.DataStoreHealthStore
import com.abhishek.antiscroll.data.DataStoreSettingsStore
import com.abhishek.antiscroll.data.DataStoreStatsStore
import com.abhishek.antiscroll.data.DataStoreUsageStore
import com.abhishek.antiscroll.data.InstagramRulesLoader
import com.abhishek.antiscroll.data.JsonlEventLog
import com.abhishek.antiscroll.data.LogcatEventSink
import com.abhishek.antiscroll.guard.FallbackStarter
import com.abhishek.antiscroll.present.OverlayStatus
import com.abhishek.antiscroll.data.FirestoreCloudStore
import com.abhishek.antiscroll.rules.sync.CloudStore
import com.abhishek.antiscroll.ui.AccountSignIn
import com.abhishek.antiscroll.ui.BackupFolder
import com.abhishek.antiscroll.ui.CloudSync
import com.abhishek.antiscroll.rules.health.HealthRecorder
import com.abhishek.antiscroll.rules.health.TrackingHealth
import com.abhishek.antiscroll.system.SystemChecks
import com.abhishek.antiscroll.rules.budget.Budgets
import com.abhishek.antiscroll.rules.budget.DailyTotalBudgetRule
import com.abhishek.antiscroll.rules.budget.SurfaceBudgetRule
import com.abhishek.antiscroll.rules.engine.GuardEngine
import com.abhishek.antiscroll.rules.escalation.CountLadderPolicy
import com.abhishek.antiscroll.rules.escalation.LadderSettings
import com.abhishek.antiscroll.rules.intervention.GoHomeIntervention
import com.abhishek.antiscroll.rules.intervention.Intervention
import com.abhishek.antiscroll.rules.intervention.StepBackIntervention
import com.abhishek.antiscroll.rules.engine.GuardRunner
import com.abhishek.antiscroll.rules.profile.InstagramProfile
import com.abhishek.antiscroll.rules.profile.InstagramRules
import com.abhishek.antiscroll.rules.events.EngineEvent
import com.abhishek.antiscroll.rules.settings.AppSettings
import com.abhishek.antiscroll.rules.settings.ChangeSource
import com.abhishek.antiscroll.rules.settings.LaunchSettings
import com.abhishek.antiscroll.rules.settings.SettingsChanger
import com.abhishek.antiscroll.rules.settings.SettingsStore
import com.abhishek.antiscroll.rules.time.Clock
import com.abhishek.antiscroll.rules.time.DayClock
import com.abhishek.antiscroll.rules.unlock.ChallengeFactory
import com.abhishek.antiscroll.rules.unlock.TypingSentences
import com.abhishek.antiscroll.rules.usage.StatsStore
import com.abhishek.antiscroll.rules.usage.UsageStore
import com.abhishek.antiscroll.rules.usage.UsageTracker
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

class AntiScrollApp : Application() {

    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}

/** Creates every object and connects them by hand. No DI library, no singletons. */
class AppGraph(context: Context) {
    val clock: Clock = SystemTimeClock()
    val dayClock = DayClock(resetHour = RESET_HOUR)
    val instagramRules: InstagramRules = InstagramRulesLoader(context.assets).load()
    val instagramProfile = InstagramProfile(instagramRules)
    val launchSettings = LaunchSettings()
    val ladderSettings = LadderSettings()
    private val challenges = ChallengeFactory(TypingSentences(Random.Default))

    /** What Leave does: one screen back from a surface block (the rest of Instagram works), home otherwise. */
    private val intervention: Intervention = StepBackIntervention(fallback = GoHomeIntervention())
    val usageStore: UsageStore = DataStoreUsageStore(context)
    val statsStore: StatsStore = DataStoreStatsStore(context)
    private val settingsStore: SettingsStore = DataStoreSettingsStore(context)

    /** When the accessibility service ran and when it didn't (docs/LLD.md, Fallback watcher). */
    val health = HealthRecorder(TrackingHealth(), DataStoreHealthStore(context))
    val systemChecks = SystemChecks(context)
    val fallback = FallbackStarter(context)

    /** The copy in the user's folder that survives uninstalling (docs/LLD.md, Backup folder). */
    val backup by lazy { BackupFolder(context, this) }

    /** The daily numbers sent to the developer's Firestore (docs/SPEC.md, Cloud copy). */
    val cloud: CloudStore by lazy { FirestoreCloudStore(context) }
    val cloudSync by lazy { CloudSync(context, this, cloud) }
    val accountSignIn by lazy { AccountSignIn(context, this) }

    /** What the user set: enforcement and the two limits (docs/SPEC.md, chosen 2026-10-29: 90 and 30 min). */
    var settings: AppSettings = settingsStore.load()
        private set

    /** The limits as set; [activeBudgets] is what's in force. */
    val budgets: Budgets get() = settings.budgets()
    /** The service plugs the overlay and navigation in when it connects. */
    val effects = EffectDispatcher()
    val runner = GuardRunner(
        tracker = UsageTracker(dayClock, clock),
        engine = engine(shortTimers = false),
        store = usageStore,
        statsStore = statsStore,
        effects = effects,
        sinks = listOf(LogcatEventSink(), JsonlEventLog(File(context.filesDir, EVENT_LOG_FILE))),
        clock = clock,
    )

    init {
        runner.enforcementEnabled = settings.enforcementEnabled
    }

    /**
     * Saves [new] and puts it in force: the enforcement switch on the runner, new limits in a new
     * engine. Callers loosening a setting must have passed [settingsChanger]'s challenge first. Every
     * change is recorded: in the event log, and in the developer's copy at once (docs/SPEC.md).
     */
    fun applySettings(new: AppSettings, source: ChangeSource = ChangeSource.USER) {
        val old = settings
        if (new == old) return
        settings = new
        settingsStore.save(new)
        val at = clock.now()
        runner.record(EngineEvent.SettingsChanged(at, old, new, source))
        cloudSync.settingsChanged(old, new, source, at)
        if (new.enforcementEnabled != old.enforcementEnabled) runner.enforcementEnabled = new.enforcementEnabled
        if (new.budgets() != old.budgets()) runner.engine = engine(shortTimers)
    }

    /** The enforcement switch. Set only by the debug command; the app goes through [settingsChanger]. */
    var enforcementEnabled: Boolean
        get() = settings.enforcementEnabled
        set(value) = applySettings(settings.copy(enforcementEnabled = value), ChangeSource.DEBUG)

    /** What changing a setting costs; with short timers, a 5-second wait instead of 10 minutes. */
    val settingsChanger: SettingsChanger
        get() = SettingsChanger(
            challenges,
            ladderSettings,
            if (shortTimers) SettingsChanger.SHORT_WAIT_SECONDS else SettingsChanger.LOOSEN_WAIT_SECONDS,
        )

    /**
     * Debug builds only: short waits, sessions and cooldowns for trying the ladder quickly
     * (docs/LLD.md, milestone 3). Off by default and whenever the app restarts.
     */
    var shortTimers: Boolean = false
        set(value) {
            field = value
            runner.engine = engine(value)
        }

    /** The budgets in force: with short timers on, a 5-minute day. */
    val activeBudgets: Budgets get() = if (shortTimers) Budgets.shortForTesting(budgets) else budgets

    private fun engine(shortTimers: Boolean): GuardEngine {
        val launch = if (shortTimers) LaunchSettings.shortForTesting() else launchSettings
        val ladder = if (shortTimers) LadderSettings.shortForTesting() else ladderSettings
        val budgets = if (shortTimers) Budgets.shortForTesting(budgets) else budgets
        // The daily total first: when both are spent, all of Instagram is covered, not just Reels.
        val rules = listOf(DailyTotalBudgetRule(budgets), SurfaceBudgetRule(budgets))
        return GuardEngine(launch, ladder, CountLadderPolicy(ladder), challenges, budgets, rules, intervention)
    }

    /** The numbers the screens over Instagram show around the engine's decisions. */
    fun overlayStatus(): OverlayStatus {
        val ladder = if (shortTimers) LadderSettings.shortForTesting() else ladderSettings
        return OverlayStatus.from(
            runner.usage,
            activeBudgets,
            CountLadderPolicy(ladder),
            ladder.dailyCap,
            dayClock.nextReset(clock.now(), clock.zone),
        )
    }

    companion object {
        const val EVENT_LOG_FILE = "events.jsonl"

        /** The limits' day starts at 4 AM (docs/SPEC.md); not yet a setting. */
        const val RESET_HOUR = 4
    }
}

/** The phone's clock. The zone is read each time, so travelling moves the 4 AM reset with you. */
private class SystemTimeClock : Clock {
    override fun now(): Instant = Instant.now()
    override val zone: ZoneId get() = ZoneId.systemDefault()
}
