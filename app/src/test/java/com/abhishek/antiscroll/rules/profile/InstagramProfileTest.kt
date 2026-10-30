package com.abhishek.antiscroll.rules.profile

import org.junit.Assert.assertEquals
import org.junit.Test

/** Screens are trimmed copies of the node dumps taken on the phone on 2026-10-29. */
class InstagramProfileTest {

    private val rules = InstagramRules(
        tabs = listOf(
            InstagramRules.Tab("feed_tab", "Home", Surface.FEED),
            InstagramRules.Tab("clips_tab", "Reels", Surface.REELS),
            InstagramRules.Tab("direct_tab", "Message", Surface.DMS),
            InstagramRules.Tab("search_tab", "Search and explore", Surface.EXPLORE),
            InstagramRules.Tab("profile_tab", "Profile", Surface.PROFILE),
        ),
        tabPagerId = "swipeable_tab_view_pager",
        reelsPagerId = "clips_viewer_view_pager",
        photoPostInReelsViewerIds = listOf("clips_carousel_viewpager", "clips_carousel_image_media_content"),
        dmThreadIds = listOf("message_list"),
        storyViewerIds = listOf("reel_viewer_root"),
    )
    private val profile = InstagramProfile(rules)

    /**
     * Main screen: every tab's content is loaded at once, as on the phone, but only the
     * selected tab's content is visible. Plus the tab bar.
     */
    private fun mainScreen(selectedTab: String?, ids: Boolean = true): ScreenNode {
        fun content(tab: String, id: String, vararg children: ScreenNode) =
            if (tab == selectedTab) node(id, null, false, *children) else hidden(id, *children)
        return group(
            node(
                "swipeable_tab_view_pager", null, false,
                content("feed_tab", "row_feed_profile_header"),
                content("clips_tab", "clips_viewer_view_pager", node("clips_media_component")),
                content("direct_tab", "inbox_refreshable_thread_list_recyclerview"),
                content("search_tab", "recycler_view", node("grid_card_layout_container")),
            ),
            tab("feed_tab", "Home", selectedTab, ids),
            tab("clips_tab", "Reels", selectedTab, ids),
            tab("direct_tab", "Message", selectedTab, ids, node("notification")),
            tab("search_tab", "Search and explore", selectedTab, ids),
            tab("profile_tab", "Profile", selectedTab, ids),
        )
    }

    private fun tab(id: String, label: String, selectedTab: String?, ids: Boolean, vararg children: ScreenNode) =
        node(if (ids) id else "renamed_$id", label, id == selectedTab, *children)

    @Test
    fun `selected tab decides the surface`() {
        assertEquals(Surface.FEED, profile.surfaceOf(mainScreen("feed_tab")))
        assertEquals(Surface.REELS, profile.surfaceOf(mainScreen("clips_tab")))
        assertEquals(Surface.DMS, profile.surfaceOf(mainScreen("direct_tab")))
        assertEquals(Surface.EXPLORE, profile.surfaceOf(mainScreen("search_tab")))
        assertEquals(Surface.PROFILE, profile.surfaceOf(mainScreen("profile_tab")))
    }

    @Test
    fun `hidden Reels tab behind another tab does not count as Reels`() {
        assertEquals(Surface.EXPLORE, profile.surfaceOf(mainScreen("search_tab")))
    }

    @Test
    fun `selected tab wins over the tab pager hint`() {
        assertEquals(Surface.DMS, profile.surfaceOf(mainScreen("direct_tab"), tabPage = 0))
    }

    @Test
    fun `two tabs selected mid-switch takes the first in tab-bar order`() {
        val screen = group(
            node("feed_tab", "Home", true),
            node("clips_tab", "Reels"),
            node("direct_tab", "Message", true),
        )
        assertEquals(Surface.FEED, profile.surfaceOf(screen))
    }

    @Test
    fun `no tab selected and no hint is the feed`() {
        // Instagram opens on Home with no tab marked selected.
        assertEquals(Surface.FEED, profile.surfaceOf(mainScreen(selectedTab = null)))
    }

    @Test
    fun `no tab selected uses the tab pager page`() {
        assertEquals(Surface.DMS, profile.surfaceOf(mainScreen(selectedTab = null), tabPage = 2))
        assertEquals(Surface.PROFILE, profile.surfaceOf(mainScreen(selectedTab = null), tabPage = 4))
    }

    @Test
    fun `tab pager page out of range falls back to the feed`() {
        assertEquals(Surface.FEED, profile.surfaceOf(mainScreen(selectedTab = null), tabPage = 7))
    }

    @Test
    fun `tabs are found by label when Instagram renames their IDs`() {
        assertEquals(Surface.PROFILE, profile.surfaceOf(mainScreen("profile_tab", ids = false)))
    }

    @Test
    fun `DM thread opened on top is DMs`() {
        val thread = group(
            node(
                "layout_container_parent", null, false,
                node(
                    "thread_fragment_container", null, false,
                    node("action_bar_button_back"),
                    node("message_list", null, false, node("message_content")),
                ),
            ),
        )
        assertEquals(Surface.DMS, profile.surfaceOf(thread))
    }

