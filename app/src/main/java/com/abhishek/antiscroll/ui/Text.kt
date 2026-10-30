package com.abhishek.antiscroll.ui

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.format.DateFormat
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.graphics.Typeface
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.abhishek.antiscroll.R
import com.abhishek.antiscroll.present.Durations
import com.abhishek.antiscroll.present.Section
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import java.util.Locale

/** Formatting the screens share: big numbers with small units, dates, times, section names and colours. */
object Text {

    /**
     * "1h 47m" with the units smaller and dimmer, as the hero's big number ([unitSp] for the units).
     * The digits keep the view's own size.
     */
    fun bigDuration(context: Context, millis: Long, unitSp: Int = 28): CharSequence {
        val plain = Durations.hm(millis)
        val text = SpannableStringBuilder(plain)
        val unitColor = ContextCompat.getColor(context, R.color.ink2)
        val px = (unitSp * context.resources.displayMetrics.scaledDensity).toInt()
        val font = ResourcesCompat.getFont(context, R.font.sora_semibold)
        plain.forEachIndexed { i, c ->
            if (!c.isDigit()) {
                text.setSpan(AbsoluteSizeSpan(px), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                text.setSpan(ForegroundColorSpan(unitColor), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (font != null) text.setSpan(FontSpan(font), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return text
    }

    /** A number with a smaller unit after it: "1.4 min", "2 of 5". */
    fun withUnit(context: Context, number: String, unit: String, unitSp: Int = 14): CharSequence {
        val text = SpannableStringBuilder(number).append(' ')
        val start = text.length
        text.append(unit)
        val px = (unitSp * context.resources.displayMetrics.scaledDensity).toInt()
        text.setSpan(AbsoluteSizeSpan(px), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.setSpan(ForegroundColorSpan(ContextCompat.getColor(context, R.color.ink2)), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return text
    }

    /** "Thu, Oct 29" in the phone's language. */
    fun dayAndDate(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEMMMd")))

    fun weekday(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))

    fun time(context: Context, at: Instant): String = DateFormat.getTimeFormat(context).format(Date.from(at))

    /** "4 AM", "12 PM" (or "04", "12" on a 24-hour phone), for chart axes. */
    fun hour(context: Context, hour: Int): String {
        val pattern = if (DateFormat.is24HourFormat(context)) "HH" else "h a"
        return java.time.LocalTime.of(hour, 0).format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }

    fun mediumDate(date: LocalDate): String = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

    fun localDate(at: Instant): LocalDate = at.atZone(ZoneId.systemDefault()).toLocalDate()

    @ColorRes
    fun color(section: Section): Int = when (section) {
        Section.FEED -> R.color.section_feed
        Section.REELS -> R.color.section_reels
        Section.MESSAGES -> R.color.section_dms
        Section.STORIES -> R.color.section_stories
        Section.EXPLORE -> R.color.section_explore
        Section.OTHER -> R.color.section_other
    }

    fun label(section: Section): Int = when (section) {
        Section.FEED -> R.string.surface_feed
        Section.REELS -> R.string.surface_reels
        Section.MESSAGES -> R.string.surface_dms
        Section.STORIES -> R.string.surface_stories
        Section.EXPLORE -> R.string.surface_explore
        Section.OTHER -> R.string.surface_other
    }
}

/** A font for part of a text; the platform's TypefaceSpan(Typeface) needs API 28. */
private class FontSpan(private val font: Typeface) : MetricAffectingSpan() {
    override fun updateDrawState(tp: TextPaint) { tp.typeface = font }
    override fun updateMeasureState(tp: TextPaint) { tp.typeface = font }
}
