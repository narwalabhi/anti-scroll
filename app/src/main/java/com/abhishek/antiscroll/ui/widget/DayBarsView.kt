package com.abhishek.antiscroll.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.abhishek.antiscroll.R
import com.abhishek.antiscroll.present.Durations

/**
 * Minutes per day with the limit as a dashed line (docs/UI.md, week card and History). Time over
 * the limit is coral; today is ink, past days dimmer; future days are dashed ghost stubs and days
 * before counting began a thin line. Day labels under the bars.
 */
class DayBarsView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    data class Bar(val label: String, val millis: Long, val isToday: Boolean, val isFuture: Boolean = false, val counted: Boolean = true)

    var bars: List<Bar> = emptyList()
        set(value) { field = value; invalidate() }
    var limitMillis = 1L
        set(value) { field = value.coerceAtLeast(1L); invalidate() }

    /** A value above every bar (History, as minutes) or only above today's (Today). */
    var valuesOnAll = false
        set(value) { field = value; invalidate() }

    /** Past bars in ink too (History has no "today" emphasis beyond the label). */
    var allInk = false
        set(value) { field = value; invalidate() }

    private val today = fillPaint(R.color.ink)
    private val past = fillPaint(R.color.bar_past)
    private val coral = fillPaint(R.color.coral)
    private val faint = fillPaint(R.color.ghost)
    private val ghost = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.token(R.color.ghost)
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        pathEffect = DashPathEffect(floatArrayOf(dp(4f), dp(3f)), 0f)
    }
    private val dashed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.token(R.color.dashed)
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        pathEffect = DashPathEffect(floatArrayOf(dp(5f), dp(4f)), 0f)
    }
    private val value = textPaint(R.color.ink, 12f, R.font.inter_semibold).apply { textAlign = Paint.Align.CENTER }
    private val label = textPaint(R.color.ink3, 12f, R.font.inter_regular).apply { textAlign = Paint.Align.CENTER }
    private val labelToday = textPaint(R.color.ink, 12f, R.font.inter_semibold).apply { textAlign = Paint.Align.CENTER }
    private val rect = RectF()
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        if (bars.isEmpty()) return
        val gap = dp(8f)
        val slot = (width - gap * (bars.size - 1)) / bars.size
        val labelBand = label.textSize + dp(10f)
        val valueBand = value.textSize + dp(6f)
        val bottom = height - labelBand
        val chartHeight = bottom - valueBand
        val max = maxOf(bars.maxOf { it.millis }, (limitMillis * 1.2).toLong()).toFloat()
        fun y(millis: Long) = bottom - chartHeight * (millis / max)
        val radius = dp(6f)

        val limitY = y(limitMillis)
        path.reset()
        path.moveTo(0f, limitY)
        path.lineTo(width.toFloat(), limitY)
        canvas.drawPath(path, dashed)

        bars.forEachIndexed { i, bar ->
            val left = i * (slot + gap)
            val right = left + slot
            val center = left + slot / 2
            when {
                bar.isFuture -> {
                    rect.set(left + dp(1f), bottom - dp(24f), right - dp(1f), bottom - dp(1f))
                    canvas.drawRoundRect(rect, radius, radius, ghost)
                }
                !bar.counted || bar.millis == 0L -> canvas.drawRect(left, bottom - dp(2f), right, bottom, faint)
                else -> {
                    val top = minOf(y(bar.millis), bottom - dp(4f))
                    val base = if (bar.isToday || allInk) today else past
                    rect.set(left, top, right, bottom + radius)
                    canvas.save()
                    canvas.clipRect(left, top, right, bottom)
                    canvas.drawRoundRect(rect, radius, radius, base)
                    if (bar.millis > limitMillis) {
                        canvas.clipRect(left, top, right, limitY)
                        canvas.drawRoundRect(rect, radius, radius, coral)
                    }
                    canvas.restore()
                    if (valuesOnAll || bar.isToday) {
                        val text = if (valuesOnAll) (bar.millis / 60_000).toString() else Durations.hm(bar.millis)
                        canvas.drawText(text, center, top - dp(6f), value)
                    }
                }
            }
            canvas.drawText(bar.label, center, height - dp(4f), if (bar.isToday) labelToday else label)
        }
    }
}
