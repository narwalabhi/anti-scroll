package com.abhishek.antiscroll.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.abhishek.antiscroll.AppGraph
import com.abhishek.antiscroll.R
import com.abhishek.antiscroll.rules.export.ExportReminders
import java.time.LocalDate

/**
 * Posts the "share your numbers" reminders when [ExportReminders] says one is due. Asked by the
 * heartbeat; looks at most once per calendar date. Shown kinds are remembered in SharedPreferences.
 */
class ExportReminderNotifier(private val context: Context, private val graph: AppGraph) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun checkOncePerDay() {
        val today = graph.runner.stats.date
        if (prefs.getString(KEY_CHECKED, null) == today.toString()) return
        prefs.edit().putString(KEY_CHECKED, today.toString()).apply()
        val first = listOfNotNull(graph.statsStore.firstDay(), graph.usageStore.firstDay()).minOrNull() ?: return
        val shown = ExportReminders.Kind.entries.filter { prefs.getBoolean(it.name, false) }.toSet()
        val kind = ExportReminders.due(first, firstGuardedDay(first, today), today, shown) ?: return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return // try again tomorrow
        post(kind)
        prefs.edit().putBoolean(kind.name, true).apply()
    }

    private fun firstGuardedDay(first: LocalDate, today: LocalDate): LocalDate? {
        return generateSequence(first) { it.plusDays(1) }
            .takeWhile { !it.isAfter(today) }
            .firstOrNull { date -> if (date == today) graph.runner.stats.guarded else graph.statsStore.load(date)?.guarded == true }
    }

    private fun post(kind: ExportReminders.Kind) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            context,
            kind.ordinal,
            Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_EXPORT).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val (title, text) = when (kind) {
            ExportReminders.Kind.BASELINE_DONE -> R.string.reminder_baseline_title to R.string.reminder_baseline_text
            ExportReminders.Kind.GUARDING_TWO_WEEKS -> R.string.reminder_guarding_title to R.string.reminder_guarding_text
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(title))
            .setContentText(context.getString(text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(text)))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_BASE + kind.ordinal, notification)
    }

    private companion object {
        const val PREFS = "export_reminders"
        const val KEY_CHECKED = "checked_on"
        const val CHANNEL_ID = "reminders"
        const val NOTIFICATION_BASE = 10
    }
}
