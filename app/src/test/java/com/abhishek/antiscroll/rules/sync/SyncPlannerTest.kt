package com.abhishek.antiscroll.rules.sync

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncPlannerTest {

    private val d1 = LocalDate.parse("2026-10-29")
    private val d2 = d1.plusDays(1)

    @Test
    fun `unsent and changed days are sent, oldest first`() {
        assertEquals(listOf(d1, d2), SyncPlanner.datesToSend(mapOf(d2 to 5, d1 to 1), emptyMap()))
        assertEquals(listOf(d2), SyncPlanner.datesToSend(mapOf(d1 to 1, d2 to 6), mapOf(d1 to 1, d2 to 5)))
        assertEquals(emptyList<LocalDate>(), SyncPlanner.datesToSend(mapOf(d1 to 1), mapOf(d1 to 1)))
    }
}
