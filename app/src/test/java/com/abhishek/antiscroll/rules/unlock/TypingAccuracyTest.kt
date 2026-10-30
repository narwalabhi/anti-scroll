package com.abhishek.antiscroll.rules.unlock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypingAccuracyTest {

    private val target = "The kettle clicked off just as the rain began to fall." // 54 characters

    @Test
    fun `exact text scores 1`() = assertEquals(1.0, TypingAccuracy.score(target, target), 0.0)

    @Test
    fun `one typo in a sentence still passes 95 percent`() {
        val score = TypingAccuracy.score("The kettle clicked off just as the rain begam to fall.", target)
        assertEquals(1.0 - 1.0 / 54, score, 1e-9)
        assertTrue(score >= 0.95)
    }

    @Test
    fun `four mistakes in a sentence fail 95 percent`() {
        val score = TypingAccuracy.score("Teh kettle clicked of just as teh rain began to fal.", target)
        assertTrue("was $score", score < 0.95)
    }

    @Test
    fun `case and punctuation count`() {
        assertEquals(1.0 - 1.0 / 54, TypingAccuracy.score("the kettle clicked off just as the rain began to fall.", target), 1e-9)
        assertEquals(1.0 - 1.0 / 54, TypingAccuracy.score("The kettle clicked off just as the rain began to fall", target), 1e-9)
    }

    @Test
    fun `extra spaces and surrounding whitespace don't count`() =
        assertEquals(1.0, TypingAccuracy.score("  The kettle  clicked off just as the rain began to   fall.\n", target), 0.0)

    @Test
    fun `nothing typed scores 0`() = assertEquals(0.0, TypingAccuracy.score("", target), 0.0)

    @Test
    fun `extra text costs too and the score never goes below 0`() {
        assertEquals(1.0 - 4.0 / 54, TypingAccuracy.score("$target abc", target), 1e-9)
        assertEquals(0.0, TypingAccuracy.score("x".repeat(500), target), 0.0)
    }
}
