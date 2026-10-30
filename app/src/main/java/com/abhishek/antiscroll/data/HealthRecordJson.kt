package com.abhishek.antiscroll.data

import com.abhishek.antiscroll.rules.health.GapCause
import com.abhishek.antiscroll.rules.health.HealthRecord
import com.abhishek.antiscroll.rules.health.TrackingGap
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

/** Turns a [HealthRecord] into JSON for storage and back. Gaps with an unknown cause are dropped. */
object HealthRecordJson {

    fun encode(record: HealthRecord): String = JSONObject().apply {
        put("running", record.running)
        record.lastAliveAt?.let { put("lastAliveAt", it.toString()) }
        put(
            "gaps",
            JSONArray().apply {
                record.gaps.forEach { gap ->
                    put(
                        JSONObject().apply {
                            put("start", gap.start.toString())
                            gap.end?.let { put("end", it.toString()) }
                            put("cause", gap.cause.name)
                        },
                    )
                }
            },
        )
    }.toString()

    fun decode(json: String): HealthRecord {
        val root = JSONObject(json)
        val gaps = root.optJSONArray("gaps") ?: JSONArray()
        return HealthRecord(
            running = root.optBoolean("running", false),
            lastAliveAt = root.optString("lastAliveAt").takeIf { it.isNotEmpty() }?.let(Instant::parse),
            gaps = (0 until gaps.length()).mapNotNull { i ->
                val gap = gaps.getJSONObject(i)
                val cause = GapCause.entries.find { it.name == gap.optString("cause") } ?: return@mapNotNull null
                TrackingGap(
                    start = Instant.parse(gap.getString("start")),
                    end = gap.optString("end").takeIf { it.isNotEmpty() }?.let(Instant::parse),
                    cause = cause,
                )
            },
        )
    }
}
