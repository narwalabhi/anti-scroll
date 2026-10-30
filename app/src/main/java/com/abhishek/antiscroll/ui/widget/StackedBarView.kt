package com.abhishek.antiscroll.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/** One bar split into coloured parts (docs/UI.md, Breakdown): 2dp gaps, no part under 4dp. */
class StackedBarView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    data class Part(val color: Int, val value: Long)

    var parts: List<Part> = emptyList()
        set(value) { field = value; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clip = Path()
    private val rect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(14f).toInt())

    override fun onDraw(canvas: Canvas) {
        val shown = parts.filter { it.value > 0 }
        if (shown.isEmpty()) return
        val gap = dp(2f)
        val minWidth = dp(4f)
        val available = width - gap * (shown.size - 1)
        val total = shown.sumOf { it.value }.toFloat()
        // Small parts get their minimum; the rest share what's left in proportion.
        val raw = shown.map { available * it.value / total }
        val small = raw.count { it < minWidth }
        val bigTotal = raw.filter { it >= minWidth }.sum()
        val scale = if (bigTotal > 0f) (available - small * minWidth) / bigTotal else 0f
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        clip.reset()
        clip.addRoundRect(rect, height / 2f, height / 2f, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        var x = 0f
        shown.forEachIndexed { i, part ->
            val w = if (raw[i] < minWidth) minWidth else raw[i] * scale
            paint.color = part.color
            canvas.drawRect(x, 0f, x + w, height.toFloat(), paint)
            x += w + gap
        }
        canvas.restore()
    }
}
