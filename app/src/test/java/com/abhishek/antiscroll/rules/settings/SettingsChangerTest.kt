package com.abhishek.antiscroll.rules.settings

import com.abhishek.antiscroll.rules.escalation.LadderSettings
import com.abhishek.antiscroll.rules.profile.Surface
import com.abhishek.antiscroll.rules.unlock.ChallengeAnswer
import com.abhishek.antiscroll.rules.unlock.ChallengeFactory
import com.abhishek.antiscroll.rules.unlock.ChallengePrompt
import com.abhishek.antiscroll.rules.unlock.ChallengeStatus
import com.abhishek.antiscroll.rules.unlock.TypingSentences
import com.abhishek.antiscroll.rules.usage.DayUsage
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsChangerTest {

    private val changer = SettingsChanger(ChallengeFactory(TypingSentences(Random(1))), LadderSettings())
    private val now = Instant.parse("2026-10-29T20:00:00Z")
    private val day = DayUsage(LocalDate.parse("2026-10-29"))
    private val on = AppSettings(enforcementEnabled = true, dailyTotalMinutes = 90, surfaceMinutes = mapOf(Surface.REELS to 30))

    private fun request(wanted: AppSettings, current: AppSettings = on, usage: DayUsage = day) =
        changer.request(current, wanted, usage, now)

    // Tightening

    @Test
    fun `turning enforcement on applies at once`() {
        val wanted = on.copy(enforcementEnabled = true)
        assertEquals(SettingsChanger.Answer.Apply(wanted), request(wanted, current = on.copy(enforcementEnabled = false)))
    }

    @Test
    fun `lowering a limit applies at once`() {
        assertEquals(SettingsChanger.Answer.Apply(on.copy(dailyTotalMinutes = 60)), request(on.copy(dailyTotalMinutes = 60)))
        assertEquals(SettingsChanger.Answer.Apply(on.copy(surfaceMinutes = mapOf(Surface.REELS to 0))), request(on.copy(surfaceMinutes = mapOf(Surface.REELS to 0))))
    }

    @Test
    fun `no change at all applies at once`() = assertEquals(SettingsChanger.Answer.Apply(on), request(on))

    @Test
    fun `tightening is allowed even with the overrides used up`() {
        val wanted = on.copy(surfaceMinutes = mapOf(Surface.REELS to 10))
        assertEquals(SettingsChanger.Answer.Apply(wanted), request(wanted, usage = day.copy(overridesUsed = 5)))
    }

    // Loosening

    @Test
    fun `turning enforcement off, or raising a limit, loosens`() {
        assertTrue(changer.loosens(on, on.copy(enforcementEnabled = false)))
        assertTrue(changer.loosens(on, on.copy(dailyTotalMinutes = 120)))
        assertTrue(changer.loosens(on, on.copy(surfaceMinutes = mapOf(Surface.REELS to 45))))
        assertFalse(changer.loosens(on, on.copy(dailyTotalMinutes = 60, surfaceMinutes = mapOf(Surface.REELS to 20))))
    }

    @Test
    fun `adding a surface limit tightens, removing one loosens`() {
        val withFeed = on.copy(surfaceMinutes = mapOf(Surface.REELS to 30, Surface.FEED to 20))
        assertFalse(changer.loosens(on, withFeed))
        assertTrue(changer.loosens(withFeed, on))
        assertTrue(changer.loosens(on, on.copy(surfaceMinutes = emptyMap())))
    }

    @Test
    fun `a change that loosens anything counts as loosening`() =
        assertTrue(changer.loosens(on, on.copy(dailyTotalMinutes = 60, surfaceMinutes = mapOf(Surface.REELS to 45))))

    @Test
    fun `loosening costs a 10-minute wait, then a paragraph at 95 percent`() {
        val wanted = on.copy(enforcementEnabled = false)
        val answer = request(wanted) as SettingsChanger.Answer.Challenge
        assertEquals(wanted, answer.wanted)
        assertEquals(ChallengePrompt.Wait(600), answer.run.prompt)

        assertEquals(ChallengeStatus.InProgress, answer.run.submit(ChallengeAnswer.WaitDone, now.plusSeconds(599)).status)
        val typing = answer.run.submit(ChallengeAnswer.WaitDone, now.plus(Duration.ofMinutes(10)))
        val prompt = typing.run.prompt as ChallengePrompt.Typing
        assertEquals(0.95, prompt.minAccuracy, 0.0)
        assertEquals(TypingSentences.PARAGRAPH_SENTENCES, TypingSentences.ALL.count { it in prompt.text })
        assertEquals(
            ChallengeStatus.Passed,
            typing.run.submit(ChallengeAnswer.Typed(prompt.text), now.plus(Duration.ofMinutes(12))).status,
        )
    }

    @Test
    fun `loosening is refused once today's overrides are used up`() {
        assertEquals(
            SettingsChanger.Answer.Refused(SettingsChanger.RefusedBecause.OVERRIDES_USED_UP),
            request(on.copy(enforcementEnabled = false), usage = day.copy(overridesUsed = 5)),
        )
    }

    @Test
    fun `short timers shorten only the wait`() {
        val short = SettingsChanger(ChallengeFactory(TypingSentences(Random(1))), LadderSettings(), SettingsChanger.SHORT_WAIT_SECONDS)
        val answer = short.request(on, on.copy(surfaceMinutes = mapOf(Surface.REELS to 45)), day, now) as SettingsChanger.Answer.Challenge
        assertEquals(ChallengePrompt.Wait(5), answer.run.prompt)
    }

    // Settings

    @Test
    fun `any surface's limit becomes a budget`() {
        val budgets = AppSettings(surfaceMinutes = mapOf(Surface.REELS to 30, Surface.EXPLORE to 15)).budgets()
        assertEquals(mapOf(Surface.REELS to Duration.ofMinutes(30), Surface.EXPLORE to Duration.ofMinutes(15)), budgets.perSurface)
    }

    @Test
    fun `defaults are guarding on, 90 minutes a day and 15 of Reels`() {
        val defaults = AppSettings()
        assertTrue(defaults.enforcementEnabled)
        assertEquals(Duration.ofMinutes(90), defaults.budgets().dailyTotal)
        assertEquals(mapOf(Surface.REELS to Duration.ofMinutes(15)), defaults.budgets().perSurface)
    }

    @Test
    fun `limits outside their ranges are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { AppSettings(dailyTotalMinutes = 4) }
        assertThrows(IllegalArgumentException::class.java) { AppSettings(dailyTotalMinutes = 601) }
        assertThrows(IllegalArgumentException::class.java) { AppSettings(surfaceMinutes = mapOf(Surface.REELS to -1)) }
        assertThrows(IllegalArgumentException::class.java) { AppSettings(surfaceMinutes = mapOf(Surface.FEED to 601)) }
        AppSettings(surfaceMinutes = mapOf(Surface.REELS to 0)) // no Reels at all is allowed
    }
}
