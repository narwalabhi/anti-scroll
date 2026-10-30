package com.abhishek.antiscroll.ui

import android.content.Context
import android.util.Log
import com.abhishek.antiscroll.AppGraph
import com.abhishek.antiscroll.data.BackupFile
import com.abhishek.antiscroll.data.CloudDocuments
import com.abhishek.antiscroll.rules.settings.AppSettings
import com.abhishek.antiscroll.rules.settings.ChangeSource
import com.abhishek.antiscroll.rules.sync.CloudStore
import com.abhishek.antiscroll.rules.sync.SyncPlanner
import com.abhishek.antiscroll.rules.usage.DayStats
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sends the daily numbers to the developer's copy (docs/SPEC.md, Cloud copy): at most every
 * [INTERVAL] (5 minutes), only the days that changed since last sent, plus the install's facts. Runs off the
 * main thread; remembers what was sent in SharedPreferences.
 */
class CloudSync(context: Context, private val graph: AppGraph, private val cloud: CloudStore) {

    private val context = context.applicationContext
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val io = Executors.newSingleThreadExecutor()

    fun syncIfDue() {
        // Unsent settings changes don't wait for the 30-minute cycle.
        if (hasPending() && graph.accountSignIn.decided) io.execute { cloud.signIn { ok -> if (ok) io.execute { sendPendingChanges() } } }
        val last = prefs.getLong(KEY_LAST, 0L).takeIf { it > 0 }?.let(Instant::ofEpochMilli)
        val now = graph.clock.now()
        if (last != null && Duration.between(last, now) < INTERVAL) return
        prefs.edit().putLong(KEY_LAST, now.toEpochMilli()).apply()
        io.execute { sync() }
    }

    /**
     * Instagram just closed or the screen turned off: the numbers have stopped changing, so send them
     * now, whatever the interval, so an uninstall soon after loses nothing.
     */
    fun syncOnLeave() {
        prefs.edit().putLong(KEY_LAST, graph.clock.now().toEpochMilli()).apply()
        if (hasPending() && graph.accountSignIn.decided) io.execute { cloud.signIn { ok -> if (ok) io.execute { sendPendingChanges() } } }
        io.execute { sync() }
    }

    private fun sync() {
        // After switching accounts, nothing goes up until the earlier install's numbers are restored.
        if (graph.accountSignIn.restorePending || !graph.accountSignIn.decided) {
            Log.i(TAG, "sync skipped: ${if (graph.accountSignIn.restorePending) "restore pending" else "sign-in screen not answered"}")
            return
        }
        cloud.signIn { signedIn ->
            if (!signedIn) return@signIn
            io.execute {
                sendPendingChanges()
                send()
            }
        }
    }

    /**
     * A settings change goes out at once (with the install's new limits), not on the 30-minute cycle.
     * It waits in a queue on the phone until sent, so being offline loses nothing.
     */
    fun settingsChanged(from: AppSettings, to: AppSettings, source: ChangeSource, at: Instant) {
        val id = "${at.toEpochMilli()}"
        io.execute {
            enqueue(id, JSONObject(CloudDocuments.settingsChange(from, to, source, at).toJson()))
            // Queued until the sign-in screen is answered; the next sync sends it.
            if (!graph.accountSignIn.decided) return@execute
            cloud.signIn { signedIn ->
                if (signedIn) io.execute {
                    sendPendingChanges()
                    sendInstallNow()
                }
            }
        }
    }

    private fun sendPendingChanges() {
        val pending = JSONObject(prefs.getString(KEY_PENDING, "{}") ?: "{}")
        pending.keys().asSequence().toList().forEach { id ->
            val fields = jsonToMap(pending.getJSONObject(id))
            cloud.sendSettingsChange(id, fields) { ok -> if (ok) dequeue(id) }
        }
    }

