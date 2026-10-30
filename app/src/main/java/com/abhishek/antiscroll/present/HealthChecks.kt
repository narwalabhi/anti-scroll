package com.abhishek.antiscroll.present

/** What Android has granted, as read by the system layer; the presenters decide what needs fixing. */
data class HealthChecks(
    val accessibility: Boolean,
    val overlay: Boolean,
    val notifications: Boolean,
    val battery: Boolean,
    val usageAccess: Boolean,
)
