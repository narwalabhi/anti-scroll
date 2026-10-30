package com.abhishek.antiscroll.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.abhishek.antiscroll.R

/** [count] segments in a row, the first [filled] in ink: the baseline week's days. */
class SegmentsView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var count = 7
        set(value) { field = value; invalidate() }
    var filled = 0
        set(value) { field = value; invalidate() }

    private val on = fillPaint(R.color.ink)
    private val off = fillPaint(R.color.surface2)
    private val rect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(6f).toInt())

    override fun onDraw(canvas: Canvas) {
        val gap = dp(4f)
        val w = (width - gap * (count - 1)) / count
        val r = height / 2f
        repeat(count) { i ->
            rect.set(i * (w + gap), 0f, i * (w + gap) + w, height.toFloat())
            canvas.drawRoundRect(rect, r, r, if (i < filled) on else off)
        }
    }
}
