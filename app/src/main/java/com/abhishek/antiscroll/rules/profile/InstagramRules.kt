package com.abhishek.antiscroll.rules.profile

/**
 * Instagram's view IDs and labels, parsed from instagram_rules.json. IDs are short names
 * (`clips_tab`), without the `com.instagram.android:id/` prefix.
 */
data class InstagramRules(
    /** In tab-bar order, which is also the tab pager's page order. */
    val tabs: List<Tab>,
    val tabPagerId: String,
    /** The Reels viewer, the same wherever a Reel is opened from. */
    val reelsPagerId: String,
    /**
     * Views of a photo post shown inside the Reels viewer (Explore opens posts there too). With one
     * of these on screen, the viewer isn't counted as Reels. Unknown layouts still count as Reels.
     */
    val photoPostInReelsViewerIds: List<String> = emptyList(),
    val dmThreadIds: List<String>,
    val storyViewerIds: List<String>,
) {
    data class Tab(val id: String, val label: String, val surface: Surface)
}
