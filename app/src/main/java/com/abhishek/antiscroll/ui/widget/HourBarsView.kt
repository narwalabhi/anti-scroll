package com.abhishek.antiscroll.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.abhishek.antiscroll.R

/** 24 hourly bars (docs/UI.md, When you scroll): late hours in ink, the rest dimmed; four hour labels under them. */
class HourBarsView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    data class Bar(val millis: Long, val late: Boolean)

    var bars: List<Bar> = emptyList()
        set(value) { field = value; invalidate() }

    /** Labels at the first, 9th, 17th and last bar. */
    var labels: List<String> = emptyList()
        set(value) { field = value; invalidate() }

    private val late = fillPaint(R.color.ink)
    private val dim = fillPaint(R.color.dashed)
    private val label = textPaint(R.color.ink3, 12f, R.font.inter_regular)
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        if (bars.isEmpty()) return
        val gap = dp(3f)
        val slot = (width - gap * (bars.size - 1)) / bars.size
        val bottom = height - label.textSize - dp(10f)
        val max = bars.maxOf { it.millis }.coerceAtLeast(1L).toFloat()
        val radius = dp(2f)
        bars.forEachIndexed { i, bar ->
            val left = i * (slot + gap)
            val h = maxOf(dp(4f), bottom * (bar.millis / max))
            rect.set(left, bottom - h, left + slot, bottom)
            canvas.drawRoundRect(rect, radius, radius, if (bar.late) late else dim)
        }
        val positions = listOf(0, 8, 16, bars.size - 1)
        labels.zip(positions).forEachIndexed { n, (text, index) ->
            val x = index * (slot + gap) + slot / 2
            label.textAlign = when (n) {
                0 -> Paint.Align.LEFT
                labels.size - 1 -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
            val drawX = when (label.textAlign) {
                Paint.Align.LEFT -> 0f
                Paint.Align.RIGHT -> width.toFloat()
                else -> x
            }
            canvas.drawText(text, drawX, height - dp(4f), label)
        }
    }
}
