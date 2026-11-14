package com.github.andreyasadchy.xtra.custom

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentFollowedChannelsListItemBinding
import com.github.andreyasadchy.xtra.databinding.FragmentVideosListItemBinding
import com.github.andreyasadchy.xtra.model.ui.User

/**
 * Xtra5 custom: readable video and channel cards when they are narrow (many columns, e.g. on a TV).
 * Below NARROW_DP the text moves under the avatar row and uses the full card width:
 *   video cards:   avatar left and the options button right on one row, text below
 *   channel cards: avatar on top, name / info / Account-Local labels stacked below
 * Wider cards keep the original layout. Re-checked whenever the card's width changes.
 *
 * Kept in its own package so upstream Xtra updates can be merged without conflicts.
 * Upstream hooks: start of bind() in VideosAdapter, ClipsAdapter, BookmarksAdapter and
 * FollowedChannelsAdapter.
 */
object NarrowCards {

    private const val NARROW_DP = 260

    private class State(val original: ConstraintSet, var narrow: Boolean? = null)

    fun videoCard(binding: FragmentVideosListItemBinding) {
        val layout = binding.topLayout
        val column = binding.title.parent as? LinearLayout ?: return
        if (column.id == View.NO_ID) column.id = View.generateViewId()
        setup(binding.root, layout) { narrow ->
            val set = ConstraintSet().apply { clone(layout) }
            val avatar = binding.userImage.id
            val options = binding.options.id
            val avatarShown = binding.userImage.visibility != View.GONE
            set.clear(avatar, ConstraintSet.BOTTOM)
            // Options button on the avatar's row, at the right.
            set.clear(options, ConstraintSet.TOP)
            if (avatarShown) {
                set.connect(options, ConstraintSet.TOP, avatar, ConstraintSet.TOP)
                set.connect(options, ConstraintSet.BOTTOM, avatar, ConstraintSet.BOTTOM)
            } else {
                set.connect(options, ConstraintSet.TOP, binding.thumbnail.id, ConstraintSet.BOTTOM)
            }
            // Text below that row, full width.
            set.clear(column.id, ConstraintSet.START)
            set.clear(column.id, ConstraintSet.END)
            set.clear(column.id, ConstraintSet.TOP)
            set.connect(column.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, dp(layout, 10))
            set.connect(column.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, dp(layout, 10))
            set.connect(column.id, ConstraintSet.TOP, if (avatarShown) avatar else options, ConstraintSet.BOTTOM, dp(layout, 4))
            set.connect(column.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, dp(layout, 7))
            set
        }
    }

    fun channelCard(binding: FragmentFollowedChannelsListItemBinding, item: User? = null) {
        // Favourite ring / star and long-press, once the adapter has set up the card.
        item?.let { user -> binding.root.post { Favorites.bindChannel(binding, user) } }
        val layout = binding.layout1.parent as? ConstraintLayout ?: return
        setup(binding.root, layout, onChange = { narrow ->
            binding.layout2.gravity = if (narrow) Gravity.START else Gravity.BOTTOM or Gravity.END
        }) { _ ->
            val set = ConstraintSet().apply { clone(layout) }
            val avatar = binding.userImage.id
            val info = binding.layout1.id
            val labels = binding.layout2.id
            val avatarShown = binding.userImage.visibility != View.GONE
            set.clear(avatar, ConstraintSet.BOTTOM)
            // Name and info below the avatar, full width.
            set.clear(info, ConstraintSet.START)
            set.clear(info, ConstraintSet.END)
            set.clear(info, ConstraintSet.TOP)
            set.clear(info, ConstraintSet.BOTTOM)
            set.connect(info, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, dp(layout, 10))
            set.connect(info, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, dp(layout, 10))
            if (avatarShown) {
                set.connect(info, ConstraintSet.TOP, avatar, ConstraintSet.BOTTOM, dp(layout, 4))
            } else {
                set.connect(info, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, dp(layout, 7))
            }
            // Account / Local labels below the info, left aligned.
            set.clear(labels, ConstraintSet.START)
            set.clear(labels, ConstraintSet.END)
            set.clear(labels, ConstraintSet.TOP)
            set.connect(labels, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, dp(layout, 10))
            set.connect(labels, ConstraintSet.TOP, info, ConstraintSet.BOTTOM)
            set.connect(labels, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, dp(layout, 10))
            set.setHorizontalBias(labels, 0f)
            set
        }
    }

    /**
     * Remembers the card's original constraints once, then switches between them and the narrow
     * version whenever the card's width crosses NARROW_DP.
     */
    private fun setup(card: View, layout: ConstraintLayout, onChange: ((Boolean) -> Unit)? = null, narrowSet: (Boolean) -> ConstraintSet) {
        if (layout.getTag(R.id.xtra5_narrow_cards) == null) {
            val state = State(ConstraintSet().apply { clone(layout) })
            layout.setTag(R.id.xtra5_narrow_cards, state)
            card.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
                if (right - left != oldRight - oldLeft) update(card, layout, state, onChange, narrowSet)
            }
        }
        val state = layout.getTag(R.id.xtra5_narrow_cards) as State
        state.narrow = null // re-apply for this item (avatar visibility can differ)
        // The adapter sets visibilities after this hook runs, so wait until bind() has finished.
        card.post { update(card, layout, state, onChange, narrowSet) }
    }

    private fun update(card: View, layout: ConstraintLayout, state: State, onChange: ((Boolean) -> Unit)?, narrowSet: (Boolean) -> ConstraintSet) {
        val width = card.width
        if (width <= 0) return
        val narrow = width / card.resources.displayMetrics.density < NARROW_DP
        if (narrow == state.narrow) return
        state.narrow = narrow
        // ConstraintSet also stores visibility; keep whatever the adapter decided to show.
        val visibility = IntArray(layout.childCount) { layout.getChildAt(it).visibility }
        // Start from the original each time, so switching back and forth is exact.
        fun restoreVisibility() {
            for (i in 0 until layout.childCount) layout.getChildAt(i).visibility = visibility[i]
        }
        state.original.applyTo(layout)
        restoreVisibility() // the narrow set checks the avatar's real visibility
        if (narrow) {
            narrowSet(true).applyTo(layout)
            restoreVisibility()
        }
        onChange?.invoke(narrow)
        card.post { card.requestLayout() }
    }

    private fun dp(view: View, value: Int) = (value * view.resources.displayMetrics.density).toInt()
}
