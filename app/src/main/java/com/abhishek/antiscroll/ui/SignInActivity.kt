package com.abhishek.antiscroll.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.abhishek.antiscroll.AntiScrollApp
import com.abhishek.antiscroll.BuildConfig
import com.abhishek.antiscroll.R
import com.abhishek.antiscroll.overlay.fitSystemBars
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

/**
 * Optional Google sign-in (docs/LLD.md, Cloud copy): links this install's anonymous account, or on a
 * reinstall or new phone switches to the account's earlier install and restores its numbers.
 */
class SignInActivity : AppCompatActivity() {

    private val graph get() = (application as AntiScrollApp).graph
    private lateinit var status: TextView
    private lateinit var button: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_sign_in)
        findViewById<android.view.View>(R.id.signInRoot).fitSystemBars()
        markShown(this)
        status = findViewById(R.id.signInStatus)
        button = findViewById(R.id.signInGoogle)
        button.setOnClickListener { signIn() }
        findViewById<android.view.View>(R.id.signInNotNow).setOnClickListener { done() }
        graph.cloud.googleEmail?.let { showStatus(getString(R.string.sign_in_already, it)) }
        // Back is "Not now": either way the choice is made and syncing can start.
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = done()
        })
    }

    private fun signIn() {
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isEmpty()) return showStatus(getString(R.string.sign_in_unavailable))
        button.isEnabled = false
        showStatus(getString(R.string.sign_in_working))
        lifecycleScope.launch {
            val token = googleIdToken() ?: return@launch failed(null)
            showStatus(getString(R.string.sign_in_restoring))
            // The app-level part carries on even if this screen closes; it shows its own message.
            graph.accountSignIn.signIn(token) { ok -> if (ok) done() else failed(getString(R.string.sign_in_failed)) }
        }
    }

    /** The Google account's ID token from Android's account chooser, or null if cancelled or failed. */
    private suspend fun googleIdToken(): String? {
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build())
            .build()
        return try {
            val credential = CredentialManager.create(this).getCredential(this, request).credential
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (e: GetCredentialCancellationException) {
            null
        } catch (e: GetCredentialException) {
            Log.w(TAG, "no Google credential", e)
            null
        }
    }

    private fun failed(message: String?) {
        button.isEnabled = true
        if (message == null) status.isVisible = false else showStatus(message)
    }

    private fun showStatus(text: String) {
        status.isVisible = true
        status.text = text
    }

    /** On first launch the disclosure comes next, if Accessibility is still off. */
    private fun done() {
        graph.accountSignIn.markDecided()
        if (!graph.systemChecks.accessibilityEnabled() && !DisclosureActivity.wasShown(this)) {
            startActivity(Intent(this, DisclosureActivity::class.java))
        }
        finish()
    }

    companion object {
        private const val TAG = "AntiScroll"
        private const val PREFS = "sign_in"
        private const val KEY_SHOWN = "shown"

        fun wasShown(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SHOWN, false)

        private fun markShown(context: Context) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_SHOWN, true).apply()
    }
}
