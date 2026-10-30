package com.abhishek.antiscroll.rules.budget

import com.abhishek.antiscroll.rules.engine.BlockReason
import com.abhishek.antiscroll.rules.profile.Surface
import com.abhishek.antiscroll.rules.usage.DayUsage
import com.abhishek.antiscroll.rules.usage.Session
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DailyTotalBudgetRuleTest {

    private val budgets = Budgets(dailyTotal = Duration.ofMinutes(90), perSurface = mapOf(Surface.REELS to Duration.ofMinutes(30)))
    private val rule = DailyTotalBudgetRule(budgets)
    private val now = Instant.parse("2026-10-29T20:00:00Z")

    /** A day with [minutes] of Instagram, split across surfaces as a real day would be. */
    private fun dayWith(minutes: Long, plusMillis: Long = 0): DayUsage {
        val millis = minutes * 60_000 + plusMillis
        return DayUsage(
            LocalDate.parse("2026-10-29"),
            millisBySurface = mapOf(Surface.REELS to millis / 2, Surface.FEED to millis - millis / 2),
        )
    }

    @Test
    fun `under the limit is allowed`() =
        assertEquals(BudgetVerdict.Allow, rule.check(dayWith(89, plusMillis = 59_999), null, null, now))

    @Test
    fun `exactly at the limit blocks`() =
        assertEquals(BudgetVerdict.Block(BlockReason.DailyTotal), rule.check(dayWith(90), null, null, now))

    @Test
    fun `over the limit blocks`() =
        assertEquals(BudgetVerdict.Block(BlockReason.DailyTotal), rule.check(dayWith(120), null, null, now))

    @Test
    fun `every surface counts, DMs included`() {
        val dmsOnly = DayUsage(LocalDate.parse("2026-10-29"), millisBySurface = mapOf(Surface.DMS to 90 * 60_000L))
        assertEquals(BudgetVerdict.Block(BlockReason.DailyTotal), rule.check(dmsOnly, null, null, now))
    }

    @Test
    fun `the rule looks only at the counters, not the session`() {
        val overrideSession = Session("Override", now.plusSeconds(600), byOverride = true)
        assertEquals(BudgetVerdict.Block(BlockReason.DailyTotal), rule.check(dayWith(90), null, overrideSession, now))
    }

    @Test
    fun `what's left today`() {
        assertEquals(Duration.ofMinutes(10), budgets.dailyLeft(dayWith(80)))
        assertEquals(Duration.ZERO, budgets.dailyLeft(dayWith(95)))
        assertEquals(Duration.ofMinutes(90), budgets.dailyLeft(DayUsage(LocalDate.parse("2026-10-29"))))
    }

    @Test
    fun `the first rule that blocks wins`() {
        val calls = mutableListOf<String>()
        fun rule(name: String, verdict: BudgetVerdict) = object : BudgetRule {
            override fun check(usage: DayUsage, surface: Surface?, session: Session?, now: Instant): BudgetVerdict {
                calls += name
                return verdict
            }
        }
        val chain = listOf(
            rule("allow", BudgetVerdict.Allow),
            rule("time up", BudgetVerdict.Block(BlockReason.SessionTimeUp)),
            rule("daily", BudgetVerdict.Block(BlockReason.DailyTotal)),
        )
        assertEquals(BudgetVerdict.Block(BlockReason.SessionTimeUp), chain.check(dayWith(0), null, null, now))
        assertEquals(listOf("allow", "time up"), calls)
        assertEquals(BudgetVerdict.Allow, emptyList<BudgetRule>().check(dayWith(500), null, null, now))
    }

    @Test
    fun `short timers make a 5-minute day and 1-minute surface limits`() {
        val short = Budgets.shortForTesting(budgets)
        assertEquals(Duration.ofMinutes(5), short.dailyTotal)
        assertEquals(mapOf(Surface.REELS to Duration.ofMinutes(1)), short.perSurface)
    }

    @Test
    fun `a zero daily total is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { Budgets(dailyTotal = Duration.ZERO) }
    }
}
