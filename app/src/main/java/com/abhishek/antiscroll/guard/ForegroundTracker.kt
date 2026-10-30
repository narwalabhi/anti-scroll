package com.abhishek.antiscroll.guard

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.abhishek.antiscroll.rules.time.Clock
import java.time.Instant

/**
 * Decides when the watched app opens and closes (docs/LLD.md, Foreground detection): its window
 * coming to front, confirmed half a second later; another app's window, except those that sit on
 * top of it (us, System UI, the keyboard); the screen turning off, and on or unlocked. While the app
 * is open, content-changed events are on, to keep Android's cached screen tree fresh.
 */
class ForegroundTracker(
    private val service: AccessibilityService,
    private val watchedPackage: String,
    private val clock: Clock,
    private val isOpen: () -> Boolean,
    private val listener: Listener,
) {

    interface Listener {
        fun onOpened(at: Instant)
        fun onClosed()
        fun onScreenOff()
    }

    private val handler = Handler(Looper.getMainLooper())
    private val confirmOpen = Runnable { onConfirmOpen() }
    private var pendingOpenAt: Instant? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    close()
                    listener.onScreenOff()
                }
                // The screen may come back on with the app still in front; no window event says so.
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> openIfInFront()
            }
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(service, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        openIfInFront()
    }

    fun stop() {
        close()
        service.unregisterReceiver(screenReceiver)
        handler.removeCallbacksAndMessages(null)
    }

    /**
     * An event from the watched app. Returns true when the app is open, so the event is also worth
     * a surface check. The app keeps sending events, and stays the "active window", for a moment
     * after another app comes up; so only its window coming to front, or a tap, proposes an open
     * (redraws during the Home animation did, and caused extra opens), confirmed if it's still in
     * front [OPEN_CONFIRM_MS] later, dated back to the proposal.
     */
    fun onWatchedAppEvent(event: AccessibilityEvent): Boolean {
        if (isOpen()) return true
        val canOpen = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED
        if (canOpen && pendingOpenAt == null) {
            pendingOpenAt = clock.now()
            handler.postDelayed(confirmOpen, OPEN_CONFIRM_MS)
        }
        return false
    }

    /** Another app's event: package name only, never its content. */
    fun onOtherAppEvent(packageName: String, event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || !isOpen()) return
        if (isOnTopOfWatchedApp(packageName)) {
            Log.d(TAG, "window on top, still open: $packageName")
        } else {
            Log.d(TAG, "window: $packageName")
            close()
        }
    }

    /** For screen on, unlock and service start, where no closing animation can mislead. */
    private fun openIfInFront() {
        if (isInFront() && !isOpen()) open(clock.now())
    }

    private fun onConfirmOpen() {
        val proposedAt = pendingOpenAt ?: return
        pendingOpenAt = null
        if (!isOpen() && isInFront()) open(proposedAt)
    }

    private fun open(at: Instant) {
        handler.removeCallbacks(confirmOpen)
        pendingOpenAt = null
        listener.onOpened(at)
        setContentEvents(true)
    }

    private fun close() {
        if (!isOpen()) return
        listener.onClosed()
        setContentEvents(false)
    }

    private fun isInFront(): Boolean = service.rootInActiveWindow?.packageName?.toString() == watchedPackage

    /** Windows that sit on top of the watched app without leaving it: us, the shade, the keyboard. */
    private fun isOnTopOfWatchedApp(packageName: String): Boolean =
        packageName == service.packageName || packageName == SYSTEM_UI || packageName == currentKeyboardPackage()

    private fun currentKeyboardPackage(): String? =
        Settings.Secure.getString(service.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')

    /** Content changes keep the cached tree fresh, but every app sends them: on only while open. */
    private fun setContentEvents(on: Boolean) {
        val info = service.serviceInfo ?: return
        val types = if (on) {
            info.eventTypes or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        } else {
            info.eventTypes and AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED.inv()
        }
        if (types == info.eventTypes) return
        info.eventTypes = types
        service.serviceInfo = info
    }

    private companion object {
        const val TAG = "AntiScroll"
        const val SYSTEM_UI = "com.android.systemui"
        const val OPEN_CONFIRM_MS = 500L
    }
}
