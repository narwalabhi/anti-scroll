package com.abhishek.antiscroll.data

import com.abhishek.antiscroll.rules.health.GapCause
import com.abhishek.antiscroll.rules.health.HealthRecord
import com.abhishek.antiscroll.rules.health.TrackingGap
import com.abhishek.antiscroll.rules.profile.Surface
import com.abhishek.antiscroll.rules.settings.AppSettings
import com.abhishek.antiscroll.rules.usage.DayStats
import com.abhishek.antiscroll.rules.usage.DayUsage
import com.abhishek.antiscroll.rules.usage.Session
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupFileTest {

    @Test
    fun `a backup comes back exactly as written`() {
        val date = LocalDate.parse("2026-10-30")
        val t = Instant.parse("2026-10-30T12:00:00Z")
        val contents = BackupFile.Contents(
            installId = "abc",
            writtenAt = t,
            settings = AppSettings(enforcementEnabled = true, dailyTotalMinutes = 60, surfaceMinutes = mapOf(Surface.REELS to 10)),
            usage = listOf(DayUsage(date, opens = 4, overridesUsed = 1, session = Session("Just browsing", t, startedAt = t))),
            stats = listOf(DayStats(date, opens = 4, millisBySurface = mapOf(Surface.FEED to 60_000L), guarded = true)),
            health = HealthRecord(true, t, listOf(TrackingGap(t, null, GapCause.SERVICE_OFF))),
        )
        assertEquals(contents, BackupFile.decode(BackupFile.encode(contents)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `another file is refused`() {
        BackupFile.decode("{\"format\": 99}")
    }
}
