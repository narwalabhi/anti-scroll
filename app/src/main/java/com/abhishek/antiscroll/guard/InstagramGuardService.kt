package com.abhishek.antiscroll.guard

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.view.accessibility.AccessibilityEvent
import com.abhishek.antiscroll.AntiScrollApp
import com.abhishek.antiscroll.AppGraph
import com.abhishek.antiscroll.EffectDispatcher
import com.abhishek.antiscroll.overlay.OverlayController
import com.abhishek.antiscroll.overlay.ScreenEffectHandler
import com.abhishek.antiscroll.overlay.SessionPill
import com.abhishek.antiscroll.rules.engine.GuardState
import com.abhishek.antiscroll.rules.engine.GuardEvent
import com.abhishek.antiscroll.rules.engine.GuardRunner
import com.abhishek.antiscroll.rules.events.EngineEvent
import com.abhishek.antiscroll.rules.profile.Surface
import com.abhishek.antiscroll.rules.time.Clock
import com.abhishek.antiscroll.ui.ExportReminderNotifier
import java.time.Instant

/**
 * The accessibility service, as composition only: when it connects it creates the pieces that do
 * the work, routes each event to them and wires what they report to the [GuardRunner]; when it stops
 * it tears them down. Content is read only for Instagram; for other apps, only the package name.
 *
 * - [ForegroundTracker]: Instagram opening and closing
 * - [SurfaceWatcher]: which surface is on screen, and Reels swipes
 * - [SessionTicker], [Heartbeat]: the tick while open, the 5-minute heartbeat
 * - [DebugCommands]: adb commands, debug builds only
 *
 * It also tells the health record it's running (gaps show on Today), and hands over to the
 * [FallbackWatcherService] when it's turned off.
 */
class InstagramGuardService : AccessibilityService() {

    private lateinit var runner: GuardRunner
    private lateinit var clock: Clock
    private lateinit var effects: EffectDispatcher
    private lateinit var overlay: OverlayController
    private lateinit var foreground: ForegroundTracker
    private lateinit var surfaces: SurfaceWatcher
    private lateinit var ticker: SessionTicker
    private lateinit var heartbeat: Heartbeat
    private lateinit var pill: SessionPill
    private lateinit var graph: AppGraph
    private var debugCommands: DebugCommands? = null
    private lateinit var watchedPackage: String

    private val isDebuggable by lazy { applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 }

    override fun onServiceConnected() {
        graph = (application as AntiScrollApp).graph
        runner = graph.runner
        clock = graph.clock
        watchedPackage = graph.instagramProfile.packageName

        effects = graph.effects
        overlay = OverlayController(this)
        effects.screens = ScreenEffectHandler(this, overlay, clock, graph::overlayStatus) { runner.handle(it) }
        effects.navigation = NavigationEffectHandler(this)

        pill = SessionPill(this)
        ticker = SessionTicker(runner, clock) { showPill() }
        val reminders = ExportReminderNotifier(this, graph)
        heartbeat = Heartbeat(runner, graph.health, clock) {
            reminders.checkOncePerDay()
            graph.backup.writeIfDue()
            graph.cloudSync.syncIfDue()
        }
        surfaces = SurfaceWatcher(
            this,
            graph.instagramProfile,
            graph.instagramRules,
            dumpUnknownScreens = isDebuggable,
            listener = object : SurfaceWatcher.Listener {
                override fun onSurfaceChanged(surface: Surface) = runner.handle(GuardEvent.SurfaceChanged(surface))
                override fun onSwiped(surface: Surface) = runner.handle(GuardEvent.Swiped(surface))
            },
        )
        foreground = ForegroundTracker(
            this,
            watchedPackage,
            clock,
            isOpen = { runner.isOpen },
            listener = object : ForegroundTracker.Listener {
                override fun onOpened(at: Instant) {
                    runner.handle(GuardEvent.InstagramOpened(at))
                    ticker.start()
                    surfaces.checkSoon()
                }

                override fun onClosed() {
                    runner.handle(GuardEvent.InstagramClosed)
                    ticker.stop()
                    pill.hide()
                    saveCopies()
                    surfaces.reset()
                }

                override fun onScreenOff() {
                    runner.flush()
                    saveCopies()
                }
            },
        )

        val now = clock.now()
        runner.record(EngineEvent.ServiceStarted(now))
        val updatedAt = Instant.ofEpochMilli(packageManager.getPackageInfo(packageName, 0).lastUpdateTime)
        graph.health.started(now, updatedAt)
        graph.fallback.stop()
        heartbeat.start()
        if (isDebuggable) debugCommands = DebugCommands(this, graph).also { it.start() }
        foreground.start()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        if (packageName == watchedPackage) {
            if (foreground.onWatchedAppEvent(event)) surfaces.onEvent(event)
        } else {
            foreground.onOtherAppEvent(packageName, event)
        }
    }

    override fun onInterrupt() {}

    /**
     * Instagram closing and the screen turning off are when the numbers have just changed; the
     * heartbeat alone pauses while the phone sleeps, which left the copies hours behind.
     */
    private fun saveCopies() {
        graph.backup.writeIfDue()
        graph.cloudSync.syncOnLeave()
    }

    /** The timer pill shows while a session runs with Instagram in front (docs/UI.md, Session pill). */
    private fun showPill() {
        val session = (runner.guardState as? GuardState.Running)?.session
        pill.update(session?.allowedUntil, clock.now())
    }

    /** Turned off: the fallback watcher takes over. Still bound here, so Android lets it start. */
    override fun onUnbind(intent: Intent?): Boolean {
        if (::graph.isInitialized) graph.fallback.start()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (::foreground.isInitialized) {
            foreground.stop()
            surfaces.stop()
            heartbeat.stop()
            debugCommands?.stop()
            val now = clock.now()
            runner.record(EngineEvent.ServiceStopped(now))
            graph.health.stopped(now)
            runner.flush()
            effects.screens = null
            effects.navigation = null
            overlay.remove()
            pill.hide()
        }
        super.onDestroy()
    }
}
