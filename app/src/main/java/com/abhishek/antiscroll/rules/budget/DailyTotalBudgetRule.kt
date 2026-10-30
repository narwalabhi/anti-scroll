package com.abhishek.antiscroll.rules.budget

import com.abhishek.antiscroll.rules.engine.BlockReason
import com.abhishek.antiscroll.rules.profile.Surface
import com.abhishek.antiscroll.rules.usage.DayUsage
import com.abhishek.antiscroll.rules.usage.Session
import java.time.Instant

/**
 * All of Instagram, every surface: blocks once today's total reaches [Budgets.dailyTotal].
 * Override sessions are the engine's business; this rule only looks at the counters.
 */
class DailyTotalBudgetRule(private val budgets: Budgets) : BudgetRule {
    override fun check(usage: DayUsage, surface: Surface?, session: Session?, now: Instant): BudgetVerdict =
        if (budgets.dailyLeft(usage).isZero) BudgetVerdict.Block(BlockReason.DailyTotal) else BudgetVerdict.Allow
}
