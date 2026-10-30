package com.abhishek.antiscroll.overlay

import android.graphics.Typeface
import android.os.Build
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.abhishek.antiscroll.R
import com.abhishek.antiscroll.present.TypingProgress
import com.google.android.material.button.MaterialButton
import kotlin.math.roundToInt

/**
 * Binds the typing layout (overlay_typing): the passage shows what's typed right (ink), wrong
 * (coral, underlined) and next (underlined), with live accuracy; Continue enables when the passage
 * is complete and accurate enough. Shared by the override and the settings challenge.
 */
class TypingPanel(val view: View) {

    private val context = view.context
    private val chip = view.findViewById<TextView>(R.id.overrideChip)
    private val title = view.findViewById<TextView>(R.id.typingTitle)
    private val passage = view.findViewById<TextView>(R.id.typingPassage)
    private val input = view.findViewById<EditText>(R.id.typingInput).apply { blockPaste() }
    private val accuracy = view.findViewById<TextView>(R.id.typingAccuracy)
    private val need = view.findViewById<TextView>(R.id.typingNeed)
    private val count = view.findViewById<TextView>(R.id.typingCount)
    private val feedback = view.findViewById<TextView>(R.id.typingFeedback)
    private val submit = view.findViewById<MaterialButton>(R.id.typingSubmit)
    private val cancel = view.findViewById<MaterialButton>(R.id.typingCancel)
    private val ink = ContextCompat.getColor(context, R.color.ink)
    private val ink3 = ContextCompat.getColor(context, R.color.ink3)
    private val coral = ContextCompat.getColor(context, R.color.coral)
    private val tint = ContextCompat.getColor(context, R.color.ink_tint)
    private var watcher: TextWatcher? = null

    data class Copy(val chip: String?, val title: String, val feedback: String?, val cancel: String)

    fun bind(target: String, minAccuracy: Double, copy: Copy, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
        chip.isVisible = copy.chip != null
        chip.text = copy.chip
        title.text = copy.title
        feedback.isVisible = copy.feedback != null
        feedback.text = copy.feedback
        cancel.text = copy.cancel
        need.text = context.getString(R.string.typing_need, (minAccuracy * 100).roundToInt())
        passage.contentDescription = context.getString(R.string.typing_passage_spoken, target)
        watcher?.let(input::removeTextChangedListener)
        input.setText("")
        val update = { show(target, input.text.toString(), minAccuracy) }
        watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable) = update()
        }.also(input::addTextChangedListener)
        update()
        submit.setOnClickListener { onSubmit(input.text.toString()) }
        cancel.setOnClickListener { onCancel() }
        passage.setOnClickListener { focus() }
    }

    /** Puts the cursor in the field and asks for the keyboard. */
    fun focus() {
        input.requestFocus()
        input.post { context.getSystemService(InputMethodManager::class.java).showSoftInput(input, 0) }
    }

    private fun show(target: String, typed: String, minAccuracy: Double) {
        val progress = TypingProgress.of(target, typed, minAccuracy)
        val text = SpannableString(target)
        progress.marks.forEachIndexed { i, mark ->
            val spans: List<Any> = when (mark) {
                TypingProgress.Mark.RIGHT -> listOf(ForegroundColorSpan(ink))
                TypingProgress.Mark.WRONG -> listOf(ForegroundColorSpan(coral), Underline(coral))
                TypingProgress.Mark.NEXT -> listOf(ForegroundColorSpan(ink), BackgroundColorSpan(tint), Underline(coral))
                TypingProgress.Mark.TODO -> listOf(ForegroundColorSpan(ink3))
            }
            spans.forEach { text.setSpan(it, i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        }
        passage.text = text
        accuracy.text = context.getString(R.string.typing_accuracy, progress.accuracyPercent)
        count.text = context.getString(R.string.typing_count, progress.typedCount, target.length)
        submit.isEnabled = progress.canContinue
        submit.setText(
            when {
                progress.canContinue -> R.string.typing_continue
                progress.complete -> R.string.typing_fix
                else -> R.string.typing_finish
            },
        )
    }

    /** An underline in [color]: wrong characters are marked by more than colour (docs/UI.md, Accessibility). */
    private class Underline(private val color: Int) : CharacterStyle() {
        override fun updateDrawState(tp: TextPaint) {
            tp.isUnderlineText = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tp.underlineColor = color
                tp.underlineThickness = tp.density * 2
            }
            tp.typeface = tp.typeface ?: Typeface.DEFAULT
        }
    }
}
