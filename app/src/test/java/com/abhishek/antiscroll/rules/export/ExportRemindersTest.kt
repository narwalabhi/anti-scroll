package com.abhishek.antiscroll.rules.export

import com.abhishek.antiscroll.rules.export.ExportReminders.Kind
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExportRemindersTest {

    private val first = LocalDate.parse("2026-10-29")

    @Test
    fun `baseline reminder from day 8, once`() {
        assertNull(ExportReminders.due(first, null, first.plusDays(6), emptySet()))
        assertEquals(Kind.BASELINE_DONE, ExportReminders.due(first, null, first.plusDays(7), emptySet()))
        assertNull(ExportReminders.due(first, null, first.plusDays(9), setOf(Kind.BASELINE_DONE)))
    }

    @Test
    fun `guarding reminder two weeks after guarding first ran`() {
        val guarded = first.plusDays(8)
        val shown = setOf(Kind.BASELINE_DONE)
        assertNull(ExportReminders.due(first, guarded, guarded.plusDays(13), shown))
        assertEquals(Kind.GUARDING_TWO_WEEKS, ExportReminders.due(first, guarded, guarded.plusDays(14), shown))
        assertNull(ExportReminders.due(first, guarded, guarded.plusDays(20), shown + Kind.GUARDING_TWO_WEEKS))
    }

    @Test
    fun `a late first reminder doesn't hide the second`() {
        val guarded = first.plusDays(8)
        assertEquals(Kind.BASELINE_DONE, ExportReminders.due(first, guarded, guarded.plusDays(20), emptySet()))
    }
}
