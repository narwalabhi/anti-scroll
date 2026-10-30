package com.abhishek.antiscroll.data

import com.abhishek.antiscroll.rules.profile.Surface
import com.abhishek.antiscroll.rules.settings.AppSettings
import com.abhishek.antiscroll.rules.usage.DayStats
import com.abhishek.antiscroll.rules.usage.DayUsage
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class CloudDocumentsTest {

    private val date = LocalDate.parse("2026-10-30")

    @Test
    fun `a day carries seconds, swipes, overrides and the guarding flag`() {
        val day = CloudDocuments.day(
            DayStats(date, opens = 2, millisBySurface = mapOf(Surface.REELS to 61_900L), swipesBySurface = mapOf(Surface.REELS to 3), guarded = true),
            DayUsage(date, overridesUsed = 1),
        )
        assertEquals("2026-10-30", day["date"])
        assertEquals(mapOf("REELS" to 61L), day["secondsBySurface"])
        assertEquals(1, day["overridesUsed"])
        assertEquals(true, day["guarded"])
    }

    @Test
    fun `the install carries the limits and the device`() {
        val install = CloudDocuments.install(
            AppSettings(), emptyList(), HistoryExport.Device("0.1.5", "Nothing", "A142P", 36, null), date, Instant.EPOCH,
        )
        assertEquals(90, install["dailyTotalMinutes"])
        assertEquals(mapOf("REELS" to 15), install["surfaceMinutes"])
        assertEquals("Nothing", install["manufacturer"])
    }

    @Test
    fun `a settings change records before, after and where it came from`() {
        val change = CloudDocuments.settingsChange(
            AppSettings(), AppSettings(enforcementEnabled = true, dailyTotalMinutes = 60),
            com.abhishek.antiscroll.rules.settings.ChangeSource.USER, Instant.EPOCH,
        )
        assertEquals("USER", change["source"])
        assertEquals(90, (change["from"] as Map<*, *>)["dailyTotalMinutes"])
        assertEquals(true, (change["to"] as Map<*, *>)["guarding"])
    }
}
