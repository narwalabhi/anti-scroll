package com.abhishek.antiscroll.system

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.abhishek.antiscroll.guard.InstagramGuardService
import com.abhishek.antiscroll.present.HealthChecks

/** What Android has granted AntiScroll, read fresh each time (the user can change any of it at any moment). */
class SystemChecks(context: Context) {

    private val context = context.applicationContext

    fun accessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val ours = ComponentName(context, InstagramGuardService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == ours }
    }

    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    fun notificationsEnabled(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun batteryUnrestricted(): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    fun usageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun snapshot() = HealthChecks(
        accessibility = accessibilityEnabled(),
        overlay = canDrawOverlays(),
        notifications = notificationsEnabled(),
        battery = batteryUnrestricted(),
        usageAccess = usageAccess(),
    )
}
