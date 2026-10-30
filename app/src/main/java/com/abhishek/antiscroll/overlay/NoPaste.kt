package com.abhishek.antiscroll.overlay

import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText

/** Most characters one change may add; more at once is a paste (or a swiped word) and is undone. */
private const val MAX_CHARS_PER_CHANGE = 3

/**
 * Makes this field accept typing only: no long-press menu or selection handles, a keyboard mode
 * without suggestions or the clipboard bar, and any change that adds more than
 * [MAX_CHARS_PER_CHANGE] characters at once is undone.
 */
fun EditText.blockPaste() {
    isLongClickable = false
    val noMenu = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu) = false
        override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem) = false
        override fun onDestroyActionMode(mode: ActionMode) = Unit
    }
    customSelectionActionModeCallback = noMenu
    customInsertionActionModeCallback = noMenu
    // Visible-password mode turns off suggestions and, on most keyboards, the clipboard bar.
    inputType = InputType.TYPE_CLASS_TEXT or
        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
        InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
        InputType.TYPE_TEXT_FLAG_MULTI_LINE
    addTextChangedListener(object : TextWatcher {
        private var previous = ""
        private var restoring = false

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            if (!restoring) previous = s.toString()
        }

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) = Unit

        override fun afterTextChanged(s: Editable) {
            if (restoring || s.length - previous.length <= MAX_CHARS_PER_CHANGE) return
            restoring = true
            s.replace(0, s.length, previous)
            restoring = false
        }
    })
}
