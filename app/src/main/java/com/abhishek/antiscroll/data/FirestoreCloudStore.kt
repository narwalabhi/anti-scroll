package com.abhishek.antiscroll.data

import android.content.Context
import android.util.Log
import com.abhishek.antiscroll.BuildConfig
import com.abhishek.antiscroll.rules.sync.CloudStore
import com.abhishek.antiscroll.rules.sync.FetchResult
import com.abhishek.antiscroll.rules.sync.SignInResult
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.time.LocalDate

/**
 * The developer's copy in Cloud Firestore (docs/LLD.md, Cloud copy): each install signs in
 * anonymously and writes `installs/{uid}` and `installs/{uid}/days/{date}`. Firebase is started here,
 * on first use, from the BuildConfig fields; without them (no google-services.json) nothing is sent.
 */
class FirestoreCloudStore(private val context: Context) : CloudStore {

    private val app: FirebaseApp? by lazy {
        if (BuildConfig.FIREBASE_PROJECT_ID.isEmpty()) return@lazy null
        FirebaseApp.getApps(context).firstOrNull { it.name == APP_NAME } ?: FirebaseApp.initializeApp(
            context,
            FirebaseOptions.Builder()
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .build(),
            APP_NAME,
        )
    }

    private val auth get() = app?.let(FirebaseAuth::getInstance)
    private val db get() = app?.let(FirebaseFirestore::getInstance)

    override fun signIn(done: (Boolean) -> Unit) {
        val auth = auth ?: return done(false)
        if (auth.currentUser != null) return done(true)
        auth.signInAnonymously().addOnCompleteListener { task ->
            if (!task.isSuccessful) Log.w(TAG, "anonymous sign-in failed", task.exception)
            done(task.isSuccessful)
        }
    }

    override fun sendInstall(fields: Map<String, Any?>, done: (Boolean) -> Unit) {
        val doc = installDoc() ?: return done(false)
        doc.set(fields).addOnCompleteListener { report("install", it.isSuccessful, it.exception, done) }
    }

    override fun sendDay(date: LocalDate, fields: Map<String, Any?>, done: (Boolean) -> Unit) {
        val doc = installDoc()?.collection("days")?.document(date.toString()) ?: return done(false)
        doc.set(fields).addOnCompleteListener { report("day $date", it.isSuccessful, it.exception, done) }
    }

    override val googleEmail: String?
        get() = auth?.currentUser?.takeUnless { it.isAnonymous }?.email

    override fun signInWithGoogle(idToken: String, done: (SignInResult) -> Unit) {
        val auth = auth ?: return done(SignInResult.FAILED)
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val user = auth.currentUser
        if (user == null || !user.isAnonymous) {
            auth.signInWithCredential(credential).addOnCompleteListener { done(if (it.isSuccessful) SignInResult.SWITCHED else failed(it.exception)) }
            return
        }
        user.linkWithCredential(credential).addOnCompleteListener { link ->
            when {
                link.isSuccessful -> done(SignInResult.LINKED)
                // The Google account already belongs to an earlier install: become that install.
                link.exception is FirebaseAuthUserCollisionException ->
                    auth.signInWithCredential(credential).addOnCompleteListener {
                        done(if (it.isSuccessful) SignInResult.SWITCHED else failed(it.exception))
                    }
                else -> done(failed(link.exception))
            }
        }
    }

    override fun fetchBackup(done: (FetchResult) -> Unit) {
        val doc = installDoc() ?: return done(FetchResult.Failed)
        doc.get().addOnCompleteListener { task ->
            // A failed task throws from `result`, so look only after checking it succeeded.
            if (!task.isSuccessful) {
                Log.w(TAG, "could not fetch the backup", task.exception)
                return@addOnCompleteListener done(FetchResult.Failed)
            }
            done(task.result?.getString("backup")?.let { FetchResult.Found(it) } ?: FetchResult.None)
        }
    }

    private fun failed(error: Exception?): SignInResult {
        Log.w(TAG, "Google sign-in failed", error)
        return SignInResult.FAILED
    }

    override fun sendSettingsChange(id: String, fields: Map<String, Any?>, done: (Boolean) -> Unit) {
        val doc = installDoc()?.collection("settingsChanges")?.document(id) ?: return done(false)
        doc.set(fields).addOnCompleteListener { report("settings change $id", it.isSuccessful, it.exception, done) }
    }

    private fun installDoc() = auth?.currentUser?.uid?.let { uid -> db?.collection("installs")?.document(uid) }

    private fun report(what: String, ok: Boolean, error: Exception?, done: (Boolean) -> Unit) {
        if (!ok) Log.w(TAG, "could not send $what", error)
        done(ok)
    }

    private companion object {
        const val TAG = "AntiScroll"
        const val APP_NAME = "antiscroll"
    }
}
