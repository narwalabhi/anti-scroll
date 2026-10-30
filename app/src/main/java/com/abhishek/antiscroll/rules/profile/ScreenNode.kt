package com.abhishek.antiscroll.rules.profile

/**
 * Read-only view of one node in an app's on-screen tree. There is no text property on purpose:
 * screen text never enters the rules.
 */
interface ScreenNode {
    val viewId: String?
    val contentDescription: String?
    val isSelected: Boolean

    /** False for views Instagram keeps loaded behind the screen you're on, such as other tabs. */
    val isVisibleToUser: Boolean
    val children: List<ScreenNode>
}

/** Depth-first search, so the tab bar is found wherever Instagram nests it. */
fun ScreenNode.find(maxDepth: Int = MAX_SEARCH_DEPTH, predicate: (ScreenNode) -> Boolean): ScreenNode? {
    if (predicate(this)) return this
    if (maxDepth == 0) return null
    for (child in children) {
        child.find(maxDepth - 1, predicate)?.let { return it }
    }
    return null
}

fun ScreenNode.has(predicate: (ScreenNode) -> Boolean): Boolean = find(predicate = predicate) != null

/** Instagram's trees seen so far nest about 25 levels deep. */
private const val MAX_SEARCH_DEPTH = 60
