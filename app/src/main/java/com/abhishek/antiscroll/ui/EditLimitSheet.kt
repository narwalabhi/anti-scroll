package com.abhishek.antiscroll.ui

import android.content.Context
import android.view.LayoutInflater
import android.widget.TextView
import androidx.core.view.isVisible
import com.abhishek.antiscroll.R
import com.abhishek.antiscroll.present.Durations
import com.abhishek.antiscroll.present.LimitEditing
import com.abhishek.antiscroll.present.LimitsUiState
import com.abhishek.antiscroll.ui.widget.GaugeView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup

/**
 * The edit sheet for one limit (docs/UI.md, Edit sheet). It only picks a number; [onSave] gets it
 * and the caller decides what the change costs.
 */
class EditLimitSheet(
    private val context: Context,
    private val row: LimitsUiState.Row,
    private val guarding: Boolean,
    private val onSave: (Int) -> Unit,
) {

    private var minutes = row.limitMinutes

    fun show() {
        val dialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.sheet_edit_limit, null)
        val name = LimitsScreen.name(context, row.target)
        view.findViewById<TextView>(R.id.editTitle).text = name
        val value = view.findViewById<TextView>(R.id.editValue)
        val gauge = view.findViewById<GaugeView>(R.id.editGauge).apply { large = false }
        val contextText = view.findViewById<TextView>(R.id.editContextText)
        val reference = row.reference
        view.findViewById<android.view.View>(R.id.editContext).isVisible = reference != null
        view.findViewById<TextView>(R.id.editRule).setText(if (guarding) R.string.edit_rule_guarding else R.string.edit_rule_counting)

        val presets = view.findViewById<MaterialButtonToggleGroup>(R.id.editPresets)
        val presetIds = LimitEditing.presets(row.target).associateBy {
            MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                id = android.view.View.generateViewId()
                text = Durations.hm(it * 60_000L)
                isAllCaps = false
                minHeight = (44 * context.resources.displayMetrics.density).toInt()
                presets.addView(this, android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }.id
        }

        fun update() {
            value.text = Text.bigDuration(context, minutes * 60_000L, unitSp = 24)
            value.contentDescription = Durations.spoken(minutes * 60_000L)
            presetIds.entries.find { it.value == minutes }?.let { presets.check(it.key) } ?: presets.clearChecked()
            if (reference != null) {
                gauge.usedMillis = reference.millis
                gauge.limitMillis = minutes * 60_000L
                gauge.contentDescription = context.getString(
                    R.string.gauge_spoken,
                    Durations.spoken(reference.millis),
                    Durations.spoken(minutes * 60_000L),
                )
                contextText.text = when (reference) {
                    is LimitsUiState.Reference.BaselineDay ->
                        context.getString(R.string.edit_context_day, reference.day, Durations.hm(reference.millis))
                    is LimitsUiState.Reference.BaselineAverage ->
                        context.getString(R.string.edit_context_average, Durations.hm(reference.millis))
                }
            }
        }

        view.findViewById<MaterialButton>(R.id.editMinus).setOnClickListener {
            minutes = LimitEditing.clamp(minutes - LimitEditing.STEP_MINUTES)
            update()
        }
        view.findViewById<MaterialButton>(R.id.editPlus).setOnClickListener {
            minutes = LimitEditing.clamp(minutes + LimitEditing.STEP_MINUTES)
            update()
        }
        presets.addOnButtonCheckedListener { _, id, checked ->
            if (checked) presetIds[id]?.let { if (it != minutes) { minutes = it; update() } }
        }
        view.findViewById<MaterialButton>(R.id.editSave).setOnClickListener {
            dialog.dismiss()
            if (minutes != row.limitMinutes) onSave(minutes)
        }
        view.findViewById<MaterialButton>(R.id.editCancel).setOnClickListener { dialog.dismiss() }
        update()
        dialog.setContentView(view)
        dialog.show()
    }
}
