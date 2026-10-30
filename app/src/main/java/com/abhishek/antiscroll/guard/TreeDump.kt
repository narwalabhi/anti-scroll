package com.abhishek.antiscroll.guard

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Debug only: logs a screen's view IDs, to add screens the profile doesn't know to
 * instagram_rules.json. Only nodes with an ID: ID, class, selected and visible state, position. The
 * content description is logged only for tab-like IDs, so no post or message text reaches Logcat.
 */
object TreeDump {

    fun log(root: AccessibilityNodeInfo) {
        Log.d(TAG, "---- dump start ----")
        dump(root, 0)
        Log.d(TAG, "---- dump end ----")
    }

    private fun dump(node: AccessibilityNodeInfo, depth: Int) {
        if (depth > MAX_DEPTH) return
        val id = node.viewIdResourceName
        if (id != null) {
            val desc = if ("tab" in id) " desc=${node.contentDescription}" else ""
            val className = node.className?.toString()?.substringAfterLast('.')
            val bounds = Rect().also(node::getBoundsInScreen).toShortString()
            Log.d(
                TAG,
                "${"  ".repeat(depth)}$id $className selected=${node.isSelected} " +
                    "visible=${node.isVisibleToUser} bounds=$bounds$desc",
            )
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { dump(it, depth + 1) }
        }
    }

    private const val TAG = "AntiScroll"
    private const val MAX_DEPTH = 40
}
