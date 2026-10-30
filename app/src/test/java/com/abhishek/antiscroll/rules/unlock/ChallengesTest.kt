package com.abhishek.antiscroll.rules.unlock

import java.time.Instant
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengesTest {

    private val t0 = Instant.parse("2026-10-29T17:00:00Z")
    private fun at(seconds: Long): Instant = t0.plusSeconds(seconds)
    private fun sentences() = TypingSentences(Random(42))

    private fun typingPrompt(run: ChallengeRun) = run.prompt as ChallengePrompt.Typing

    // Wait

    @Test
    fun `a wait reported done too early changes nothing`() {
        val run = WaitChallenge(30).begin(t0)
        assertEquals(ChallengePrompt.Wait(30), run.prompt)
        assertEquals(ChallengeStatus.InProgress, run.submit(ChallengeAnswer.WaitDone, at(29)).status)
    }

    @Test
    fun `a wait passes once its seconds have gone by`() {
        val run = WaitChallenge(30).begin(t0)
        assertEquals(ChallengeStatus.Passed, run.submit(ChallengeAnswer.WaitDone, at(30)).status)
    }

    @Test
    fun `typing during a wait does nothing`() {
        val run = WaitChallenge(30).begin(t0)
        assertEquals(ChallengeStatus.InProgress, run.submit(ChallengeAnswer.Typed("hello"), at(60)).status)
    }

    // Typing

    @Test
    fun `typing the text passes`() {
        val run = TypingChallenge(1, 0.95, sentences()).begin(t0)
        val text = typingPrompt(run).text
        assertEquals(ChallengeStatus.Passed, run.submit(ChallengeAnswer.Typed(text), t0).status)
    }

    @Test
    fun `a failed attempt gets different text`() {
        val run = TypingChallenge(1, 0.95, sentences()).begin(t0)
        val step = run.submit(ChallengeAnswer.Typed("nope"), t0)
        assertEquals(ChallengeStatus.InProgress, step.status)
        assertNotEquals(typingPrompt(run).text, typingPrompt(step.run).text)
    }

    @Test
    fun `near-perfect accuracy rejects a single typo in a short text`() {
        val run = TypingChallenge(1, 0.99, sentences()).begin(t0)
        val text = typingPrompt(run).text
        val oneTypo = text.dropLast(2) + "x" + text.last()
        assertEquals(ChallengeStatus.InProgress, run.submit(ChallengeAnswer.Typed(oneTypo), t0).status)
    }

    @Test
    fun `the prompt asks for the right number of sentences and accuracy`() {
        val prompt = typingPrompt(TypingChallenge(2, 0.95, sentences()).begin(t0))
        assertEquals(0.95, prompt.minAccuracy, 0.0)
        assertEquals(2, TypingSentences.ALL.count { it in prompt.text })
    }

    // Sequence

    @Test
    fun `a step runs its wait, then its typing`() {
        val step = SequenceChallenge(listOf(WaitChallenge(30), TypingChallenge(1, 0.95, sentences())))
        var run = step.begin(t0)
        assertEquals(ChallengePrompt.Wait(30), run.prompt)

        val afterWait = run.submit(ChallengeAnswer.WaitDone, at(30))
        assertEquals(ChallengeStatus.InProgress, afterWait.status)
        run = afterWait.run
        assertTrue(run.prompt is ChallengePrompt.Typing)

        val passed = run.submit(ChallengeAnswer.Typed(typingPrompt(run).text), at(60))
        assertEquals(ChallengeStatus.Passed, passed.status)
    }

    @Test
    fun `a typing retry keeps the finished wait`() {
        val step = SequenceChallenge(listOf(WaitChallenge(30), TypingChallenge(1, 0.95, sentences())))
        val typing = step.begin(t0).submit(ChallengeAnswer.WaitDone, at(30)).run
        val retry = typing.submit(ChallengeAnswer.Typed("wrong"), at(40))
        assertEquals(ChallengeStatus.InProgress, retry.status)
        assertTrue("still typing, not back to the wait", retry.run.prompt is ChallengePrompt.Typing)
        assertEquals(ChallengeStatus.Passed, retry.run.submit(ChallengeAnswer.Typed(typingPrompt(retry.run).text), at(50)).status)
    }

    @Test
    fun `an early wait report inside a sequence stays on the wait`() {
        val run = SequenceChallenge(listOf(WaitChallenge(30), TypingChallenge(1, 0.95, sentences()))).begin(t0)
        val step = run.submit(ChallengeAnswer.WaitDone, at(10))
        assertEquals(ChallengePrompt.Wait(30), step.run.prompt)
    }

    @Test
    fun `an empty sequence is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { SequenceChallenge(emptyList()) }
    }

    // Factory and sentences

    @Test
    fun `the factory builds the parts in order`() {
        val challenge = ChallengeFactory(sentences()).build(
            listOf(ChallengeSpec.Wait(60), ChallengeSpec.Typing(TypingSentences.PARAGRAPH_SENTENCES, 0.99)),
        )
        val run = challenge.begin(t0)
        assertEquals(ChallengePrompt.Wait(60), run.prompt)
        val typing = typingPrompt(run.submit(ChallengeAnswer.WaitDone, at(60)).run)
        assertEquals(0.99, typing.minAccuracy, 0.0)
        assertEquals(4, TypingSentences.ALL.count { it in typing.text })
    }

    @Test
    fun `sentences are distinct, plain and of a typeable length`() {
        val all = TypingSentences.ALL
        assertEquals(all.size, all.toSet().size)
        all.forEach { sentence ->
            assertTrue(sentence, sentence.length in 25..70)
            assertTrue(sentence, sentence.all { it.code < 128 })
            assertTrue(sentence, sentence.last() == '.')
        }
    }

    @Test
    fun `the same seed picks the same text`() {
        assertEquals(TypingSentences(Random(7)).pick(2), TypingSentences(Random(7)).pick(2))
    }
}
