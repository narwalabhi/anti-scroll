package com.abhishek.antiscroll.data

import com.abhishek.antiscroll.rules.profile.Surface
import com.abhishek.antiscroll.rules.usage.DayStats
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

/** Turns a [DayStats] into JSON for storage and back. Surface names that no longer exist are dropped. */
object DayStatsJson {

    fun encode(stats: DayStats): String = JSONObject().apply {
        put("date", stats.date.toString())
        put("opens", stats.opens)
        put("millisBySurface", JSONObject().apply { stats.millisBySurface.forEach { (s, ms) -> put(s.name, ms) } })
        put("swipesBySurface", JSONObject().apply { stats.swipesBySurface.forEach { (s, n) -> put(s.name, n) } })
        put("longestVisitMillis", stats.longestVisitMillis)
        put("guarded", stats.guarded)
        if (stats.millisByHour.isNotEmpty()) put("millisByHour", JSONArray(stats.millisByHour))
    }.toString()

    fun decode(json: String): DayStats {
        val root = JSONObject(json)
        return DayStats(
            date = LocalDate.parse(root.getString("date")),
            opens = root.optInt("opens", 0),
            millisBySurface = root.optJSONObject("millisBySurface").toSurfaceMap { getLong(it) },
            swipesBySurface = root.optJSONObject("swipesBySurface").toSurfaceMap { getInt(it) },
            longestVisitMillis = root.optLong("longestVisitMillis", 0L),
            guarded = root.optBoolean("guarded", false),
            millisByHour = root.optJSONArray("millisByHour")?.let { hours -> List(hours.length()) { hours.getLong(it) } }
                ?: emptyList(),
        )
    }

    private fun <T> JSONObject?.toSurfaceMap(value: JSONObject.(String) -> T): Map<Surface, T> {
        if (this == null) return emptyMap()
        return keys().asSequence().mapNotNull { key ->
            Surface.entries.find { it.name == key }?.let { it to value(key) }
        }.toMap()
    }
}
