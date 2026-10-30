package com.abhishek.antiscroll.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.abhishek.antiscroll.R
import com.abhishek.antiscroll.present.Durations

/**
 * A linear gauge (docs/UI.md, Hero card). The scale runs to limit / 0.75, so the limit tick always
 * sits at 75%; ink up to the limit, coral past it. [unknownTail] hatches the rest of the track (time
 * that wasn't counted). Never a ring.
 */
class GaugeView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var usedMillis = 0L
        set(value) { field = value; invalidate() }
    var limitMillis = 1L
        set(value) { field = value.coerceAtLeast(1L); requestLayout(); invalidate() }
    var unknownTail = false
        set(value) { field = value; invalidate() }

    /** The big gauge has labels under it and a 12dp bar; the small one an 8dp bar only. */
    var large = true
        set(value) { field = value; requestLayout(); invalidate() }

    private val track = fillPaint(R.color.surface2)
    private val ink = fillPaint(R.color.ink)
    private val coral = fillPaint(R.color.coral)
    private val tick = fillPaint(R.color.ink2)
    private val hatch = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.token(R.color.hatch)
        strokeWidth = dp(4f)
    }
    private val label = textPaint(R.color.ink3, 12f, R.font.inter_regular)
    private val limitLabel = textPaint(R.color.ink2, 12f, R.font.inter_regular)
    private val rect = RectF()
    private val clip = Path()

    private val barHeight get() = dp(if (large) 12f else 8f)
    private val tickOverhang get() = dp(if (large) 4f else 3f)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val labels = if (large) dp(8f) + label.textSize + dp(4f) else 0f
        val height = (tickOverhang * 2 + barHeight + labels).toInt()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val top = tickOverhang
        val bottom = top + barHeight
        val radius = barHeight / 2
        val scale = limitMillis / 0.75
        fun x(millis: Long) = (w * (millis / scale)).toFloat().coerceIn(0f, w)

        rect.set(0f, top, w, bottom)
        clip.reset()
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        canvas.drawRect(rect, track)
        val usedX = x(usedMillis)
        val limitX = x(limitMillis)
        canvas.drawRect(0f, top, minOf(usedX, limitX), bottom, ink)
        if (usedX > limitX) canvas.drawRect(limitX, top, usedX, bottom, coral)
        if (unknownTail) {
            // Hatched from where counting stopped to the end: time that may have gone uncounted.
            canvas.clipRect(usedX, top, w, bottom)
            var stripe = usedX - barHeight
            while (stripe < w) {
                canvas.drawLine(stripe, bottom, stripe + barHeight, top, hatch)
                stripe += dp(8f)
            }
        }
        canvas.restore()
        canvas.drawRect(limitX - dp(1f), 0f, limitX + dp(1f), bottom + tickOverhang, tick)

        if (large) {
            val baseline = bottom + tickOverhang + dp(8f) + label.textSize - dp(2f)
            label.textAlign = Paint.Align.LEFT
            canvas.drawText("0", 0f, baseline, label)
            label.textAlign = Paint.Align.RIGHT
            canvas.drawText(Durations.hm(scale.toLong()), w, baseline, label)
            limitLabel.textAlign = Paint.Align.CENTER
            canvas.drawText(context.getString(R.string.gauge_limit, Durations.hm(limitMillis)), limitX, baseline, limitLabel)
        }
    }
}
