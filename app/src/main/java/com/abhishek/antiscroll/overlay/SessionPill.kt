package com.abhishek.antiscroll.overlay

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.abhishek.antiscroll.R
import com.abhishek.antiscroll.present.Durations
import java.time.Duration
import java.time.Instant

/**
 * The session timer over Instagram (docs/UI.md, Session pill): a small untouchable pill at the top
 * centre showing m:ss, coral in the last minute, with a short vibration at 60 and 10 seconds left.
 * [context] is the accessibility service, whose overlay window needs no permission.
 */
class SessionPill(context: Context) {

    private val overlay = OverlayController(context, pill = true)
    private val themed = context.overlayContext()
    private val vibrator = context.getSystemService(Vibrator::class.java)
    private val view: View = LayoutInflater.from(themed).inflate(R.layout.overlay_session_pill, null)
    private val pill = view.findViewById<View>(R.id.pill)
    private val icon = view.findViewById<ImageView>(R.id.pillIcon)
    private val time = view.findViewById<TextView>(R.id.pillTime)
    private var lastSecondsLeft: Long? = null

    /** Shows [until] counting down from [now]; null hides it. */
    fun update(until: Instant?, now: Instant) {
        if (until == null) return hide()
        val left = Duration.between(now, until).toMillis()
        if (left <= 0) return hide()
        val seconds = (left + 999) / 1000
        lastSecondsLeft?.let { before -> if (crossed(before, seconds, 60) || crossed(before, seconds, 10)) buzz() }
        lastSecondsLeft = seconds
        val lastMinute = seconds <= 60
        pill.setBackgroundResource(if (lastMinute) R.drawable.bg_session_pill_last else R.drawable.bg_session_pill)
        val color = ContextCompat.getColor(themed, if (lastMinute) R.color.on_coral else R.color.ink)
        time.setTextColor(color)
        icon.setColorFilter(color)
        time.text = Durations.clock(left)
        // Announced at milestones only, not every second (docs/UI.md, Accessibility).
        if (seconds == 60L || seconds == 10L || pill.contentDescription == null) {
            pill.contentDescription = themed.getString(R.string.pill_spoken, Durations.spoken(left))
        }
        overlay.show(view)
    }

    fun hide() {
        lastSecondsLeft = null
        pill.contentDescription = null
        overlay.remove()
    }

    private fun crossed(before: Long, now: Long, mark: Long) = before > mark && now <= mark

    private fun buzz() {
        vibrator?.vibrate(VibrationEffect.createOneShot(BUZZ_MS, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private companion object {
        const val BUZZ_MS = 60L
    }
}
