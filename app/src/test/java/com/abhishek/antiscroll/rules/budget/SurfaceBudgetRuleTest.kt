package com.abhishek.antiscroll.rules.budget

import com.abhishek.antiscroll.rules.engine.BlockReason
import com.abhishek.antiscroll.rules.profile.Surface
import com.abhishek.antiscroll.rules.usage.DayUsage
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class SurfaceBudgetRuleTest {

    private val budgets = Budgets(dailyTotal = Duration.ofMinutes(90), perSurface = mapOf(Surface.REELS to Duration.ofMinutes(30)))
    private val rule = SurfaceBudgetRule(budgets)
    private val now = Instant.parse("2026-10-29T20:00:00Z")

    private fun day(reelsMinutes: Long, feedMinutes: Long = 0) = DayUsage(
        LocalDate.parse("2026-10-29"),
        millisBySurface = mapOf(Surface.REELS to reelsMinutes * 60_000, Surface.FEED to feedMinutes * 60_000),
    )

    @Test
    fun `on Reels under the limit is allowed`() =
        assertEquals(BudgetVerdict.Allow, rule.check(day(29), Surface.REELS, null, now))

    @Test
    fun `on Reels at the limit blocks Reels`() =
        assertEquals(BudgetVerdict.Block(BlockReason.SurfaceBudget(Surface.REELS)), rule.check(day(30), Surface.REELS, null, now))

    @Test
    fun `elsewhere in Instagram is allowed even with Reels spent`() {
        assertEquals(BudgetVerdict.Allow, rule.check(day(45), Surface.FEED, null, now))
        assertEquals(BudgetVerdict.Allow, rule.check(day(45), Surface.DMS, null, now))
    }

    @Test
    fun `an unknown surface is allowed`() =
        assertEquals(BudgetVerdict.Allow, rule.check(day(45), null, null, now))

    @Test
    fun `a surface without a limit is never blocked`() =
        assertEquals(BudgetVerdict.Allow, rule.check(day(0, feedMinutes = 500), Surface.FEED, null, now))

    @Test
    fun `in the chain, a spent daily total wins over the surface`() {
        val chain = listOf(DailyTotalBudgetRule(budgets), SurfaceBudgetRule(budgets))
        assertEquals(BudgetVerdict.Block(BlockReason.DailyTotal), chain.check(day(45, feedMinutes = 50), Surface.REELS, null, now))
        assertEquals(BudgetVerdict.Block(BlockReason.SurfaceBudget(Surface.REELS)), chain.check(day(45), Surface.REELS, null, now))
    }
}