    @Test
    fun `Reel opened from a DM is Reels`() {
        val reelFromDm = group(
            node(
                "layout_container_parent", null, false,
                node("gesture_manager", null, false, node("clips_viewer_view_pager")),
                node("reply_bar_container_scroll_view", null, false, node("reply_bar_edittext")),
            ),
        )
        assertEquals(Surface.REELS, profile.surfaceOf(reelFromDm))
    }

    @Test
    fun `Reel opened from Explore is Reels`() {
        val reelFromExplore = group(
            node(
                "swipeable_tab_view_pager", null, false,
                hidden("list"),
                node("gesture_manager", null, false, node("clips_viewer_view_pager")),
                node("comment_composer_text_view"),
            ),
        )
        assertEquals(Surface.REELS, profile.surfaceOf(reelFromExplore))
    }

    /** Explore's full-screen viewer, from the phone dumps of 2026-10-29: the same pager for posts and Reels. */
    private fun exploreViewer(vararg item: ScreenNode) = group(
        node(
            "swipeable_tab_view_pager", null, false,
            node(
                "gesture_manager", null, false,
                node("clips_viewer_view_pager", null, false, node("clips_media_component", null, false, *item)),
            ),
        ),
    )

    @Test
    fun `a carousel post in Explore's viewer is not Reels`() {
        val carousel = exploreViewer(
            node("clips_carousel_viewpager", null, false, node("clips_carousel_image_media_content")),
            node("clips_item_overlay_component"),
        )
        assertEquals(Surface.OTHER, profile.surfaceOf(carousel))
    }

    @Test
    fun `a Reel in the same viewer is Reels`() {
        val reel = exploreViewer(
            node("clips_single_media_component", null, false, node("clips_video_container")),
            node("scrubber"),
        )
        assertEquals(Surface.REELS, profile.surfaceOf(reel))
    }

    @Test
    fun `a hidden carousel next to the Reel on screen doesn't stop it counting`() {
        val reelWithCarouselBelow = exploreViewer(
            node("clips_single_media_component", null, false, node("clips_video_container")),
            hidden("clips_carousel_viewpager", hidden("clips_carousel_image_media_content")),
        )
        assertEquals(Surface.REELS, profile.surfaceOf(reelWithCarouselBelow))
    }

    @Test
    fun `an item of unknown layout in the viewer still counts as Reels`() =
        assertEquals(Surface.REELS, profile.surfaceOf(exploreViewer(node("some_future_media_component"))))

    @Test
    fun `Reel opened from a profile grid is Reels`() {
        val reelFromProfile = group(
            node(
                "swipeable_tab_view_pager", null, false,
                node("gesture_manager", null, false, node("clips_viewer_view_pager")),
                hidden("recycler_view", hidden("grid_card_layout_container")),
            ),
        )
        assertEquals(Surface.REELS, profile.surfaceOf(reelFromProfile))
    }

    @Test
    fun `story viewer is Stories`() {
        val story = group(
            node(
                "swipeable_tab_view_pager", null, false,
                node("reel_viewer_root", null, false, node("reel_viewer_header"), node("toolbar_like_button")),
            ),
        )
        assertEquals(Surface.STORIES, profile.surfaceOf(story))
    }

    @Test
    fun `a stray tab label on a Story is not a tab bar`() {
        // Seen on the phone: the Story screen has no tab IDs; with the Reels page as the last hint,
        // a single label match used to be read as a tab bar and gave Reels.
        val story = group(
            node(
                "swipeable_tab_view_pager", null, false,
                node("reel_viewer_root", null, false, node("reel_viewer_header"), node("toolbar_like_button", "Profile")),
                hidden("gesture_manager", hidden("clips_viewer_view_pager")),
            ),
        )
        assertEquals(Surface.STORIES, profile.surfaceOf(story, tabPage = 1))
    }

    @Test
    fun `activity screen is Other`() {
        val activity = group(
            node("swipeable_tab_view_pager", null, false, node("activity_feed_root")),
            node("action_bar_new_title_container", null, false, node("action_bar_button_back")),
        )
        assertEquals(Surface.OTHER, profile.surfaceOf(activity))
    }

    @Test
    fun `comments sheet is Other`() {
        val comments = group(
            node(
                "bottom_sheet_container", null, false,
                node("main_list_view", null, false, node("sticky_header_list")),
                node("comment_composer_parent_updated"),
            ),
        )
        assertEquals(Surface.OTHER, profile.surfaceOf(comments))
    }

    @Test
    fun `IDs from another package do not match`() {
        val other = FakeScreenNode(viewId = "com.example:id/clips_viewer_view_pager")
        assertEquals(Surface.OTHER, profile.surfaceOf(group(other)))
    }

    @Test
    fun `search stops at the depth limit`() {
        var deep: ScreenNode = node("message_list")
        repeat(100) { deep = group(deep) }
        assertEquals(Surface.OTHER, profile.surfaceOf(deep))
    }
}
