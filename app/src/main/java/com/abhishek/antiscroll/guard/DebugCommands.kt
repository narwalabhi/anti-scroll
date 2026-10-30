package com.abhishek.antiscroll.guard

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.core.content.ContextCompat
import com.abhishek.antiscroll.AppGraph
import com.abhishek.antiscroll.R
import com.abhishek.antiscroll.data.SurfaceMinutesCodec
import com.abhishek.antiscroll.rules.settings.ChangeSource
import com.abhishek.antiscroll.overlay.OverlayController

/**
 * Debug builds only: commands from a computer, for testing while the baseline week runs.
 * - `adb shell am broadcast -a com.abhishek.antiscroll.debug.ENFORCEMENT --ez on true` (or false)
 * - `adb shell am broadcast -a com.abhishek.antiscroll.debug.LIMITS --ei daily 600 --es surfaces REELS=46`
 *   sets limits directly, skipping the loosening friction, for testing a limit on a day it's spent
 * - `adb shell am broadcast -a com.abhishek.antiscroll.debug.KEYBOARD_TEST` (a text field in an overlay)
 * - `adb shell am broadcast -a com.abhishek.antiscroll.debug.CLEAR_TIMERS`: ends today's cooldown and
 *   break now (the override count stays), to keep testing without waiting
 * - `adb shell am broadcast -a com.abhishek.antiscroll.debug.DUMP`: the screen's view IDs now, for a
 *   screen the profile reads wrongly (view IDs only, see [TreeDump])
 */
class DebugCommands(private val service: AccessibilityService, private val graph: AppGraph) {

    private val keyboardTestOverlay = OverlayController(service)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_ENFORCEMENT -> {
                    graph.enforcementEnabled = intent.getBooleanExtra("on", false)
                    Log.i(TAG, "enforcement ${if (graph.enforcementEnabled) "on" else "off"}")
                }
                ACTION_LIMITS -> setLimits(intent)
                ACTION_KEYBOARD_TEST -> showKeyboardTest()
                ACTION_DUMP -> service.rootInActiveWindow?.let(TreeDump::log)
                ACTION_CLEAR_TIMERS -> {
                    graph.runner.clearCooldownAndBreak()
                    Log.i(TAG, "cooldown and break cleared")
                }
            }
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(ACTION_ENFORCEMENT)
            addAction(ACTION_LIMITS)
            addAction(ACTION_KEYBOARD_TEST)
            addAction(ACTION_DUMP)
            addAction(ACTION_CLEAR_TIMERS)
        }
        // Exported so adb can reach it; only ever started in debug builds.
        ContextCompat.registerReceiver(service, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    fun stop() {
        service.unregisterReceiver(receiver)
        keyboardTestOverlay.remove()
    }

    private fun setLimits(intent: Intent) {
        try {
            graph.applySettings(
                source = ChangeSource.DEBUG,
                new = graph.settings.copy(
                    dailyTotalMinutes = intent.getIntExtra("daily", graph.settings.dailyTotalMinutes),
                    surfaceMinutes = intent.getStringExtra("surfaces")?.let(SurfaceMinutesCodec::decode)
                        ?: graph.settings.surfaceMinutes,
                ),
            )
            Log.i(TAG, "limits: ${graph.settings.dailyTotalMinutes} min a day, ${graph.settings.surfaceMinutes}")
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "limits not changed: ${e.message}")
        }
    }

    /** A text field in an accessibility overlay; logs how many characters were typed. */
    private fun showKeyboardTest() {
        val view = LayoutInflater.from(ContextThemeWrapper(service, R.style.Theme_AntiScroll))
            .inflate(R.layout.overlay_keyboard_test, null)
        val field = view.findViewById<EditText>(R.id.keyboardTestField)
        view.findViewById<View>(R.id.keyboardTestClose).setOnClickListener {
            Log.i(TAG, "keyboard test: ${field.text.length} characters typed")
            keyboardTestOverlay.remove()
        }
        keyboardTestOverlay.show(view, focusable = true)
        field.requestFocus()
        field.post { service.getSystemService(InputMethodManager::class.java).showSoftInput(field, 0) }
    }

    private companion object {
        const val TAG = "AntiScroll"
        const val ACTION_ENFORCEMENT = "com.abhishek.antiscroll.debug.ENFORCEMENT"
        const val ACTION_LIMITS = "com.abhishek.antiscroll.debug.LIMITS"
        const val ACTION_KEYBOARD_TEST = "com.abhishek.antiscroll.debug.KEYBOARD_TEST"
        const val ACTION_DUMP = "com.abhishek.antiscroll.debug.DUMP"
        const val ACTION_CLEAR_TIMERS = "com.abhishek.antiscroll.debug.CLEAR_TIMERS"
    }
}
