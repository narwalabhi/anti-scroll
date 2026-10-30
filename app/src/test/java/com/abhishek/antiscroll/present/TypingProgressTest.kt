package com.abhishek.antiscroll.present

import com.abhishek.antiscroll.present.TypingProgress.Mark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TypingProgressTest {

    @Test
    fun `marks right, wrong, next and to-do characters`() {
        val p = TypingProgress.of("abcd", "ax", 0.95)
        assertEquals(listOf(Mark.RIGHT, Mark.WRONG, Mark.NEXT, Mark.TODO), p.marks)
        assertEquals(2, p.typedCount)
        assertEquals(50, p.accuracyPercent)
        assertFalse(p.complete)
    }

    @Test
    fun `continue needs the whole text at the required accuracy`() {
        val target = "the quiet light of early morning settles over the harbour"
        assertTrue(TypingProgress.of(target, target, 0.95).canContinue)
        assertFalse(TypingProgress.of(target, target.dropLast(3), 0.95).canContinue)
        val oneTypo = target.replaceFirst("quiet", "qiuet")
        assertTrue(TypingProgress.of(target, oneTypo, 0.95).canContinue)
        assertFalse(TypingProgress.of(target, oneTypo, 0.99).canContinue)
    }

    @Test
    fun `nothing typed reads as full accuracy`() {
        assertEquals(100, TypingProgress.of("abc", "", 0.95).accuracyPercent)
    }
}
