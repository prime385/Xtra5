package com.github.andreyasadchy.xtra.custom

import android.content.res.Configuration
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.util.prefs

/**
 * Xtra5 custom: column count for the VOD Chapters menu, independent of the app's column settings.
 * "0" (default) keeps the app's column count.
 *
 * Kept in its own package so upstream Xtra updates can be merged without conflicts.
 * Upstream hook: PlayerGamesDialog.onCreateView (chapters list).
 */
object ChapterColumns {

    const val PREF_LANDSCAPE = "xtra5_chapter_columns_landscape"
    const val PREF_PORTRAIT = "xtra5_chapter_columns_portrait"

    fun apply(recyclerView: RecyclerView) {
        val prefs = recyclerView.context.prefs()
        val portrait = recyclerView.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        val columns = prefs.getString(if (portrait) PREF_PORTRAIT else PREF_LANDSCAPE, "0")?.toIntOrNull() ?: 0
        if (columns in 1..6) {
            // Own layout manager, so the app-wide column setting no longer applies to this list.
            recyclerView.layoutManager = GridLayoutManager(recyclerView.context, columns)
        }
    }
}
