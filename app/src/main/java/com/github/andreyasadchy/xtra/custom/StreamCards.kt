package com.github.andreyasadchy.xtra.custom

import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import com.github.andreyasadchy.xtra.databinding.FragmentStreamsListItemBinding

/**
 * Xtra5 custom: live stream card layout (VOD and clip cards are unchanged).
 * Under the thumbnail: username (bold), then category, then title (2sp smaller). No tags.
 *
 * Kept in its own package so upstream Xtra updates can be merged without conflicts.
 * Upstream hook: StreamsAdapter.PagingViewHolder.bind (end of the card setup).
 */
object StreamCards {

    private const val TITLE_SHRINK_SP = 2f

    fun apply(binding: FragmentStreamsListItemBinding) {
        with(binding) {
            tagsLayout.visibility = View.GONE
            // Move the title below the username and category (once per card view).
            (title.parent as? LinearLayout)?.let { column ->
                if (column.indexOfChild(title) != column.childCount - 1) {
                    column.removeView(title)
                    column.addView(title)
                    val shrink = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, TITLE_SHRINK_SP, root.resources.displayMetrics)
                    title.setTextSize(TypedValue.COMPLEX_UNIT_PX, title.textSize - shrink)
                    username.setTypeface(username.typeface, Typeface.BOLD)
                }
            }
        }
    }
}
