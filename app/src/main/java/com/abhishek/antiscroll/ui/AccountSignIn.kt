package com.abhishek.antiscroll.ui

import android.content.Context
import android.widget.Toast
import com.abhishek.antiscroll.AppGraph
import com.abhishek.antiscroll.R
import com.abhishek.antiscroll.data.BackupFile
import com.abhishek.antiscroll.rules.sync.FetchResult
import com.abhishek.antiscroll.rules.sync.SignInResult
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Signing in with a Google ID token and, for an account with an earlier install, restoring its
 * numbers (docs/LLD.md, Cloud copy). Runs in the app's own scope, not a screen's, so closing the
 * sign-in screen can't cut a restore short (seen on the phone 2026-10-30). Says how it went in a toast.
 */
class AccountSignIn(context: Context, private val graph: AppGraph) {

    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * True after switching to an account whose earlier install hasn't been restored yet. Until it is,
     * nothing is uploaded, so this install's few numbers can't overwrite the account's stored backup.
     */
    val restorePending: Boolean get() = prefs.getBoolean(KEY_PENDING, false)

    /**
     * True once the user has answered the sign-in screen (signed in, or "Not now"). Before that
     * nothing syncs: an anonymous upload behind the screen left a stray install on every reinstall.
     */
    val decided: Boolean get() = prefs.getBoolean(KEY_DECIDED, false) || graph.cloud.googleEmail != null

    fun markDecided() = prefs.edit().putBoolean(KEY_DECIDED, true).apply()

    fun signIn(idToken: String, done: (ok: Boolean) -> Unit) {
        scope.launch {
            val result = suspendCancellableCoroutine { cont -> graph.cloud.signInWithGoogle(idToken) { cont.resume(it) } }
            if (result == SignInResult.SWITCHED) prefs.edit().putBoolean(KEY_PENDING, true).apply()
            if (result != SignInResult.FAILED) markDecided()
            val message = when {
                result == SignInResult.FAILED -> R.string.sign_in_failed
                restorePending -> restore()
                else -> R.string.sign_in_linked
            }
            if (!restorePending && result != SignInResult.FAILED) graph.cloudSync.syncNow()
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            done(result != SignInResult.FAILED)
        }
    }

    /** Tries a pending restore again, e.g. when the app opens after a failed one. */
    fun retryPendingRestore() {
        if (!restorePending) return
        scope.launch {
            val message = restore()
            if (!restorePending) {
                graph.cloudSync.syncNow()
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Brings back the account's earlier install, merged with what's counted here; the pending flag is
     * cleared only once that's done or there's nothing to restore. A backup this install wrote itself
     * is skipped, so signing in again never counts the same day twice.
     */
    private suspend fun restore(): Int {
        val fetched = suspendCancellableCoroutine { cont -> graph.cloud.fetchBackup { cont.resume(it) } }
        val backup = when (fetched) {
            FetchResult.Failed -> return R.string.restore_failed
            FetchResult.None -> null
            is FetchResult.Found -> runCatching { BackupFile.decode(fetched.json) }.getOrNull()
        }
        if (backup != null && backup.installId != graph.backup.installId) {
            val today = graph.runner.stats.date
            val window = graph.runner.usage.day
            withContext(Dispatchers.IO) { graph.backup.mergePast(backup, today, window) }
            graph.backup.finish(backup)
            prefs.edit().putBoolean(KEY_PENDING, false).apply()
            return R.string.backup_restored
        }
        prefs.edit().putBoolean(KEY_PENDING, false).apply()
        return R.string.sign_in_linked
    }

    private companion object {
        const val PREFS = "account"
        const val KEY_PENDING = "restore_pending"
        const val KEY_DECIDED = "decided"
    }
}
