package com.abhishek.antiscroll.guard

import android.os.Handler
import android.os.Looper
import com.abhishek.antiscroll.rules.engine.GuardRunner
import com.abhishek.antiscroll.rules.events.EngineEvent
import com.abhishek.antiscroll.rules.health.HealthRecorder
import com.abhishek.antiscroll.rules.time.Clock

/**
 * Shows the service is alive: an event every 5 minutes. Timers pause while the phone sleeps, so
 * gaps with the screen off are normal; a ServiceStarted without a ServiceStopped before it means the
 * service was killed. Each beat is also the service's last sign of life for [health], so a death
 * without goodbye shows as a gap from then.
 */
class Heartbeat(
    private val runner: GuardRunner,
    private val health: HealthRecorder,
    private val clock: Clock,
    private val onBeat: () -> Unit = {},
) {

    private val handler = Handler(Looper.getMainLooper())
    private val beat = object : Runnable {
        override fun run() {
            val now = clock.now()
            runner.record(EngineEvent.Heartbeat(now))
            health.alive(now)
            onBeat()
            handler.postDelayed(this, INTERVAL_MS)
        }
    }

    fun start() = handler.postDelayed(beat, INTERVAL_MS)

    fun stop() = handler.removeCallbacks(beat)

    private companion object {
        const val INTERVAL_MS = 5 * 60 * 1_000L
    }
}
