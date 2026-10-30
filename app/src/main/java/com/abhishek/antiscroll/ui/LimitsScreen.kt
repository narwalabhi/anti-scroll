package com.abhishek.antiscroll.ui

import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.abhishek.antiscroll.R
import com.abhishek.antiscroll.labelRes
import com.abhishek.antiscroll.present.Durations
import com.abhishek.antiscroll.present.LimitTarget
import com.abhishek.antiscroll.present.LimitsUiState
import com.google.android.material.button.MaterialButton

/** Draws a [LimitsUiState] into screen_limits (docs/UI.md, Limits). */
class LimitsScreen(private val root: View, private val actions: Actions) {

    interface Actions {
        fun setGuarding(on: Boolean)
        fun editLimit(row: LimitsUiState.Row)
    }

    private val context = root.context
    private val inflater = LayoutInflater.from(context)
    private val header = root.findViewById<View>(R.id.limitsHeader)

    init {
        header.findViewById<TextView>(R.id.headerTitle).setText(R.string.tab_limits)
        header.findViewById<View>(R.id.headerSubtitle).isVisible = false
    }

    fun render(state: LimitsUiState) {
        renderMode(state)
        val rows = root.findViewById<LinearLayout>(R.id.limitRows)
        rows.removeAllViews()
        state.rows.forEachIndexed { i, row ->
            if (i > 0) rows.addView(inflater.inflate(R.layout.item_divider, rows, false))
            val view = inflater.inflate(R.layout.item_limit_row, rows, false)
            val name = name(row.target)
            view.findViewById<TextView>(R.id.limitName).text = name
            view.findViewById<TextView>(R.id.limitValue).text = Durations.hm(row.limitMinutes * 60_000L)
            view.findViewById<TextView>(R.id.limitReference).apply {
                isVisible = row.reference != null
                text = row.reference?.let(::reference)
            }
            view.contentDescription = context.getString(R.string.limit_row_spoken, name, Durations.spoken(row.limitMinutes * 60_000L))
            view.setOnClickListener { actions.editLimit(row) }
            rows.addView(view)
        }
        renderLadder(state)
        root.findViewById<TextView>(R.id.changesNote).setText(R.string.changes_note)
    }

    private fun renderMode(state: LimitsUiState) {
        val icon = root.findViewById<ImageView>(R.id.modeIcon)
        val button = root.findViewById<MaterialButton>(R.id.modeButton)
        if (state.guarding) {
            icon.setImageResource(R.drawable.ic_shield)
            root.findViewById<TextView>(R.id.modeTitle).setText(R.string.mode_guarding_on)
            root.findViewById<TextView>(R.id.modeSub).setText(R.string.mode_guarding_sub)
            root.findViewById<TextView>(R.id.modeCopy).setText(R.string.mode_guarding_copy)
            button.setText(R.string.mode_turn_off)
            button.setOnClickListener { actions.setGuarding(false) }
        } else {
            icon.setImageResource(R.drawable.ic_eye)
            root.findViewById<TextView>(R.id.modeTitle).setText(R.string.mode_counting)
            root.findViewById<TextView>(R.id.modeSub).text = state.baselineDay
                ?.let { context.getString(R.string.mode_counting_sub, it) }
                ?: context.getString(R.string.mode_counting_sub_done)
            root.findViewById<TextView>(R.id.modeCopy).setText(
                if (state.baselineDay != null) R.string.mode_counting_copy else R.string.mode_counting_copy_done,
            )
            button.setText(if (state.baselineDay != null) R.string.mode_start_early else R.string.mode_start)
            button.setOnClickListener { actions.setGuarding(true) }
        }
    }

    private fun renderLadder(state: LimitsUiState) {
        val table = root.findViewById<LinearLayout>(R.id.ladderTable)
        table.removeAllViews()
        table.addView(row3(context.getString(R.string.ladder_override), context.getString(R.string.ladder_wait), context.getString(R.string.ladder_get), header = true))
        state.rungs.forEach { rung ->
            table.addView(row3(rung.number.toString(), Durations.hm(rung.waitSeconds * 1_000L), Durations.hm(rung.grantMinutes * 60_000L)))
        }
        val facts = root.findViewById<LinearLayout>(R.id.ladderFacts)
        facts.removeAllViews()
        facts.addView(keyValue(context.getString(R.string.ladder_cap), context.getString(R.string.ladder_cap_value, state.cap)))
        facts.addView(
            keyValue(
                context.getString(R.string.ladder_cooldown),
                context.getString(R.string.ladder_cooldown_value, state.cooldownBaseMinutes, state.cooldownStepMinutes),
            ),
        )
        facts.addView(keyValue(context.getString(R.string.ladder_typing), context.getString(R.string.ladder_typing_value, state.typingAccuracyPercent)))
    }

    private fun row3(a: String, b: String, c: String, header: Boolean = false): View {
        val view = inflater.inflate(R.layout.item_key_value, null, false)
        view.findViewById<TextView>(R.id.kvKey).text = a
        view.findViewById<TextView>(R.id.kvMiddle).apply {
            isVisible = true
            text = b
        }
        view.findViewById<TextView>(R.id.kvValue).text = c
        if (header) {
            val dim = ContextCompat.getColor(context, R.color.ink3)
            listOf(R.id.kvKey, R.id.kvMiddle, R.id.kvValue).forEach {
                view.findViewById<TextView>(it).apply {
                    setTextColor(dim)
                    textSize = 12f
                }
            }
        }
        return view
    }

    private fun keyValue(key: String, value: String): View {
        val view = inflater.inflate(R.layout.item_key_value, null, false)
        view.findViewById<TextView>(R.id.kvKey).text = key
        view.findViewById<TextView>(R.id.kvValue).text = value
        return view
    }

    private fun name(target: LimitTarget): String = when (target) {
        LimitTarget.Total -> context.getString(R.string.limit_total)
        is LimitTarget.OneSurface -> context.getString(target.surface.labelRes())
    }

    private fun reference(reference: LimitsUiState.Reference): String = when (reference) {
        is LimitsUiState.Reference.BaselineDay -> context.getString(R.string.reference_day, reference.day, Durations.hm(reference.millis))
        is LimitsUiState.Reference.BaselineAverage -> context.getString(R.string.reference_average, Durations.hm(reference.millis))
    }

    companion object {
        fun name(context: android.content.Context, target: LimitTarget): String = when (target) {
            LimitTarget.Total -> context.getString(R.string.limit_total)
            is LimitTarget.OneSurface -> context.getString(target.surface.labelRes())
        }
    }
}
