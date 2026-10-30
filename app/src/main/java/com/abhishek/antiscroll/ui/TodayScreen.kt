package com.abhishek.antiscroll.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.abhishek.antiscroll.R
import com.abhishek.antiscroll.labelRes
import com.abhishek.antiscroll.present.Durations
import com.abhishek.antiscroll.present.TodayUiState
import com.abhishek.antiscroll.rules.health.GapCause
import com.abhishek.antiscroll.ui.widget.DayBarsView
import com.abhishek.antiscroll.ui.widget.GaugeView
import com.abhishek.antiscroll.ui.widget.SegmentsView
import com.abhishek.antiscroll.ui.widget.StackedBarView
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.time.Duration
import java.time.Instant

/**
 * Draws a [TodayUiState] into screen_today (docs/UI.md, Today). Only draws; the [Actions] say what
 * the buttons do.
 */
class TodayScreen(private val root: View, private val actions: Actions) {

    interface Actions {
        fun openLimits()
        fun openSettings()
        fun openHistory()
        fun turnOnAccessibility()
        fun openBattery()
        fun endSession()
    }

    private val context = root.context
    private val inflater = LayoutInflater.from(context)
    private val header = root.findViewById<View>(R.id.todayHeader)
    private val chip = header.findViewById<TextView>(R.id.headerChip)

    init {
        header.findViewById<TextView>(R.id.headerTitle).setText(R.string.tab_today)
        root.findViewById<View>(R.id.alertTurnOn).setOnClickListener { actions.turnOnAccessibility() }
        root.findViewById<View>(R.id.alertBattery).setOnClickListener { actions.openBattery() }
        root.findViewById<View>(R.id.sessionEnd).setOnClickListener { actions.endSession() }
        root.findViewById<View>(R.id.historyLink).setOnClickListener { actions.openHistory() }
    }

    fun render(state: TodayUiState, now: Instant) {
        header.findViewById<TextView>(R.id.headerSubtitle).text = Text.dayAndDate(state.date)
        renderChip(state.mode)
        renderAlert(state)
        renderBaseline(state.baseline)
        renderSession(state.session)
        renderHero(state)
        renderBreakdown(state)
        renderStats(state)
        renderWeek(state.week)
        renderGaps(state.gaps, now)
    }

    private fun renderChip(mode: TodayUiState.Mode) {
        chip.isVisible = true
        val (text, background, icon, color) = when (mode) {
            TodayUiState.Mode.COUNTING -> Quad(R.string.mode_counting, R.drawable.bg_chip_outline, 0, R.color.ink)
            TodayUiState.Mode.GUARDING -> Quad(R.string.mode_guarding, R.drawable.bg_chip_tint, R.drawable.ic_shield, R.color.ink)
            TodayUiState.Mode.NOT_WATCHING -> Quad(R.string.mode_not_watching, R.drawable.bg_chip_coral, R.drawable.ic_warning, R.color.coral)
        }
        chip.setText(text)
        chip.setBackgroundResource(background)
        chip.setTextColor(ContextCompat.getColor(context, color))
        chip.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
        chip.compoundDrawablesRelative[0]?.let { drawable ->
            drawable.setBounds(0, 0, dp(16), dp(16))
            drawable.setTint(ContextCompat.getColor(context, color))
            chip.setCompoundDrawablesRelative(drawable, null, null, null)
        }
        chip.setOnClickListener { if (mode == TodayUiState.Mode.NOT_WATCHING) actions.openSettings() else actions.openLimits() }
    }

    private fun renderAlert(state: TodayUiState) {
        val card = root.findViewById<View>(R.id.alertCard)
        card.isVisible = state.alert != null
        val alert = state.alert ?: return
        root.findViewById<TextView>(R.id.alertMessage).text = alert.offSince
            ?.let { context.getString(R.string.alert_message_since, Text.time(context, it)) }
            ?: context.getString(R.string.alert_message)
    }

    private fun renderBaseline(baseline: TodayUiState.Baseline?) {
        root.findViewById<View>(R.id.baselineCard).isVisible = baseline != null
        if (baseline == null) return
        root.findViewById<TextView>(R.id.baselineDay).text = context.getString(R.string.baseline_day_of, baseline.day, baseline.of)
        root.findViewById<SegmentsView>(R.id.baselineSegments).apply {
            count = baseline.of
            filled = baseline.day
            contentDescription = context.getString(R.string.baseline_day_of, baseline.day, baseline.of)
        }
    }

