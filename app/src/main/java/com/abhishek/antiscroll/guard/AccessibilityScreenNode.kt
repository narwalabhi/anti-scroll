package com.abhishek.antiscroll.guard

import android.view.accessibility.AccessibilityNodeInfo
import com.abhishek.antiscroll.rules.profile.ScreenNode

/**
 * Wraps an [AccessibilityNodeInfo] as a [ScreenNode]. Children are fetched once, on first use,
 * so the several searches of one surface check don't ask Android for the same node twice.
 * The node's text is never read.
 */
class AccessibilityScreenNode(private val node: AccessibilityNodeInfo) : ScreenNode {

    override val viewId: String? get() = node.viewIdResourceName
    override val contentDescription: String? get() = node.contentDescription?.toString()
    override val isSelected: Boolean get() = node.isSelected
    override val isVisibleToUser: Boolean get() = node.isVisibleToUser

    override val children: List<ScreenNode> by lazy {
        (0 until node.childCount).mapNotNull { i -> node.getChild(i)?.let(::AccessibilityScreenNode) }
    }
}
