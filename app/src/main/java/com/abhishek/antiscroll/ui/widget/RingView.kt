package com.abhishek.antiscroll.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.abhishek.antiscroll.R

/** A countdown ring (docs/UI.md, overlays): one arc, drawn in onDraw, [progress] of it left. */
class RingView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var progress = 1f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(10f)
        color = context.token(R.color.surface2)
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(10f)
        strokeCap = Paint.Cap.ROUND
        color = context.token(R.color.ink)
    }
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val inset = track.strokeWidth / 2
        rect.set(inset, inset, width - inset, height - inset)
        canvas.drawOval(rect, track)
        if (progress > 0f) canvas.drawArc(rect, -90f, 360f * progress, false, arc)
    }
}
