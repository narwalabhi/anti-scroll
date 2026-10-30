package com.abhishek.antiscroll.rules.profile

/**
 * Tells Instagram's surfaces apart. Instagram keeps every tab loaded at once, so the selected
 * tab decides, not which views exist. See "Surface detection" in docs/LLD.md.
 */
class InstagramProfile(private val rules: InstagramRules) : AppProfile {

    override val packageName = PACKAGE_NAME

    override fun surfaceOf(root: ScreenNode, tabPage: Int?): Surface {
        // A Reel on screen is Reels wherever it was opened from, so a DM link is no way around the budget.
        if (isShowingReel(root)) return Surface.REELS
        val tabNodes = findTabs(root)
        return if (tabNodes.isNotEmpty()) {
            surfaceFromTabBar(tabNodes, tabPage)
        } else {
            surfaceWithoutTabBar(root)
        }
    }

    /**
     * The Reels viewer is on screen and the item in it isn't a known photo post (Explore shows photo
     * posts in the same viewer). Anything not known to be a photo counts as a Reel, so a new layout
     * can't open a way around the Reels limit; a wrongly covered photo is a config fix.
     */
    private fun isShowingReel(root: ScreenNode): Boolean {
        if (!root.has { it.viewId == fullId(rules.reelsPagerId) && it.isVisibleToUser }) return false
        val photoIds = rules.photoPostInReelsViewerIds.map(::fullId).toSet()
        return !root.has { it.viewId in photoIds && it.isVisibleToUser }
    }

    /**
     * Matches by view ID; only if no tab ID matches at all, falls back to the tab labels. Labels
     * count only when most of them are found, since a single "Profile" or "Home" button on another
     * screen (a Story, say) is not a tab bar.
     */
    private fun findTabs(root: ScreenNode): Map<InstagramRules.Tab, ScreenNode> {
        val byId = rules.tabs.mapNotNull { tab ->
            root.find { it.viewId == fullId(tab.id) }?.let { tab to it }
        }
        if (byId.isNotEmpty()) return byId.toMap()
        val byLabel = rules.tabs.mapNotNull { tab ->
            root.find { it.contentDescription == tab.label }?.let { tab to it }
        }
        return if (byLabel.size >= MIN_LABELS_FOR_TAB_BAR) byLabel.toMap() else emptyMap()
    }

    private fun surfaceFromTabBar(tabNodes: Map<InstagramRules.Tab, ScreenNode>, tabPage: Int?): Surface {
        // Mid-switch two tabs can both show as selected for a moment; the next re-check settles it.
        tabNodes.entries.firstOrNull { it.value.isSelected }?.let { return it.key.surface }
        // Instagram marks no tab selected on Home until a tab is tapped.
        return tabPage?.let { rules.tabs.getOrNull(it)?.surface } ?: Surface.FEED
    }

    private fun surfaceWithoutTabBar(root: ScreenNode): Surface = when {
        root.hasAny(rules.dmThreadIds) -> Surface.DMS
        root.hasAny(rules.storyViewerIds) -> Surface.STORIES
        else -> Surface.OTHER
    }

    private fun ScreenNode.hasAny(ids: List<String>): Boolean {
        val fullIds = ids.map(::fullId).toSet()
        return has { it.viewId in fullIds }
    }

    private fun fullId(id: String) = "$PACKAGE_NAME:id/$id"

    companion object {
        const val PACKAGE_NAME = "com.instagram.android"
        private const val MIN_LABELS_FOR_TAB_BAR = 3
    }
}
