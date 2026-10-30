package com.abhishek.antiscroll.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * Shows one window over everything. By default it's full screen, of the accessibility service's own
 * overlay type (so [context] must be the service and no extra permission is needed), and takes all
 * touches, so the app underneath can't be used while it's up. It takes key focus only when [show] is
 * asked to, for screens with a text field, and then resizes for the keyboard.
 *
 * [windowType] `TYPE_APPLICATION_OVERLAY` is for the fallback watcher (needs Display over other
 * apps); [pill] makes a small untouchable window at the top centre instead, for the session timer.
 */
class OverlayController(
    context: Context,
    private val windowType: Int = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
    private val pill: Boolean = false,
) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var current: View? = null

    val isShowing: Boolean get() = current != null

    /** Shows [view], replacing what's up; showing the view that's already up changes nothing. */
    fun show(view: View, focusable: Boolean = false) {
        if (current === view) return
        remove()
        try {
            windowManager.addView(view, layoutParams(focusable))
            current = view
        } catch (e: RuntimeException) {
            // A missing permission or a window token gone with the service: nothing to draw on.
            Log.w(TAG, "could not show overlay", e)
        }
    }

    fun remove() {
        val view = current ?: return
        current = null
        try {
            windowManager.removeView(view)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "overlay was already gone", e)
        }
    }

    private fun layoutParams(focusable: Boolean): WindowManager.LayoutParams {
        if (pill) {
            return WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL }
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.OPAQUE,
        ).apply {
            if (focusable) softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
    }

    private companion object {
        const val TAG = "AntiScroll"
    }
}