    private fun renderSession(session: TodayUiState.SessionCard?) {
        root.findViewById<View>(R.id.sessionCard).isVisible = session != null
        if (session == null) return
        val left = Durations.clock(session.leftMillis)
        root.findViewById<TextView>(R.id.sessionLeft).text = if (session.overrideNumber != null) {
            context.getString(R.string.session_left_override, left, session.overrideNumber, session.cap)
        } else {
            context.getString(R.string.session_left, left)
        }
        root.findViewById<LinearProgressIndicator>(R.id.sessionBar).apply {
            val total = session.totalMillis
            isVisible = total != null
            if (total != null) setProgressCompat((session.leftMillis * 1000 / total.coerceAtLeast(1L)).toInt(), false)
            contentDescription = context.getString(R.string.session_spoken, Durations.spoken(session.leftMillis))
        }
    }

    private fun renderHero(state: TodayUiState) {
        val hero = state.hero
        root.findViewById<TextView>(R.id.heroTotal).text = Text.bigDuration(context, hero.totalMillis)
        val limit = Durations.hm(hero.limitMillis)
        root.findViewById<TextView>(R.id.heroSub).text = when {
            hero.unknownTail -> context.getString(R.string.hero_sub_unknown, limit)
            hero.windowMillis != null -> context.getString(R.string.hero_sub_window, Durations.hm(hero.windowMillis), limit)
            else -> context.getString(R.string.hero_sub, limit)
        }
        val pill = root.findViewById<TextView>(R.id.heroPill)
        when {
            hero.pausedAt != null -> neutralPill(pill, context.getString(R.string.paused_at, Text.time(context, hero.pausedAt)))
            hero.overMillis > 0 -> coralPill(pill, context.getString(R.string.min_over, Durations.minutesText(hero.overMillis)))
            else -> neutralPill(pill, context.getString(R.string.min_left, Durations.minutesText(hero.leftMillis)))
        }
        root.findViewById<GaugeView>(R.id.heroGauge).apply {
            usedMillis = hero.usedMillis
            limitMillis = hero.limitMillis
            unknownTail = hero.unknownTail
            contentDescription = spokenGauge(hero.usedMillis, hero.limitMillis)
        }
        val rows = root.findViewById<LinearLayout>(R.id.surfaceLimitRows)
        rows.removeAllViews()
        state.surfaceLimits.forEach { limitRow ->
            val row = inflater.inflate(R.layout.item_surface_limit, rows, false)
            val name = context.getString(limitRow.surface.labelRes())
            row.findViewById<TextView>(R.id.surfaceName).text = name
            row.findViewById<TextView>(R.id.surfaceValue).text =
                context.getString(R.string.of_limit, Durations.hm(limitRow.usedMillis), Durations.hm(limitRow.limitMillis))
            row.findViewById<GaugeView>(R.id.surfaceGauge).apply {
                large = false
                usedMillis = limitRow.usedMillis
                limitMillis = limitRow.limitMillis
                contentDescription = "$name: " + spokenGauge(limitRow.usedMillis, limitRow.limitMillis)
            }
            row.findViewById<TextView>(R.id.surfaceStatus).apply {
                if (limitRow.overMillis > 0) {
                    text = context.getString(R.string.min_over, Durations.minutesText(limitRow.overMillis))
                    setTextColor(ContextCompat.getColor(context, R.color.coral))
                } else {
                    text = context.getString(R.string.min_left, Durations.minutesText(limitRow.leftMillis))
                    setTextColor(ContextCompat.getColor(context, R.color.ink2))
                }
            }
            rows.addView(row)
        }
    }

