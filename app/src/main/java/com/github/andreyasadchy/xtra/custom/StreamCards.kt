package com.github.andreyasadchy.xtra.custom

import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.github.andreyasadchy.xtra.databinding.FragmentStreamsListItemBinding
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.util.prefs

/**
 * Xtra5 custom: live stream card layout (VOD and clip cards are unchanged).
 * Under the thumbnail: username (bold), then category (bold), then title (regular weight). No tags.
 * Text sizes come from Settings > UI > Stream cards (10-20sp). Long-press opens the stream in
 * audio-only quality, just this once (see AudioOnlyOnce).
 *
 * Kept in its own package so upstream Xtra updates can be merged without conflicts.
 * Upstream hook: StreamsAdapter.PagingViewHolder.bind (end of the card setup).
 */
object StreamCards {

    const val PREF_NAME_SIZE = "xtra5_card_name_size"
    const val PREF_CATEGORY_SIZE = "xtra5_card_category_size"
    const val PREF_TITLE_SIZE = "xtra5_card_title_size"
    private const val DEFAULT_NAME_SP = 16
    private const val DEFAULT_CATEGORY_SP = 16
    private const val DEFAULT_TITLE_SP = 12

    private val regular = Typeface.create("sans-serif", Typeface.NORMAL)

    fun apply(binding: FragmentStreamsListItemBinding, stream: Stream, fragment: Fragment) {
        // Long-press: open this stream in audio-only quality, just this once.
        binding.root.setOnLongClickListener {
            val activity = fragment.activity as? MainActivity ?: return@setOnLongClickListener false
            AudioOnlyOnce.request()
            Toast.makeText(activity, "Opening audio only", Toast.LENGTH_SHORT).show()
            activity.startStream(stream)
            true
        }
        with(binding) {
            tagsLayout.visibility = View.GONE
            // Move the title below the username and category and set the weights (once per card view).
            (title.parent as? LinearLayout)?.let { column ->
                if (column.indexOfChild(title) != column.childCount - 1) {
                    column.removeView(title)
                    column.addView(title)
                    title.typeface = regular // the Title style is medium weight
                    username.setTypeface(username.typeface, Typeface.BOLD)
                    gameName.setTypeface(gameName.typeface, Typeface.BOLD)
                }
            }
            // Sizes every time, so changes in Settings show without restarting.
            val prefs = root.context.prefs()
            username.setTextSize(TypedValue.COMPLEX_UNIT_SP, size(prefs.getInt(PREF_NAME_SIZE, DEFAULT_NAME_SP)))
            gameName.setTextSize(TypedValue.COMPLEX_UNIT_SP, size(prefs.getInt(PREF_CATEGORY_SIZE, DEFAULT_CATEGORY_SP)))
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, size(prefs.getInt(PREF_TITLE_SIZE, DEFAULT_TITLE_SP)))
        }
    }

    private fun size(sp: Int) = sp.coerceIn(10, 20).toFloat()
}
