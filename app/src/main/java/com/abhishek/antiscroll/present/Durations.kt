package com.abhishek.antiscroll.present

/**
 * Durations as docs/UI.md writes them: "1h 47m", "58m", "45s"; "17 min" in running text; "6:12"
 * on timers; and a spoken form for screen readers.
 */
object Durations {

    /** "1h 47m", "2h", "58m", "45s", "0m". */
    fun hm(millis: Long): String {
        val totalSeconds = millis.coerceAtLeast(0L) / 1000
        if (totalSeconds in 1..59) return "${totalSeconds}s"
        val totalMinutes = totalSeconds / 60
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours == 0L -> "${minutes}m"
            minutes == 0L -> "${hours}h"
            else -> "${hours}h ${minutes}m"
        }
    }

    /** Whole minutes for running text, rounded up so "17 min over" never says 0: "17 min", "1h 5m". */
    fun minutesText(millis: Long): String {
        val minutes = (millis.coerceAtLeast(0L) + 59_999) / 60_000
        return if (minutes < 60) "$minutes min" else hm(minutes * 60_000)
    }

    /** A timer: "0:42", "6:12", "1:02:03". Rounded up, so it reads 0:00 only when time is up. */
    fun clock(millis: Long): String {
        val totalSeconds = (millis.coerceAtLeast(0L) + 999) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
    }

    /** "1 hour 47 minutes", "1 minute", "30 seconds", for content descriptions. */
    fun spoken(millis: Long): String {
        val totalSeconds = millis.coerceAtLeast(0L) / 1000
        if (totalSeconds < 60) return plural(totalSeconds, "second")
        val totalMinutes = totalSeconds / 60
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return listOfNotNull(
            plural(hours, "hour").takeIf { hours > 0 },
            plural(minutes, "minute").takeIf { minutes > 0 },
        ).joinToString(" ")
    }

    private fun plural(n: Long, unit: String) = if (n == 1L) "1 $unit" else "$n ${unit}s"
}
