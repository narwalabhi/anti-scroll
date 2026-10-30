package com.abhishek.antiscroll.rules.budget

import com.abhishek.antiscroll.rules.engine.BlockReason
import com.abhishek.antiscroll.rules.profile.Surface
import com.abhishek.antiscroll.rules.usage.DayUsage
import com.abhishek.antiscroll.rules.usage.Session
import java.time.Instant

/**
 * One surface (Reels, say): blocks while the user is on a surface whose limit in
 * [Budgets.perSurface] today's time has reached. Elsewhere in Instagram it allows.
 */
class SurfaceBudgetRule(private val budgets: Budgets) : BudgetRule {
    override fun check(usage: DayUsage, surface: Surface?, session: Session?, now: Instant): BudgetVerdict {
        if (surface == null) return BudgetVerdict.Allow
        val limit = budgets.perSurface[surface] ?: return BudgetVerdict.Allow
        val used = usage.millisBySurface[surface] ?: 0L
        return if (used >= limit.toMillis()) BudgetVerdict.Block(BlockReason.SurfaceBudget(surface)) else BudgetVerdict.Allow
    }
}
