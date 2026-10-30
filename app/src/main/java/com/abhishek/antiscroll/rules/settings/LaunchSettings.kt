package com.abhishek.antiscroll.rules.settings

/**
 * Launch friction: the pause before the intention prompt and the choices it offers. The largest
 * minute choice is the most one session can ask for. Constants in AppGraph until settings are
 * editable (milestone 6).
 */
data class LaunchSettings(
    val pauseSeconds: Int = 5,
    val purposes: List<String> = listOf("Reply to messages", "Post or share", "Look something up", "Just browsing"),
    val minuteChoices: List<Int> = listOf(5, 10, 15, 20), // 20 added 2026-10-30; raise after friends' feedback
) {
    init {
        require(pauseSeconds >= 0) { "pauseSeconds must not be negative" }
        require(purposes.isNotEmpty()) { "purposes must not be empty" }
        require(minuteChoices.isNotEmpty() && minuteChoices.all { it > 0 }) { "minuteChoices must be positive" }
    }

    val maxMinutes: Int get() = minuteChoices.max()

    companion object {
        /** Debug "short timers": 1- and 2-minute sessions, so a session's end can be tried quickly. */
        fun shortForTesting() = LaunchSettings(minuteChoices = listOf(1, 2))
    }
}
