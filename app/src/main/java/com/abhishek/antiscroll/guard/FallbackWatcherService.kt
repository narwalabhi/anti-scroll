package com.abhishek.antiscroll.guard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.abhishek.antiscroll.AntiScrollApp
import com.abhishek.antiscroll.R
import com.abhishek.antiscroll.overlay.OverlayController
import com.abhishek.antiscroll.overlay.fitSystemBars
import com.abhishek.antiscroll.overlay.overlayContext
import com.abhishek.antiscroll.rules.health.AppEvent
import com.abhishek.antiscroll.rules.health.FallbackCover
import com.abhishek.antiscroll.rules.health.ForegroundApp
import com.abhishek.antiscroll.system.SystemChecks
import com.abhishek.antiscroll.system.SystemSettings

/**
 * Covers Instagram while the accessibility service is off (docs/LLD.md, Fallback watcher). A
 * foreground service: it reads Android's usage events every [POLL_MS] while the screen is on (there
 * is no other way to see an app come to front without accessibility), and stops itself as soon as
 * the accessibility service is enabled again. Package names only; nothing is counted.
 */
class FallbackWatcherService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var usageStats: UsageStatsManager
    private lateinit var checks: SystemChecks
    private lateinit var cover: FallbackCover
    private lateinit var overlay: OverlayController
    private var coverView: View? = null
    private var state = FallbackCover.State()
    private var foreground: String? = null
    private var readUntil = 0L
    private val graph get() = (application as AntiScrollApp).graph

    private val poll = object : Runnable {
        override fun run() {
            readEvents()
            handler.postDelayed(this, POLL_MS)
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> stopPolling()
                Intent.ACTION_SCREEN_ON -> startPolling()
            }
        }
    }

    /** The accessibility service coming back on ends the watch at once, no polling needed. */
    private val servicesObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            if (checks.accessibilityEnabled()) stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        usageStats = getSystemService(UsageStatsManager::class.java)
        checks = SystemChecks(this)
        cover = FallbackCover(graph.instagramProfile.packageName)
        overlay = OverlayController(this, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        startInForeground()
        Log.i(TAG, "fallback watcher started")
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            false,
            servicesObserver,
        )
        // Look back a little, so an Instagram already in front is seen at once.
        readUntil = System.currentTimeMillis() - LOOK_BACK_MS
        if (getSystemService(PowerManager::class.java).isInteractive) startPolling()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (checks.accessibilityEnabled()) stopSelf()
        return START_STICKY
    }

    override fun onDestroy() {
        stopPolling()
        unregisterReceiver(screenReceiver)
        contentResolver.unregisterContentObserver(servicesObserver)
        hideCover()
        Log.i(TAG, "fallback watcher stopped")
        super.onDestroy()
    }

    private fun startPolling() {
        handler.removeCallbacks(poll)
        handler.post(poll)
    }

    private fun stopPolling() = handler.removeCallbacks(poll)

    /** Folds the usage events since the last read into the app in front, then covers or uncovers. */
    private fun readEvents() {
        val now = System.currentTimeMillis()
        val events = usageStats.queryEvents(readUntil, now)
        readUntil = now
        val appEvents = mutableListOf<AppEvent>()
        val event = UsageEvents.Event()
        while (events.getNextEvent(event)) {
            val kind = when (event.eventType) {
                RESUMED -> AppEvent.Kind.RESUMED
                PAUSED -> AppEvent.Kind.PAUSED
                else -> null
            } ?: continue
            appEvents += AppEvent(event.packageName, kind)
        }
        if (appEvents.isEmpty()) return
        foreground = ForegroundApp.fold(foreground, appEvents)
        state = cover.onForeground(state, foreground)
        apply()
    }

    private fun apply() {
        if (cover.showsCover(state, graph.enforcementEnabled)) showCover() else hideCover()
    }

    private fun showCover() {
        if (!checks.canDrawOverlays()) return // the notification still says what's wrong
        val view = coverView ?: LayoutInflater.from(overlayContext()).inflate(R.layout.overlay_fallback, null).also { view ->
            view.fitSystemBars()
            view.findViewById<View>(R.id.fallbackTurnOn).setOnClickListener {
                startActivity(SystemSettings.accessibility().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            view.findViewById<View>(R.id.fallbackClose).setOnClickListener {
                startActivity(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            view.findViewById<View>(R.id.fallbackUncounted).setOnClickListener {
                state = cover.useUncounted(state)
                apply()
            }
            coverView = view
        }
        view.findViewById<View>(R.id.fallbackUncounted).isVisible = cover.offersUncounted(graph.enforcementEnabled)
        overlay.show(view)
    }

    private fun hideCover() = overlay.remove()

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.tracking_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this,
            0,
            SystemSettings.accessibility().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.fallback_notification_title))
            .setContentText(getString(R.string.fallback_notification_text))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    companion object {
        private const val TAG = "AntiScroll"
        private const val CHANNEL_ID = "tracking"
        private const val NOTIFICATION_ID = 1
        private const val POLL_MS = 1_500L
        private const val LOOK_BACK_MS = 10 * 60 * 1_000L

        // ACTIVITY_RESUMED and ACTIVITY_PAUSED (API 29) share these values with the older names.
        private const val RESUMED = 1
        private const val PAUSED = 2
    }
}
