package com.abhishek.antiscroll.guard

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.abhishek.antiscroll.rules.engine.GuardEvent
import com.abhishek.antiscroll.rules.engine.GuardRunner
import com.abhishek.antiscroll.rules.time.Clock

/**
 * The one-second tick while the app is open, with a usage summary in Logcat every 10 ticks and on
 * stop. [onTick] runs after each tick is handled (the session pill follows it).
 */
class SessionTicker(private val runner: GuardRunner, private val clock: Clock, private val onTick: () -> Unit = {}) {

    private val handler = Handler(Looper.getMainLooper())
    private var ticksSinceSummary = 0
    private val tick = object : Runnable {
        override fun run() {
            runner.handle(GuardEvent.Tick(clock.now()))
            onTick()
            if (++ticksSinceSummary >= SUMMARY_EVERY_TICKS) {
                ticksSinceSummary = 0
                logSummary()
            }
            handler.postDelayed(this, TICK_MS)
        }
    }

    fun start() {
        ticksSinceSummary = 0
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, TICK_MS)
    }

    fun stop() {
        handler.removeCallbacks(tick)
        logSummary()
    }

    private fun logSummary() {
        val usage = runner.usage
        val perSurface = usage.millisBySurface.entries
            .sortedBy { it.key }
            .joinToString(" ") { "${it.key}=${it.value / 1000}s" }
        Log.i(TAG, "usage day=${usage.day} opens=${usage.opens} total=${usage.totalMillis / 1000}s $perSurface")
    }

    private companion object {
        const val TAG = "AntiScroll"
        const val TICK_MS = 1_000L
        const val SUMMARY_EVERY_TICKS = 10
    }
}
