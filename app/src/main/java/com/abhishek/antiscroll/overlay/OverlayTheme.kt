package com.abhishek.antiscroll.overlay

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.abhishek.antiscroll.R

/**
 * A context for drawing over Instagram: always the dark tokens (docs/UI.md, Inside Instagram),
 * whatever theme the phone or the app uses.
 */
fun Context.overlayContext(): Context {
    val config = Configuration(resources.configuration).apply {
        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
    }
    return ContextThemeWrapper(createConfigurationContext(config), R.style.Theme_AntiScroll)
}

/**
 * Keeps the view's content clear of the status bar, the navigation bar and the keyboard: its own
 * padding plus theirs. Overlay windows are laid out under the bars.
 */
fun View.fitSystemBars() {
    val start = paddingStart
    val top = paddingTop
    val end = paddingEnd
    val bottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
        view.setPaddingRelative(start + bars.left, top + bars.top, end + bars.right, bottom + bars.bottom)
        insets
    }
}
