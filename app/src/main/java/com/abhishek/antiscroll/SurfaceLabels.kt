package com.abhishek.antiscroll

import androidx.annotation.StringRes
import com.abhishek.antiscroll.rules.profile.Surface

/** The name shown for a surface, on the status screen and the block screens. */
@StringRes
fun Surface.labelRes(): Int = when (this) {
    Surface.REELS -> R.string.surface_reels
    Surface.FEED -> R.string.surface_feed
    Surface.DMS -> R.string.surface_dms
    Surface.EXPLORE -> R.string.surface_explore
    Surface.STORIES -> R.string.surface_stories
    Surface.SEARCH -> R.string.surface_search
    Surface.PROFILE -> R.string.surface_profile
    Surface.OTHER -> R.string.surface_other
}
