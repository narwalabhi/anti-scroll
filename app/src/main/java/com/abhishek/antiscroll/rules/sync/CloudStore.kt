package com.abhishek.antiscroll.rules.sync

import java.time.LocalDate

/**
 * Where the developer's copy goes (docs/LLD.md, Cloud copy). Plain field maps, so nothing in rules
 * knows the cloud behind it. Each call reports success or failure once, on any thread.
 */
interface CloudStore {
    /** Makes sure the install is signed in (anonymously); false if that isn't possible now. */
    fun signIn(done: (Boolean) -> Unit)

    fun sendInstall(fields: Map<String, Any?>, done: (Boolean) -> Unit)

    fun sendDay(date: LocalDate, fields: Map<String, Any?>, done: (Boolean) -> Unit)

    /**
     * Signs in with a Google ID token: links this install's anonymous account ([SignInResult.LINKED],
     * same ID), or, when that Google account already has an ID (an earlier install), switches to it
     * ([SignInResult.SWITCHED]) so its data can be restored.
     */
    fun signInWithGoogle(idToken: String, done: (SignInResult) -> Unit)

    /** The Google account's email while signed in with Google, else null. */
    val googleEmail: String?

    /** The account's stored backup: [FetchResult.Found], [FetchResult.None], or [FetchResult.Failed] (try again later). */
    fun fetchBackup(done: (FetchResult) -> Unit)

    /** Adds one settings change to the install's history; [id] makes a resend overwrite, not duplicate. */
    fun sendSettingsChange(id: String, fields: Map<String, Any?>, done: (Boolean) -> Unit)
}

enum class SignInResult { LINKED, SWITCHED, FAILED }

sealed interface FetchResult {
    data class Found(val json: String) : FetchResult
    data object None : FetchResult
    data object Failed : FetchResult
}

/** Which days need sending: those whose fields changed since they were last sent (or never were). */
object SyncPlanner {

    /** [current] and [sent]: a fingerprint of each day's fields now, and when last sent. Oldest first. */
    fun datesToSend(current: Map<LocalDate, Int>, sent: Map<LocalDate, Int>): List<LocalDate> =
        current.filter { (date, fingerprint) -> sent[date] != fingerprint }.keys.sorted()
}
