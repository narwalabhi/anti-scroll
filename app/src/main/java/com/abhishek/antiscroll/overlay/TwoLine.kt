package com.abhishek.antiscroll.overlay

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import androidx.core.content.ContextCompat
import com.abhishek.antiscroll.R

/** A button label with a smaller, dimmer second line, as the design's secondary buttons have. */
fun twoLine(context: Context, title: String, detail: String?): CharSequence {
    if (detail == null) return title
    val text = SpannableStringBuilder(title).append('\n')
    val start = text.length
    text.append(detail)
    val px = (12 * context.resources.displayMetrics.scaledDensity).toInt()
    text.setSpan(AbsoluteSizeSpan(px), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    text.setSpan(ForegroundColorSpan(ContextCompat.getColor(context, R.color.ink3)), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    return text
}
