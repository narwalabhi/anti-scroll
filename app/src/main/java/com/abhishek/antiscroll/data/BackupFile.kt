package com.abhishek.antiscroll.data

import com.abhishek.antiscroll.rules.health.HealthRecord
import com.abhishek.antiscroll.rules.settings.AppSettings
import com.abhishek.antiscroll.rules.usage.DayStats
import com.abhishek.antiscroll.rules.usage.DayUsage
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

/**
 * The backup kept in the user's folder (docs/LLD.md, Backup folder): everything needed to restore
 * after a reinstall, in the stores' own JSON forms. Counts and timestamps only.
 */
object BackupFile {

    const val FILE_NAME = "antiscroll-backup.json"
    private const val FORMAT_VERSION = 1

    /** [installId]: which install wrote it, so an install never restores (and double-counts) its own copy. */
    data class Contents(
        val installId: String,
        val writtenAt: Instant,
        val settings: AppSettings,
        val usage: List<DayUsage>,
        val stats: List<DayStats>,
        val health: HealthRecord,
    )

    fun encode(contents: Contents): String = JSONObject().apply {
        put("format", FORMAT_VERSION)
        put("installId", contents.installId)
        put("writtenAt", contents.writtenAt.toString())
        put(
            "settings",
            JSONObject().apply {
                put("guarding", contents.settings.enforcementEnabled)
                put("dailyTotalMinutes", contents.settings.dailyTotalMinutes)
                put("surfaceMinutes", SurfaceMinutesCodec.encode(contents.settings.surfaceMinutes))
            },
        )
        put("usage", JSONArray().apply { contents.usage.forEach { put(JSONObject(DayUsageJson.encode(it))) } })
        put("stats", JSONArray().apply { contents.stats.forEach { put(JSONObject(DayStatsJson.encode(it))) } })
        put("health", JSONObject(HealthRecordJson.encode(contents.health)))
    }.toString()

    /** Throws on a file that isn't a backup. */
    fun decode(json: String): Contents {
        val root = JSONObject(json)
        require(root.optInt("format") == FORMAT_VERSION) { "not an AntiScroll backup" }
        val settings = root.getJSONObject("settings")
        fun JSONArray.objects() = (0 until length()).map { getJSONObject(it).toString() }
        return Contents(
            installId = root.optString("installId"),
            writtenAt = Instant.parse(root.getString("writtenAt")),
            settings = AppSettings(
                enforcementEnabled = settings.getBoolean("guarding"),
                dailyTotalMinutes = settings.getInt("dailyTotalMinutes"),
                surfaceMinutes = SurfaceMinutesCodec.decode(settings.getString("surfaceMinutes")),
            ),
            usage = root.getJSONArray("usage").objects().map(DayUsageJson::decode),
            stats = root.getJSONArray("stats").objects().map(DayStatsJson::decode),
            health = HealthRecordJson.decode(root.getJSONObject("health").toString()),
        )
    }
}
