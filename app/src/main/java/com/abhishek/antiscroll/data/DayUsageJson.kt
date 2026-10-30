package com.abhishek.antiscroll.data

import com.abhishek.antiscroll.rules.profile.Surface
import com.abhishek.antiscroll.rules.usage.DayUsage
import com.abhishek.antiscroll.rules.usage.Session
import java.time.Instant
import java.time.LocalDate
import org.json.JSONObject

/** Turns a [DayUsage] into JSON for storage and back. Surface names that no longer exist are dropped. */
object DayUsageJson {

    fun encode(usage: DayUsage): String = JSONObject().apply {
        put("day", usage.day.toString())
        put("opens", usage.opens)
        put("millisBySurface", JSONObject().apply { usage.millisBySurface.forEach { (s, ms) -> put(s.name, ms) } })
        put("swipesBySurface", JSONObject().apply { usage.swipesBySurface.forEach { (s, n) -> put(s.name, n) } })
        put("overridesUsed", usage.overridesUsed)
        usage.cooldownUntil?.let { put("cooldownUntil", it.toString()) }
        usage.breakUntil?.let { put("breakUntil", it.toString()) }
        usage.session?.let { session ->
            put(
                "session",
                JSONObject().apply {
                    put("purpose", session.purpose)
                    put("allowedUntil", session.allowedUntil.toString())
                    put("byOverride", session.byOverride)
                    session.startedAt?.let { put("startedAt", it.toString()) }
                },
            )
        }
    }.toString()

    fun decode(json: String): DayUsage {
        val root = JSONObject(json)
        return DayUsage(
            day = LocalDate.parse(root.getString("day")),
            opens = root.getInt("opens"),
            millisBySurface = root.optJSONObject("millisBySurface").toSurfaceMap { getLong(it) },
            swipesBySurface = root.optJSONObject("swipesBySurface").toSurfaceMap { getInt(it) },
            overridesUsed = root.optInt("overridesUsed", 0),
            cooldownUntil = root.optInstant("cooldownUntil"),
            breakUntil = root.optInstant("breakUntil"),
            session = root.optJSONObject("session")?.let { session ->
                Session(
                    purpose = session.getString("purpose"),
                    allowedUntil = Instant.parse(session.getString("allowedUntil")),
                    byOverride = session.optBoolean("byOverride", false),
                    startedAt = session.optInstant("startedAt"),
                )
            },
        )
    }

    private fun JSONObject.optInstant(name: String): Instant? = optString(name).takeIf { it.isNotEmpty() }?.let(Instant::parse)

    private fun <T> JSONObject?.toSurfaceMap(value: JSONObject.(String) -> T): Map<Surface, T> {
        if (this == null) return emptyMap()
        return keys().asSequence().mapNotNull { key ->
            Surface.entries.find { it.name == key }?.let { it to value(key) }
        }.toMap()
    }
}
