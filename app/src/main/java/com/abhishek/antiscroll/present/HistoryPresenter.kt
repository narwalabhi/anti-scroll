package com.abhishek.antiscroll.present

import com.abhishek.antiscroll.rules.profile.Surface
import com.abhishek.antiscroll.rules.usage.DayStats
import java.time.LocalDate
import kotlin.math.roundToInt

/** The History drill-in (docs/UI.md, History). */
data class HistoryUiState(
    val rangeDays: Int,
    val averageMillis: Long,
    /** The baseline week's daily average, once at least one baseline day is in. */
    val baselineAverageMillis: Long?,
    val daysUnder: Int,
    val daysCounted: Int,
    val lowest: Lowest?,
    val bars: List<Bar>,
    val limitMillis: Long,
    /** 24 bars starting at the day-start hour. */
    val hours: List<HourBar>,
    val lateSharePercent: Int,
    val peakHour: Int?,
    val reelsShareBaseline: Int?,
    val reelsShareRecent: Int?,
) {
    data class Lowest(val date: LocalDate, val millis: Long, val isToday: Boolean)
    data class Bar(val date: LocalDate, val millis: Long, val isToday: Boolean, val counted: Boolean)
    data class HourBar(val hour: Int, val millis: Long, val late: Boolean)
}

/**
 * Builds [HistoryUiState]. Days before the first counted day aren't averaged in as zeros. "Late" is
 * from [lateFromHour] (9 PM) until the day starts again.
 */
class HistoryPresenter(
    private val resetHour: Int,
    private val baselineDays: Int = TodayPresenter.BASELINE_DAYS,
    private val lateFromHour: Int = 21,
) {

    /** [days]: the calendar dates of the range, oldest first, ending today; [baseline]: the baseline week's dates. */
    fun present(days: List<DayStats>, baseline: List<DayStats>, firstDay: LocalDate, limitMillis: Long): HistoryUiState {
        val today = days.last().date
        val counted = days.filter { !it.date.isBefore(firstDay) }
        val totals = counted.map { it.totalMillis }
        val lowest = counted.minByOrNull { it.totalMillis }
        val hours = counted.fold(List(DayStats.HOURS) { 0L }) { acc, day -> acc.zip(day.hours(), Long::plus) }
        val ordered = (0 until DayStats.HOURS).map { (resetHour + it) % DayStats.HOURS }
        val lateHours = ordered.drop((lateFromHour - resetHour + DayStats.HOURS) % DayStats.HOURS).toSet()
        val hoursTotal = hours.sum()
        val recent = counted.takeLast(7)
        val stillInBaseline = !today.isAfter(firstDay.plusDays(baselineDays - 1L))

        return HistoryUiState(
            rangeDays = days.size,
            averageMillis = if (totals.isEmpty()) 0L else totals.sum() / totals.size,
            baselineAverageMillis = baseline.takeIf { it.isNotEmpty() }?.let { b -> b.sumOf { it.totalMillis } / b.size },
            daysUnder = counted.count { it.totalMillis < limitMillis },
            daysCounted = counted.size,
            lowest = lowest?.let { HistoryUiState.Lowest(it.date, it.totalMillis, it.date == today) },
            bars = days.map { HistoryUiState.Bar(it.date, it.totalMillis, it.date == today, !it.date.isBefore(firstDay)) },
            limitMillis = limitMillis,
            hours = ordered.map { HistoryUiState.HourBar(it, hours[it], it in lateHours) },
            lateSharePercent = if (hoursTotal == 0L) 0 else (lateHours.sumOf { hours[it] } * 100.0 / hoursTotal).roundToInt(),
            peakHour = hours.indices.maxByOrNull { hours[it] }?.takeIf { hours[it] > 0L },
            reelsShareBaseline = reelsShare(baseline),
            reelsShareRecent = if (stillInBaseline) null else reelsShare(recent),
        )
    }

    private fun reelsShare(days: List<DayStats>): Int? {
        val total = days.sumOf { it.totalMillis }
        if (total == 0L) return null
        return (days.sumOf { it.millisBySurface[Surface.REELS] ?: 0L } * 100.0 / total).roundToInt()
    }
}
