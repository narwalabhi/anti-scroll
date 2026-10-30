package com.abhishek.antiscroll.data

import com.abhishek.antiscroll.rules.health.GapCause
import com.abhishek.antiscroll.rules.health.TrackingGap
import com.abhishek.antiscroll.rules.profile.Surface
import com.abhishek.antiscroll.rules.settings.AppSettings
import com.abhishek.antiscroll.rules.usage.DayStats
import com.abhishek.antiscroll.rules.usage.DayUsage
import java.time.Instant
import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryExportTest {

    private val date = LocalDate.parse("2026-10-29")
    private val device = HistoryExport.Device("1.0", "Nothing", "A142P", 35, "400.0")

    private fun export(): JSONObject = JSONObject(
        HistoryExport.build(
            days = listOf(
                DayStats(date, opens = 3, millisBySurface = mapOf(Surface.REELS to 90_500L), guarded = true),
                DayStats(date.plusDays(1)),
            ),
            usageByDay = mapOf(date to DayUsage(date, overridesUsed = 2)),
            settings = AppSettings(),
            gaps = listOf(TrackingGap(Instant.parse("2026-10-29T10:00:00Z"), null, GapCause.SERVICE_OFF)),
            device = device,
            exportedAt = Instant.parse("2026-10-30T09:00:00Z"),
        ),
    )

    @Test
    fun `every day is exported in seconds with its overrides and guarding flag`() {
        val days = export().getJSONArray("days")
        assertEquals(2, days.length())
        val first = days.getJSONObject(0)
        assertEquals("2026-10-29", first.getString("date"))
        assertTrue(first.getBoolean("guarded"))
        assertEquals(90, first.getJSONObject("secondsBySurface").getLong("REELS"))
        assertEquals(2, first.getInt("overridesUsed"))
        assertEquals(0, days.getJSONObject(1).getInt("overridesUsed"))
    }

    @Test
    fun `settings, gaps and device are included`() {
        val root = export()
        assertEquals(90, root.getJSONObject("settings").getInt("dailyTotalMinutes"))
        assertTrue(root.getJSONArray("gaps").getJSONObject(0).isNull("end"))
        assertEquals("Nothing", root.getJSONObject("device").getString("manufacturer"))
    }
}
