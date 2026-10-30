package com.abhishek.antiscroll.rules.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FallbackCoverTest {

    private val ig = "com.instagram.android"
    private val cover = FallbackCover(ig)

    @Test
    fun `the last app resumed is in front until it pauses`() {
        val events = listOf(
            AppEvent("launcher", AppEvent.Kind.RESUMED),
            AppEvent("launcher", AppEvent.Kind.PAUSED),
            AppEvent(ig, AppEvent.Kind.RESUMED),
        )
        assertEquals(ig, ForegroundApp.fold(null, events))
        assertNull(ForegroundApp.next(ig, AppEvent(ig, AppEvent.Kind.PAUSED)))
    }

    @Test
    fun `another app pausing late doesn't hide the one in front`() {
        // Android may report the old app's pause after the new one's resume.
        assertEquals(ig, ForegroundApp.next(ig, AppEvent("launcher", AppEvent.Kind.PAUSED)))
    }

    @Test
    fun `Instagram in front is covered`() {
        val state = cover.onForeground(FallbackCover.State(), ig)
        assertTrue(cover.showsCover(state, enforcementEnabled = false))
        assertTrue(cover.showsCover(state, enforcementEnabled = true))
        assertFalse(cover.showsCover(cover.onForeground(state, "launcher"), enforcementEnabled = true))
    }

    @Test
    fun `uncounted use lasts until Instagram leaves the front`() {
        val uncounted = cover.useUncounted(cover.onForeground(FallbackCover.State(), ig))
        assertFalse(cover.showsCover(uncounted, enforcementEnabled = false))
        assertFalse(cover.showsCover(cover.onForeground(uncounted, ig), enforcementEnabled = false))

        val back = cover.onForeground(cover.onForeground(uncounted, "launcher"), ig)
        assertTrue(cover.showsCover(back, enforcementEnabled = false))
    }

    @Test
    fun `uncounted is not offered or honoured while guarding`() {
        assertFalse(cover.offersUncounted(enforcementEnabled = true))
        val uncounted = cover.useUncounted(cover.onForeground(FallbackCover.State(), ig))
        assertTrue(cover.showsCover(uncounted, enforcementEnabled = true))
    }
}
