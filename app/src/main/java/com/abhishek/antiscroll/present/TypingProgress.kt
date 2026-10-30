package com.abhishek.antiscroll.present

import com.abhishek.antiscroll.rules.unlock.TypingAccuracy

/**
 * The typing screen's live feedback (docs/UI.md, Type to continue): each character of the target
 * typed right, typed wrong, next, or still to type; live accuracy; and whether Continue may be
 * pressed. Only feedback: the engine's run still judges the submitted text.
 */
data class TypingProgress(val marks: List<Mark>, val typedCount: Int, val accuracyPercent: Int, val complete: Boolean, val canContinue: Boolean) {

    enum class Mark { RIGHT, WRONG, NEXT, TODO }

    companion object {
        fun of(target: String, typed: String, minAccuracy: Double): TypingProgress {
            val marks = target.indices.map { i ->
                when {
                    i < typed.length -> if (typed[i] == target[i]) Mark.RIGHT else Mark.WRONG
                    i == typed.length -> Mark.NEXT
                    else -> Mark.TODO
                }
            }
            val complete = typed.trimEnd().length >= target.trimEnd().length
            // So far: against as much of the target as has been typed.
            val soFar = if (typed.isEmpty()) 1.0 else TypingAccuracy.score(typed, target.take(typed.length))
            val final = TypingAccuracy.score(typed, target)
            return TypingProgress(
                marks = marks,
                typedCount = minOf(typed.length, target.length),
                accuracyPercent = ((if (complete) final else soFar) * 100).toInt(),
                complete = complete,
                canContinue = complete && final >= minAccuracy,
            )
        }
    }
}
