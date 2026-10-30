package com.abhishek.antiscroll.ui.widget

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import androidx.annotation.ColorRes
import androidx.annotation.FontRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat

/** Small helpers the custom views share: units, token colours and text paints. */
internal fun View.dp(value: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

internal fun View.sp(value: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

internal fun Context.token(@ColorRes id: Int): Int = ContextCompat.getColor(this, id)

internal fun View.fillPaint(@ColorRes color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = context.token(color)
    style = Paint.Style.FILL
}

internal fun View.textPaint(@ColorRes color: Int, sizeSp: Float, @FontRes font: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = context.token(color)
    textSize = sp(sizeSp)
    typeface = ResourcesCompat.getFont(context, font) ?: Typeface.DEFAULT
}
