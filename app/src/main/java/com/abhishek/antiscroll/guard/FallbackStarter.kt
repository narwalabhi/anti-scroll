package com.abhishek.antiscroll.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.abhishek.antiscroll.system.SystemChecks

/**
 * Starts the [FallbackWatcherService] when it's needed and possible (the accessibility service off,
 * Usage access granted) and stops it otherwise. A start Android refuses from the background is
 * logged; the next app screen or boot tries again.
 */
class FallbackStarter(context: Context) {

    private val context = context.applicationContext
    private val checks = SystemChecks(context)

    fun sync() {
        if (checks.accessibilityEnabled()) stop() else start()
    }

    /** Called as the accessibility service is unbound: it's off now, whatever the setting reads yet. */
    fun start() {
        if (!checks.usageAccess()) return
        try {
            ContextCompat.startForegroundService(context, Intent(context, FallbackWatcherService::class.java))
        } catch (e: IllegalStateException) {
            Log.w(TAG, "fallback watcher not allowed to start now", e)
        }
    }

    fun stop() {
        context.stopService(Intent(context, FallbackWatcherService::class.java))
    }

    private companion object {
        const val TAG = "AntiScroll"
    }
}

/** After a reboot or an update, the accessibility service may be off: start watching if so. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            FallbackStarter(context).sync()
        }
    }
}
