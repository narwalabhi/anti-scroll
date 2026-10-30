package com.abhishek.antiscroll.overlay

import android.os.Handler
import android.os.Looper

/**
 * The one shared once-a-second countdown for whatever screen is up (docs/UI.md, Performance):
 * starting a new one stops the last, and nothing ticks while no screen counts down.
 */
class Ticker {

    private val handler = Handler(Looper.getMainLooper())
    private var current: Runnable? = null

    /**
     * Calls [step] now (posted, never inline: the engine must finish its step first) and then
     * every second while it returns true.
     */
    fun start(step: () -> Boolean) {
        stop()
        val runnable = object : Runnable {
            override fun run() {
                if (current !== this) return
                if (step()) handler.postDelayed(this, 1_000L) else current = null
            }
        }
        current = runnable
        handler.post(runnable)
    }

    fun stop() {
        current?.let(handler::removeCallbacks)
        current = null
    }
}
