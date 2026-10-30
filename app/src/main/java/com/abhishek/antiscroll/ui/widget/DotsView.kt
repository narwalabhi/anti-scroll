package com.abhishek.antiscroll.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.abhishek.antiscroll.R

/** Override dots (docs/UI.md, Cooldown or cap): filled for each used, outlined for each left. */
class DotsView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var count = 5
        set(value) { field = value; requestLayout(); invalidate() }
    var filled = 0
        set(value) { field = value; invalidate() }

    private val used = fillPaint(R.color.ink)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        color = context.token(R.color.dashed)
    }
    private val size get() = dp(14f)
    private val gap get() = dp(8f)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
        setMeasuredDimension((count * size + (count - 1) * gap).toInt(), size.toInt())

    override fun onDraw(canvas: Canvas) {
        val r = size / 2
        repeat(count) { i ->
            val cx = i * (size + gap) + r
            if (i < filled) canvas.drawCircle(cx, r, r, used) else canvas.drawCircle(cx, r, r - ring.strokeWidth / 2, ring)
        }
    }
}
