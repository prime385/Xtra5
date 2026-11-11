package com.github.andreyasadchy.xtra.custom

import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import com.github.andreyasadchy.xtra.databinding.FragmentStreamsListItemBinding

/**
 * Xtra5 custom: live stream card layout (VOD and clip cards are unchanged).
 * Under the thumbnail: username (bold, 16sp), then category (bold, 16sp), then title
 * (regular weight, 12sp). No tags.
 *
 * Kept in its own package so upstream Xtra updates can be merged without conflicts.
 * Upstream hook: StreamsAdapter.PagingViewHolder.bind (end of the card setup).
 */
object StreamCards {

    private const val NAME_SP = 16f
    private const val CATEGORY_SP = 16f
    private const val TITLE_SP = 12f

    fun apply(binding: FragmentStreamsListItemBinding) {
        with(binding) {
            tagsLayout.visibility = View.GONE
            // Move the title below the username and category and style the text (once per card view).
            (title.parent as? LinearLayout)?.let { column ->
                if (column.indexOfChild(title) != column.childCount - 1) {
                    column.removeView(title)
                    column.addView(title)
                    title.setTextSize(TypedValue.COMPLEX_UNIT_SP, TITLE_SP)
                    title.typeface = Typeface.create("sans-serif", Typeface.NORMAL) // the Title style is medium weight
                    username.setTextSize(TypedValue.COMPLEX_UNIT_SP, NAME_SP)
                    username.setTypeface(username.typeface, Typeface.BOLD)
                    gameName.setTextSize(TypedValue.COMPLEX_UNIT_SP, CATEGORY_SP)
                    gameName.setTypeface(gameName.typeface, Typeface.BOLD)
                }
            }
        }
    }
}
