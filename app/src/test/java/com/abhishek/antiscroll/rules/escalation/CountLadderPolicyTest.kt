package com.abhishek.antiscroll.rules.escalation

import com.abhishek.antiscroll.rules.settings.LaunchSettings
import com.abhishek.antiscroll.rules.unlock.ChallengeSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** The SPEC's ladder table, row by row. */
class CountLadderPolicyTest {

    private val policy = CountLadderPolicy(LadderSettings())

    private fun assertStep(overridesToday: Int, waitSeconds: Int, sentences: Int, accuracy: Double, grant: Int) {
        val step = policy.stepFor(overridesToday)!!
        assertEquals(
            listOf(ChallengeSpec.Wait(waitSeconds), ChallengeSpec.Typing(sentences, accuracy)),
            step.challenges,
        )
        assertEquals(grant, step.grantMinutes)
    }

    @Test
    fun `1st override - 30 s and a sentence for 10 min`() = assertStep(0, 30, 1, 0.95, 10)

    @Test
    fun `2nd override - 1 min and 2 sentences for 5 min`() = assertStep(1, 60, 2, 0.95, 5)

    @Test
    fun `3rd override - 2 min and a paragraph for 3 min`() = assertStep(2, 120, 4, 0.95, 3)

    @Test
    fun `4th and 5th override - 5 min and a near-perfect paragraph for 2 min`() {
        assertStep(3, 300, 4, 0.99, 2)
        assertStep(4, 300, 4, 0.99, 2)
    }

    @Test
    fun `no 6th override`() {
        assertNull(policy.stepFor(5))
        assertNull(policy.stepFor(9))
        assertEquals(5, LadderSettings().dailyCap)
    }

    @Test
    fun `cooldown is 20 min plus 10 for each earlier override`() {
        assertEquals(listOf(20, 30, 40, 50, 60), (1..5).map(policy::cooldownMinutes))
    }

    @Test
    fun `no cooldown before any override`() = assertEquals(0, policy.cooldownMinutes(0))

    @Test
    fun `the break is 15 minutes`() = assertEquals(15, LadderSettings().breakMinutes)

    @Test
    fun `short timers keep the typing but shorten every wait and grant`() {
        val short = LadderSettings.shortForTesting()
        val shortPolicy = CountLadderPolicy(short)
        assertEquals(5, short.dailyCap)
        (0 until 5).forEach { n ->
            val step = shortPolicy.stepFor(n)!!
            assertEquals(ChallengeSpec.Wait(5), step.challenges.first())
            assertEquals(policy.stepFor(n)!!.challenges.last(), step.challenges.last())
            assertEquals(1, step.grantMinutes)
        }
        assertEquals(listOf(1, 1, 1), (1..3).map(shortPolicy::cooldownMinutes))
        assertEquals(1, short.breakMinutes)
        assertEquals(listOf(1, 2), LaunchSettings.shortForTesting().minuteChoices)
    }

    @Test
    fun `invalid ladders are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { LadderSettings(steps = emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { LadderStep(emptyList(), 5) }
        assertThrows(IllegalArgumentException::class.java) { LadderStep(listOf(ChallengeSpec.Wait(1)), 0) }
    }
}
