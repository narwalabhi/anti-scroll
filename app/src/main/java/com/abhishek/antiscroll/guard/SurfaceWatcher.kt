package com.abhishek.antiscroll.guard

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.abhishek.antiscroll.rules.profile.InstagramProfile
import com.abhishek.antiscroll.rules.profile.InstagramRules
import com.abhishek.antiscroll.rules.profile.Surface

/**
 * Works out which surface is on screen, through the [InstagramProfile], and reports changes and
 * Reels swipes. Checks are debounced: one, [CHECK_DELAY_MS] after the first event of a burst, so
 * the screen has settled; events during the wait don't push it back, so a playing video can't
 * delay it forever. In debug builds a screen read as Other is dumped (view IDs only).
 */
class SurfaceWatcher(
    private val service: AccessibilityService,
    private val profile: InstagramProfile,
    private val rules: InstagramRules,
    private val dumpUnknownScreens: Boolean,
    private val listener: Listener,
) {

    interface Listener {
        fun onSurfaceChanged(surface: Surface)
        fun onSwiped(surface: Surface)
    }

    private val handler = Handler(Looper.getMainLooper())
    private val check = Runnable { onCheck() }
    private var checkPending = false

    private var surface: Surface? = null
    private var tabPage: Int? = null
    private var reelsPage: Int? = null

    /** An event from the app while it's open. */
    fun onEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) onScrolled(event)
        checkSoon()
    }

    fun checkSoon() {
        if (checkPending) return
        checkPending = true
        handler.postDelayed(check, CHECK_DELAY_MS)
    }

    /** The app closed: forget the surface, so the next visit reports its own. */
    fun reset() {
        handler.removeCallbacks(check)
        checkPending = false
        surface = null
        reelsPage = null
    }

    fun stop() = handler.removeCallbacksAndMessages(null)

    private fun onScrolled(event: AccessibilityEvent) {
        when (event.source?.viewIdResourceName) {
            fullId(rules.tabPagerId) -> tabPage = event.fromIndex
            fullId(rules.reelsPagerId) -> {
                // A swipe is a page change while on Reels. The first page seen in a newly opened viewer
                // isn't one: reelsPage is cleared whenever the surface changes.
                val previous = reelsPage
                if (surface == Surface.REELS && previous != null && event.fromIndex != previous) {
                    listener.onSwiped(Surface.REELS)
                    Log.d(TAG, "reels swipe, page=${event.fromIndex}")
                }
                if (surface == Surface.REELS) reelsPage = event.fromIndex
            }
        }
    }

    private fun onCheck() {
        checkPending = false
        val root = service.rootInActiveWindow ?: return
        if (root.packageName?.toString() != profile.packageName) return
        val now = profile.surfaceOf(AccessibilityScreenNode(root), tabPage)
        if (now == surface) return
        surface = now
        reelsPage = null
        listener.onSurfaceChanged(now)
        Log.i(TAG, "surface=$now")
        if (now == Surface.OTHER && dumpUnknownScreens) TreeDump.log(root)
    }

    private fun fullId(id: String) = "${profile.packageName}:id/$id"

    private companion object {
        const val TAG = "AntiScroll"
        const val CHECK_DELAY_MS = 300L
    }
}
