package com.abhishek.antiscroll.overlay

import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import com.abhishek.antiscroll.R
import com.abhishek.antiscroll.present.Durations
import com.abhishek.antiscroll.ui.widget.RingView
import com.google.android.material.button.MaterialButton

/**
 * Binds the wait layout (overlay_wait): the ring counts down, then Continue enables. Shared by the
 * override over Instagram and the settings challenge, so both look the same. Only draws and
 * reports; the run decides whether the wait is done.
 */
class WaitPanel(val view: View, private val ticker: Ticker) {

    private val context = view.context
    private val ring = view.findViewById<RingView>(R.id.waitRing)
    private val time = view.findViewById<TextView>(R.id.waitTime)
    private val of = view.findViewById<TextView>(R.id.waitOf)
    private val timer = view.findViewById<View>(R.id.waitTimer)
    private val chip = view.findViewById<TextView>(R.id.overrideChip)
    private val heading = view.findViewById<TextView>(R.id.waitHeading)
    private val sub = view.findViewById<TextView>(R.id.waitSub)
    private val cancel = view.findViewById<MaterialButton>(R.id.waitCancel)
    private val next = view.findViewById<MaterialButton>(R.id.waitContinue)

    data class Copy(val chip: String?, val heading: String, val sub: String, val cancel: String, val detail: String?)

    fun bind(seconds: Int, copy: Copy, onCancel: () -> Unit, onDone: () -> Unit) {
        chip.isVisible = copy.chip != null
        chip.text = copy.chip
        heading.text = copy.heading
        sub.text = copy.sub
        cancel.text = copy.cancel
        cancel.setOnClickListener { onCancel() }
        next.setOnClickListener { onDone() }
        of.text = context.getString(R.string.wait_of, Durations.clock(seconds * 1_000L))
        var left = seconds
        ticker.start {
            ring.progress = if (seconds == 0) 0f else left.toFloat() / seconds
            time.text = Durations.clock(left * 1_000L)
            val done = left <= 0
            next.isEnabled = done
            val title = if (done) context.getString(R.string.wait_continue) else
                context.getString(R.string.wait_continue_in, Durations.clock(left * 1_000L))
            next.text = twoLine(context, title, copy.detail)
            // Announced at milestones, not every second (docs/UI.md, Accessibility).
            if (left == seconds || left % 15 == 0) {
                timer.contentDescription = context.getString(R.string.wait_spoken, Durations.spoken(left * 1_000L))
            }
            left--
            !done
        }
    }
}
