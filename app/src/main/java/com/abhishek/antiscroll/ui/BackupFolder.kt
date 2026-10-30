package com.abhishek.antiscroll.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.abhishek.antiscroll.AppGraph
import com.abhishek.antiscroll.data.BackupFile
import com.abhishek.antiscroll.rules.settings.ChangeSource
import com.abhishek.antiscroll.rules.usage.DayStats
import com.abhishek.antiscroll.rules.usage.DayUsage
import com.abhishek.antiscroll.rules.usage.plus
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.Executors

/**
 * The folder the user picked for a copy that survives uninstalling (docs/LLD.md, Backup folder):
 * writes the backup there, reads one back and merges it in. Reading the stores blocks, so the
 * public calls that do it run off the main thread ([write], [read]) or say so.
 */
class BackupFolder(context: Context, private val graph: AppGraph) {

    private val context = context.applicationContext
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val io = Executors.newSingleThreadExecutor()

    /** This install's own ID, written into its backups. */
    val installId: String
        get() = prefs.getString(KEY_INSTALL, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_INSTALL, it).apply()
        }

    val folder: Uri? get() = prefs.getString(KEY_FOLDER, null)?.let(Uri::parse)

    val lastWrittenAt: Instant? get() = prefs.getLong(KEY_WRITTEN, 0L).takeIf { it > 0 }?.let(Instant::ofEpochMilli)

    /** "Documents/AntiScroll": the picked folder, plus the AntiScroll folder inside it unless it is one. */
    fun folderName(): String? = folder?.let { tree ->
        val picked = DocumentsContract.getTreeDocumentId(tree).substringAfter(':')
        if (isAntiScroll(picked)) picked else "$picked/$FOLDER_NAME".trimStart('/')
    }

    /** Where the picker opens when no folder is set: Documents, so one tap picks it. */
    val suggestedStart: Uri
        get() = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Documents")

    /** Keeps access to [tree] across restarts and makes it the backup folder. */
    fun choose(tree: Uri) {
        context.contentResolver.takePersistableUriPermission(
            tree,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        prefs.edit().putString(KEY_FOLDER, tree.toString()).apply()
    }

    /** From Instagram closing, screen off, the app opening and the heartbeat: at most every [INTERVAL], in the background. */
    fun writeIfDue() {
        val last = lastWrittenAt
        if (folder == null || (last != null && Duration.between(last, graph.clock.now()) < INTERVAL)) return
        io.execute { write() }
    }

    /** Writes the backup now. Blocking; returns false if the folder can't be written. */
    fun write(): Boolean {
        val tree = folder ?: return false
        return try {
            val dir = antiScrollDir(tree, create = true) ?: return false
            val file = findChild(tree, dir, BackupFile.FILE_NAME) ?: DocumentsContract.createDocument(
                context.contentResolver,
                DocumentsContract.buildDocumentUriUsingTree(tree, dir),
                "application/json",
                BackupFile.FILE_NAME,
            ) ?: return false
            context.contentResolver.openOutputStream(file, "wt")?.use { it.write(BackupFile.encode(contents()).toByteArray()) }
            prefs.edit().putLong(KEY_WRITTEN, graph.clock.now().toEpochMilli()).apply()
            true
        } catch (e: Exception) {
            Log.w(TAG, "could not write the backup", e)
            false
        }
    }

    /** The backup in [tree], or null if there's none or it can't be read. Blocking. */
    fun read(tree: Uri): BackupFile.Contents? = try {
        antiScrollDir(tree, create = false)?.let { findChild(tree, it, BackupFile.FILE_NAME) }?.let { file ->
            context.contentResolver.openInputStream(file)?.use { BackupFile.decode(it.readBytes().decodeToString()) }
        }
    } catch (e: Exception) {
        Log.w(TAG, "could not read the backup", e)
        null
    }

    /**
     * The restore, split by thread: [mergePast] (blocking, off the main thread) merges and saves every
     * day except today; [finish] (main thread, where the runner lives) merges today and the rest.
     */
    fun mergePast(backup: BackupFile.Contents, today: LocalDate, todayWindow: LocalDate) {
        backup.usage.filter { it.day != todayWindow }.forEach { day ->
            graph.usageStore.save(graph.usageStore.load(day.day)?.let { day + it } ?: day)
        }
        backup.stats.filter { it.date != today }.forEach { day ->
            graph.statsStore.save(graph.statsStore.load(day.date)?.let { day + it } ?: day)
        }
    }

    fun finish(backup: BackupFile.Contents) {
        val runner = graph.runner
        val usage = backup.usage.find { it.day == runner.usage.day }?.let { it + runner.usage } ?: runner.usage
        val stats = backup.stats.find { it.date == runner.stats.date }?.let { it + runner.stats } ?: runner.stats
        runner.replaceToday(usage, stats)
        graph.health.restore(backup.health)
        // Not a loosening: it brings back what the user had set before reinstalling.
        graph.applySettings(backup.settings, ChangeSource.RESTORE)
    }

    /** Everything a restore needs, as of now. Blocking: reads the stores. */
    fun contents(): BackupFile.Contents {
        val runner = graph.runner
        val usage = days(graph.usageStore.firstDay(), runner.usage.day).map { day ->
            if (day == runner.usage.day) runner.usage else graph.usageStore.load(day)
        }
        val stats = days(graph.statsStore.firstDay(), runner.stats.date).map { date ->
            if (date == runner.stats.date) runner.stats else graph.statsStore.load(date)
        }
        return BackupFile.Contents(
            installId = installId,
            writtenAt = graph.clock.now(),
            settings = graph.settings,
            usage = usage.filterNotNull(),
            stats = stats.filterNotNull(),
            health = graph.health.record,
        )
    }

    private fun days(first: LocalDate?, last: LocalDate): List<LocalDate> =
        generateSequence(minOf(first ?: last, last)) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()

    /**
     * The document ID of the AntiScroll folder: the picked folder itself if that's its name, else the
     * one inside it, made if [create] (so nobody has to type the name).
     */
    private fun antiScrollDir(tree: Uri, create: Boolean): String? {
        val picked = DocumentsContract.getTreeDocumentId(tree)
        if (isAntiScroll(picked)) return picked
        findChild(tree, picked, FOLDER_NAME)?.let { return DocumentsContract.getDocumentId(it) }
        if (!create) return null
        val made = DocumentsContract.createDocument(
            context.contentResolver,
            DocumentsContract.buildDocumentUriUsingTree(tree, picked),
            DocumentsContract.Document.MIME_TYPE_DIR,
            FOLDER_NAME,
        ) ?: return null
        return DocumentsContract.getDocumentId(made)
    }

    /** Phone storage ignores letter case, so "Antiscroll" is the AntiScroll folder too. */
    private fun isAntiScroll(documentId: String) =
        documentId.substringAfterLast('/').substringAfterLast(':').equals(FOLDER_NAME, ignoreCase = true)

    private fun findChild(tree: Uri, parent: String, name: String): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
        context.contentResolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1).equals(name, ignoreCase = true)) return DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
            }
        }
        return null
    }

    private companion object {
        const val TAG = "AntiScroll"
        const val PREFS = "backup"
        const val KEY_FOLDER = "folder"
        const val KEY_WRITTEN = "written_at"
        const val KEY_INSTALL = "install_id"
        const val FOLDER_NAME = "AntiScroll"
        val INTERVAL: Duration = Duration.ofMinutes(10)
    }
}
