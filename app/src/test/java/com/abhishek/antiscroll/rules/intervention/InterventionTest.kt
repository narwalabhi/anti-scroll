package com.abhishek.antiscroll.rules.intervention

import com.abhishek.antiscroll.rules.engine.BlockReason
import com.abhishek.antiscroll.rules.engine.Effect
import com.abhishek.antiscroll.rules.profile.Surface
import com.abhishek.antiscroll.rules.usage.Session
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterventionTest {

    private val session = Session("Just browsing", Instant.parse("2026-10-29T18:00:00Z"))
    private val reels = BlockReason.SurfaceBudget(Surface.REELS)
    private val stepBack = StepBackIntervention(fallback = GoHomeIntervention())

    @Test
    fun `a surface block steps one screen back and stays in Instagram`() =
        assertEquals(LeavePlan.StayInInstagram(listOf(Effect.GoBack)), stepBack.onLeave(reels, session))

    @Test
    fun `blocks that cover all of Instagram fall back to going home`() {
        val home = LeavePlan.EndVisit(listOf(Effect.GoHome))
        assertEquals(home, stepBack.onLeave(BlockReason.DailyTotal, session))
        assertEquals(home, stepBack.onLeave(BlockReason.SessionTimeUp, session))
    }

    @Test
    fun `without a session to carry on with, a surface block goes home too`() =
        assertEquals(LeavePlan.EndVisit(listOf(Effect.GoHome)), stepBack.onLeave(reels, null))

    @Test
    fun `go home always ends the visit`() =
        assertEquals(LeavePlan.EndVisit(listOf(Effect.GoHome)), GoHomeIntervention().onLeave(reels, session))

    @Test
    fun `a surface block keeps the session and lifts anywhere but its surface`() {
        assertTrue(reels.keepsSession)
        assertTrue(reels.liftsOn(Surface.FEED))
        assertFalse(reels.liftsOn(Surface.REELS))
    }

    @Test
    fun `the other blocks keep nothing and never lift by moving`() {
        listOf(BlockReason.DailyTotal, BlockReason.SessionTimeUp).forEach { reason ->
            assertFalse(reason.keepsSession)
            Surface.entries.forEach { assertFalse(reason.liftsOn(it)) }
        }
    }
}