    private fun sendInstallNow() {
        if (graph.accountSignIn.restorePending) return // never overwrite the account's backup before restoring it
        val today = graph.runner.stats.date
        val first = listOfNotNull(graph.statsStore.firstDay(), graph.usageStore.firstDay()).minOrNull() ?: today
        val device = ExportSharer(context, graph).device()
        val fields = CloudDocuments.install(graph.settings, graph.health.record.gaps, device, first, graph.clock.now()) +
            // The same file the backup folder holds, so signing in on a new phone restores it (docs/LLD.md).
            ("backup" to BackupFile.encode(graph.backup.contents()))
        cloud.sendInstall(fields) {}
    }

    private fun hasPending() = JSONObject(prefs.getString(KEY_PENDING, "{}") ?: "{}").length() > 0

    @Synchronized
    private fun enqueue(id: String, change: JSONObject) {
        val pending = JSONObject(prefs.getString(KEY_PENDING, "{}") ?: "{}").put(id, change)
        prefs.edit().putString(KEY_PENDING, pending.toString()).apply()
    }

    @Synchronized
    private fun dequeue(id: String) {
        val pending = JSONObject(prefs.getString(KEY_PENDING, "{}") ?: "{}").apply { remove(id) }
        prefs.edit().putString(KEY_PENDING, pending.toString()).apply()
    }

    private fun send() {
        val runner = graph.runner
        val today = runner.stats.date
        val first = listOfNotNull(graph.statsStore.firstDay(), graph.usageStore.firstDay()).minOrNull() ?: today
        val dates = generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.toList()
        val fields = dates.associateWith { date ->
            val stats = if (date == today) runner.stats else graph.statsStore.load(date) ?: DayStats(date)
            val usage = if (date == runner.usage.day) runner.usage else graph.usageStore.load(date)
            CloudDocuments.day(stats, usage)
        }
        val sent = sentFingerprints()
        val due = SyncPlanner.datesToSend(fields.mapValues { it.value.hashCode() }, sent)
        Log.i(TAG, "sync: sending ${due.size} changed day(s) and the install")
        due.forEach { date ->
            val day = fields.getValue(date)
            cloud.sendDay(date, day) { ok -> if (ok) markSent(date, day.hashCode()) }
        }
        sendInstallNow()
    }

    /** Sync now, whatever the interval: after signing in or restoring. */
    fun syncNow() {
        prefs.edit().putLong(KEY_LAST, graph.clock.now().toEpochMilli()).apply()
        io.execute { sync() }
    }

    private fun sentFingerprints(): Map<LocalDate, Int> {
        val json = JSONObject(prefs.getString(KEY_SENT, "{}") ?: "{}")
        return json.keys().asSequence().associate { LocalDate.parse(it) to json.getInt(it) }
    }

    @Synchronized
    private fun markSent(date: LocalDate, fingerprint: Int) {
        val json = JSONObject(prefs.getString(KEY_SENT, "{}") ?: "{}").put(date.toString(), fingerprint)
        prefs.edit().putString(KEY_SENT, json.toString()).apply()
    }

    private companion object {
        const val TAG = "AntiScroll"
        const val PREFS = "cloud_sync"
        const val KEY_LAST = "last_sync"
        const val KEY_SENT = "sent"
        const val KEY_PENDING = "pending_changes"
        val INTERVAL: Duration = Duration.ofMinutes(5)
    }
}

/** Field maps to JSON and back, for the queue of unsent changes (plain values only). */
private fun Map<String, Any?>.toJson(): Map<String, Any?> = mapValues { (_, v) ->
    when (v) {
        is Map<*, *> -> JSONObject(@Suppress("UNCHECKED_CAST") (v as Map<String, Any?>).toJson())
        null -> JSONObject.NULL
        else -> v
    }
}

private fun jsonToMap(json: JSONObject): Map<String, Any?> = json.keys().asSequence().associateWith { key ->
    when (val v = json.get(key)) {
        is JSONObject -> jsonToMap(v)
        is JSONArray -> List(v.length()) { v.get(it) }
        JSONObject.NULL -> null
        else -> v
    }
}
