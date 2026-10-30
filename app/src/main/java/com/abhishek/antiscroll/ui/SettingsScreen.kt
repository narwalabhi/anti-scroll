package com.abhishek.antiscroll.ui

import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.abhishek.antiscroll.R
import com.abhishek.antiscroll.present.HealthUiState
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch

/** Draws Settings: the tracking-health rows, and in debug builds the short-timers switch. */
class SettingsScreen(private val root: View, private val actions: Actions) {

    interface Actions {
        fun fix(item: HealthUiState.Item)
        fun setShortTimers(on: Boolean)
        fun export()
        fun chooseBackupFolder()
        fun openSignIn()
    }

    private val context = root.context
    private val inflater = LayoutInflater.from(context)

    init {
        val header = root.findViewById<View>(R.id.settingsHeader)
        header.findViewById<TextView>(R.id.headerTitle).setText(R.string.tab_settings)
        header.findViewById<View>(R.id.headerSubtitle).isVisible = false
        root.findViewById<View>(R.id.exportRow).setOnClickListener { actions.export() }
        root.findViewById<View>(R.id.backupRow).setOnClickListener { actions.chooseBackupFolder() }
        root.findViewById<View>(R.id.accountRow).setOnClickListener { actions.openSignIn() }
        root.findViewById<MaterialSwitch>(R.id.shortTimersSwitch).setOnCheckedChangeListener { _, on -> actions.setShortTimers(on) }
    }

    fun render(health: HealthUiState, debug: Boolean, shortTimers: Boolean, version: String) {
        root.findViewById<TextView>(R.id.healthChip).apply {
            isVisible = health.toFix > 0
            text = context.getString(R.string.to_fix, health.toFix)
            setBackgroundResource(R.drawable.bg_pill_coral)
            setTextColor(ContextCompat.getColor(context, R.color.coral))
        }
        val rows = root.findViewById<LinearLayout>(R.id.healthRows)
        rows.removeAllViews()
        health.rows.forEach { row ->
            val view = inflater.inflate(R.layout.item_health_row, rows, false)
            val (name, sub) = texts(row.item)
            view.findViewById<TextView>(R.id.healthName).setText(name)
            view.findViewById<TextView>(R.id.healthSub).setText(sub)
            view.findViewById<View>(R.id.healthOn).isVisible = row.status == HealthUiState.Status.ON
            view.findViewById<MaterialButton>(R.id.healthAction).apply {
                isVisible = row.status != HealthUiState.Status.ON
                if (row.status == HealthUiState.Status.FIX) {
                    setText(R.string.status_fix)
                    setTextColor(ContextCompat.getColor(context, R.color.coral))
                    strokeColor = ContextCompat.getColorStateList(context, R.color.coral)
                } else {
                    setText(R.string.status_set_up)
                }
                setOnClickListener { actions.fix(row.item) }
            }
            rows.addView(view)
        }
        root.findViewById<View>(R.id.developerSection).isVisible = debug
        root.findViewById<MaterialSwitch>(R.id.shortTimersSwitch).isChecked = shortTimers
        root.findViewById<TextView>(R.id.versionLine).text = version
    }

    fun renderAccount(email: String?) {
        root.findViewById<TextView>(R.id.accountStatus).text =
            email?.let { context.getString(R.string.account_signed_in, it) } ?: context.getString(R.string.account_signed_out)
    }

    /** "Not set up yet", or the folder and when the copy was last saved. */
    fun renderBackup(folder: String?, savedAt: String?) {
        root.findViewById<TextView>(R.id.backupStatus).text = when {
            folder == null -> context.getString(R.string.backup_not_set)
            savedAt == null -> context.getString(R.string.backup_in, folder)
            else -> context.getString(R.string.backup_saved, folder, savedAt)
        }
    }

    private fun texts(item: HealthUiState.Item): Pair<Int, Int> = when (item) {
        HealthUiState.Item.ACCESSIBILITY -> R.string.health_accessibility to R.string.health_accessibility_sub
        HealthUiState.Item.OVERLAY -> R.string.health_overlay to R.string.health_overlay_sub
        HealthUiState.Item.NOTIFICATIONS -> R.string.health_notifications to R.string.health_notifications_sub
        HealthUiState.Item.BATTERY -> R.string.health_battery to R.string.health_battery_sub
        HealthUiState.Item.USAGE_ACCESS -> R.string.health_usage to R.string.health_usage_sub
    }
}
