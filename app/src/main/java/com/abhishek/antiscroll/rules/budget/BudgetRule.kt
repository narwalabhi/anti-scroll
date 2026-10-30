package com.abhishek.antiscroll.rules.budget

import com.abhishek.antiscroll.rules.engine.BlockReason
import com.abhishek.antiscroll.rules.profile.Surface
import com.abhishek.antiscroll.rules.usage.DayUsage
import com.abhishek.antiscroll.rules.usage.Session
import java.time.Instant

sealed interface BudgetVerdict {
    data object Allow : BudgetVerdict
    data class Block(val reason: BlockReason) : BudgetVerdict
}

/** One limit: should Instagram be blocked right now? [surface] is what's on screen, null until known. */
interface BudgetRule {
    fun check(usage: DayUsage, surface: Surface?, session: Session?, now: Instant): BudgetVerdict
}

/** Runs the rules in order; the first Block wins (Chain of Responsibility). */
fun List<BudgetRule>.check(usage: DayUsage, surface: Surface?, session: Session?, now: Instant): BudgetVerdict =
    asSequence().map { it.check(usage, surface, session, now) }.firstOrNull { it is BudgetVerdict.Block }
        ?: BudgetVerdict.Allow