    private fun renderBreakdown(state: TodayUiState) {
        root.findViewById<TextView>(R.id.breakdownWhen).text = state.hero.pausedAt
            ?.let { context.getString(R.string.until_time, Text.time(context, it)) }
            ?: context.getString(R.string.tab_today)
        val bar = root.findViewById<StackedBarView>(R.id.breakdownBar)
        val rows = root.findViewById<LinearLayout>(R.id.breakdownRows)
        rows.removeAllViews()
        root.findViewById<View>(R.id.breakdownEmpty).isVisible = state.breakdown.isEmpty()
        bar.isVisible = state.breakdown.isNotEmpty()
        bar.parts = state.breakdown.map { StackedBarView.Part(ContextCompat.getColor(context, Text.color(it.section)), it.millis) }
        bar.contentDescription = state.breakdown.joinToString { "${context.getString(Text.label(it.section))} ${it.percent} percent" }
        state.breakdown.forEachIndexed { i, section ->
            if (i > 0) rows.addView(divider(rows))
            val row = inflater.inflate(R.layout.item_section_row, rows, false)
            row.findViewById<View>(R.id.sectionDot).backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(context, Text.color(section.section)))
            row.findViewById<TextView>(R.id.sectionName).setText(Text.label(section.section))
            row.findViewById<TextView>(R.id.sectionTime).text = Durations.minutesText(section.millis)
            row.findViewById<TextView>(R.id.sectionPercent).text = context.getString(R.string.percent, section.percent)
            rows.addView(row)
        }
    }

    private fun renderStats(state: TodayUiState) {
        stat(R.id.statOpens, state.opens.toString(), context.getString(R.string.stat_opens))
        val average = state.averageOpenMillis / 60_000.0
        stat(R.id.statAverage, Text.withUnit(context, "%.1f".format(average), context.getString(R.string.unit_min)), context.getString(R.string.stat_average))
        when (val third = state.third) {
            is TodayUiState.ThirdStat.LongestStretch -> stat(
                R.id.statThird,
                Text.withUnit(context, (third.millis / 60_000).toString(), context.getString(R.string.unit_min)),
                context.getString(R.string.stat_longest),
            )
            is TodayUiState.ThirdStat.Overrides -> stat(
                R.id.statThird,
                Text.withUnit(context, third.used.toString(), context.getString(R.string.of_n, third.cap)),
                context.getString(R.string.stat_overrides),
            )
        }
    }

    private fun stat(id: Int, value: CharSequence, label: String) {
        val view = root.findViewById<View>(id)
        view.findViewById<TextView>(R.id.statValue).text = value
        view.findViewById<TextView>(R.id.statLabel).text = label
    }

    private fun renderWeek(week: TodayUiState.Week) {
        root.findViewById<TextView>(R.id.weekTitle).setText(if (week.thisWeek) R.string.this_week else R.string.last_7_days)
        root.findViewById<DayBarsView>(R.id.weekBars).apply {
            limitMillis = week.limitMillis
            bars = week.days.map { DayBarsView.Bar(Text.weekday(it.date), it.millis, it.isToday, it.isFuture) }
            contentDescription = week.days.filterNot { it.isFuture }
                .joinToString { "${Text.weekday(it.date)} ${Durations.spoken(it.millis)}" }
        }
        root.findViewById<TextView>(R.id.weekNote).text = context.getString(R.string.week_note, Durations.hm(week.limitMillis))
    }

    private fun renderGaps(gaps: List<TodayUiState.GapRow>, now: Instant) {
        root.findViewById<View>(R.id.gapsCard).isVisible = gaps.isNotEmpty()
        val rows = root.findViewById<LinearLayout>(R.id.gapRows)
        rows.removeAllViews()
        gaps.forEach { gap ->
            val row = inflater.inflate(R.layout.item_gap_row, rows, false)
            val start = Text.time(context, gap.start)
            val day = Text.localDate(gap.start)
            val dayName = if (day == Text.localDate(now)) context.getString(R.string.tab_today) else Text.weekday(day)
            row.findViewById<TextView>(R.id.gapWhen).text = if (gap.end == null) {
                context.getString(R.string.gap_ongoing_when, dayName, start)
            } else {
                context.getString(R.string.gap_when, dayName, start, Text.time(context, gap.end))
            }
            row.findViewById<TextView>(R.id.gapCause).setText(
                if (gap.cause == GapCause.SERVICE_OFF) R.string.gap_service_off else R.string.gap_stopped,
            )
            row.findViewById<TextView>(R.id.gapLength).apply {
                if (gap.end == null) {
                    setText(R.string.gap_ongoing)
                    setTextColor(ContextCompat.getColor(context, R.color.coral))
                } else {
                    text = Durations.minutesText(Duration.between(gap.start, gap.end).toMillis())
                    setTextColor(ContextCompat.getColor(context, R.color.ink2))
                }
            }
            rows.addView(row)
        }
    }

    private fun spokenGauge(used: Long, limit: Long): String {
        val base = context.getString(R.string.gauge_spoken, Durations.spoken(used), Durations.spoken(limit))
        val over = used - limit
        return if (over > 0) {
            base + ", " + context.getString(R.string.min_over, Durations.spoken(over))
        } else {
            base + ", " + context.getString(R.string.min_left, Durations.spoken(-over))
        }
    }

    private fun coralPill(pill: TextView, text: String) {
        pill.text = text
        pill.setBackgroundResource(R.drawable.bg_pill_coral)
        pill.setTextColor(ContextCompat.getColor(context, R.color.coral))
    }

    private fun neutralPill(pill: TextView, text: String) {
        pill.text = text
        pill.setBackgroundResource(R.drawable.bg_pill_neutral)
        pill.setTextColor(ContextCompat.getColor(context, R.color.ink))
    }

    private fun divider(parent: ViewGroup) = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
        setBackgroundColor(ContextCompat.getColor(context, R.color.line))
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    private data class Quad(val text: Int, val background: Int, val icon: Int, val color: Int)
}
