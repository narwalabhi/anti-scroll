package com.abhishek.antiscroll.rules.unlock

/**
 * How much of a typed text matches its target: 1.0 is exact. Based on edit distance (each
 * missing, extra or wrong character costs one) against the target's length, after trimming and
 * collapsing runs of spaces. Case and punctuation count.
 */
object TypingAccuracy {

    fun score(typed: String, target: String): Double {
        val a = normalize(typed)
        val b = normalize(target)
        if (b.isEmpty()) return if (a.isEmpty()) 1.0 else 0.0
        return (1.0 - editDistance(a, b).toDouble() / b.length).coerceAtLeast(0.0)
    }

    private fun normalize(text: String) = text.trim().replace(WHITESPACE, " ")

    /** Levenshtein distance, two rows at a time. */
    private fun editDistance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, substitution)
            }
            previous = current.also { current = previous }
        }
        return previous[b.length]
    }

    private val WHITESPACE = Regex("\\s+")
}
