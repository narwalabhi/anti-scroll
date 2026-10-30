package com.abhishek.antiscroll.present

/** Settings' Tracking health card (docs/UI.md, Settings). */
data class HealthUiState(val rows: List<Row>) {
    enum class Item { ACCESSIBILITY, OVERLAY, NOTIFICATIONS, BATTERY, USAGE_ACCESS }

    /** ON: granted. FIX: counting or guarding suffers without it. SET_UP: optional, a backup or extra. */
    enum class Status { ON, FIX, SET_UP }

    data class Row(val item: Item, val status: Status)

    val toFix: Int get() = rows.count { it.status == Status.FIX }
}

/**
 * Which grants need fixing. Accessibility is what counts at all, and an optimised battery lets the
 * phone stop it; the overlay, notifications and Usage access only feed the fallback watcher.
 */
class HealthPresenter {

    fun present(checks: HealthChecks) = HealthUiState(
        listOf(
            row(HealthUiState.Item.ACCESSIBILITY, checks.accessibility, required = true),
            row(HealthUiState.Item.OVERLAY, checks.overlay, required = false),
            row(HealthUiState.Item.NOTIFICATIONS, checks.notifications, required = false),
            row(HealthUiState.Item.BATTERY, checks.battery, required = true),
            row(HealthUiState.Item.USAGE_ACCESS, checks.usageAccess, required = false),
        ),
    )

    private fun row(item: HealthUiState.Item, granted: Boolean, required: Boolean) = HealthUiState.Row(
        item,
        when {
            granted -> HealthUiState.Status.ON
            required -> HealthUiState.Status.FIX
            else -> HealthUiState.Status.SET_UP
        },
    )
}
