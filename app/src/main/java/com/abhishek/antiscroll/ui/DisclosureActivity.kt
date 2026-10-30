package com.abhishek.antiscroll.ui

import android.content.Context
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.abhishek.antiscroll.R
import com.abhishek.antiscroll.overlay.fitSystemBars
import com.abhishek.antiscroll.system.SystemSettings

/**
 * Says what the Accessibility permission is used for and what it never does, before the user is
 * sent to Android's settings (docs/SPEC.md, Sharing with friends; Play policy asks for this too).
 */
class DisclosureActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_disclosure)
        findViewById<android.view.View>(R.id.disclosureRoot).fitSystemBars()
        markShown(this)
        findViewById<android.view.View>(R.id.disclosureContinue).setOnClickListener {
            startActivity(SystemSettings.accessibility())
            finish()
        }
        findViewById<android.view.View>(R.id.disclosureNotNow).setOnClickListener { finish() }
    }

    companion object {
        private const val PREFS = "disclosure"
        private const val KEY_SHOWN = "shown"

        fun wasShown(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SHOWN, false)

        private fun markShown(context: Context) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_SHOWN, true).apply()
    }
}
