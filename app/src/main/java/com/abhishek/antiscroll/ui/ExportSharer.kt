package com.abhishek.antiscroll.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.abhishek.antiscroll.AppGraph
import com.abhishek.antiscroll.R
import com.abhishek.antiscroll.data.HistoryExport
import com.abhishek.antiscroll.rules.usage.DayStats
import com.abhishek.antiscroll.rules.usage.DayUsage
import java.io.File
import java.time.LocalDate

/**
 * Builds the export file from the stores and opens Android's share sheet with it. Reads the stores,
 * so [share] must run off the main thread; [open] starts the sheet.
 */
class ExportSharer(private val context: Context, private val graph: AppGraph) {

    /** Writes the export and returns the share intent, or null if there's nothing to share. */
    fun prepare(): Intent? {
        val today = graph.runner.stats.date
        val first = listOfNotNull(graph.statsStore.firstDay(), graph.usageStore.firstDay()).minOrNull() ?: today
        val dates = generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.toList()
        val usage = mutableMapOf<LocalDate, DayUsage>()
        val days = dates.map { date ->
            if (date == today) {
                usage[date] = graph.runner.usage
                return@map graph.runner.stats
            }
            val window = graph.usageStore.load(date)?.also { usage[date] = it }
            graph.statsStore.load(date) ?: window?.let(DayStats::from) ?: DayStats(date)
        }
        val json = HistoryExport.build(days, usage, graph.settings, graph.health.record.gaps, device(), graph.clock.now())
        return try {
            val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
            val file = File(dir, "antiscroll-$today.json").apply { writeText(json) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            Intent(Intent.ACTION_SEND)
                .setType("application/json")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.export_subject, today.toString()))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
            Log.w(TAG, "could not write the export", e)
            null
        }
    }

    fun open(send: Intent) {
        context.startActivity(Intent.createChooser(send, context.getString(R.string.export_title)))
    }

    fun device(): HistoryExport.Device {
        val pm = context.packageManager
        fun version(pkg: String): String? = try {
            pm.getPackageInfo(pkg, 0).versionName
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
        return HistoryExport.Device(
            appVersion = version(context.packageName) ?: "?",
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidSdk = Build.VERSION.SDK_INT,
            instagramVersion = version(graph.instagramProfile.packageName),
        )
    }

    private companion object {
        const val TAG = "AntiScroll"
        const val EXPORT_DIR = "exports"
    }
}
