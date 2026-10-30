package com.abhishek.antiscroll.rules.profile

/** Which app this is, and which of its surfaces is on screen. */
interface AppProfile {
    val packageName: String

    /** [tabPage] is the last tab-pager page the service saw, if any; a hint for when no tab is selected. */
    fun surfaceOf(root: ScreenNode, tabPage: Int? = null): Surface
}
