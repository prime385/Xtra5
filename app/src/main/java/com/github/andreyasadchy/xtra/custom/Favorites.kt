package com.github.andreyasadchy.xtra.custom

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentStreamsListItemBinding
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.util.prefs

/**
 * Xtra5 custom: favourite streamers.
 *  - Long-press a streamer's profile picture on a live stream card to favourite / unfavourite.
 *  - Favourites get a gold ring around the profile picture and a gold star on top of it, centred at
 *    the bottom and hanging slightly below.
 *  - Following > Live lists favourites first (A-Z), then everyone else (A-Z).
 *
 * Kept in its own package so upstream Xtra updates can be merged without conflicts.
 * Upstream hooks: the sort line in FollowedStreamsDataSource; stream cards via StreamCards.
 */
object Favorites {

    private const val PREF = "xtra5_favorites"
    private const val GOLD = 0xFFFFC107.toInt()

    private var appContext: Context? = null
    private var ids: MutableSet<String>? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun favorites(): MutableSet<String> {
        ids?.let { return it }
        val loaded = appContext?.prefs()?.getStringSet(PREF, null)?.toMutableSet() ?: mutableSetOf()
        ids = loaded
        return loaded
    }

    fun isFavorite(channelId: String?) = channelId != null && channelId in favorites()

    private fun toggle(channelId: String): Boolean {
        val set = favorites()
        val nowFavorite = if (channelId in set) { set.remove(channelId); false } else { set.add(channelId); true }
        appContext?.prefs()?.edit { putStringSet(PREF, HashSet(set)) }
        return nowFavorite
    }

    /** Following > Live order: favourites first (A-Z), then the rest (A-Z). */
    fun sortFollowed(list: MutableList<Stream>) {
        list.sortWith(compareBy<Stream>({ !isFavorite(it.channelId) }, { (it.channelName ?: it.channelLogin ?: "").lowercase() }))
    }

    /** Called for every live stream card (from StreamCards). */
    fun bind(binding: FragmentStreamsListItemBinding, stream: Stream, fragment: Fragment) {
        val avatar = binding.userImage
        val layout = avatar.parent as? ConstraintLayout ?: return
        val star = layout.findViewById(R.id.xtra5_favorite_star) ?: ImageView(layout.context).also { star ->
            star.id = R.id.xtra5_favorite_star
            star.setImageResource(R.drawable.xtra5_star)
            star.isClickable = false
            star.isFocusable = false
            val size = dp(layout, 20)
            layout.addView(star, ConstraintLayout.LayoutParams(size, size).apply {
                startToStart = avatar.id
                endToEnd = avatar.id
                bottomToBottom = avatar.id
            })
            star.translationY = dp(layout, 7).toFloat() // hangs slightly below the picture
        }
        fun show(favorite: Boolean) {
            val visible = favorite && avatar.visibility == View.VISIBLE
            star.visibility = if (visible) View.VISIBLE else View.GONE
            avatar.foreground = if (visible) GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(layout, 3), GOLD)
            } else null
        }
        show(isFavorite(stream.channelId))
        val channelId = stream.channelId ?: return
        avatar.setOnLongClickListener {
            val favorite = toggle(channelId)
            show(favorite)
            val name = stream.channelName ?: stream.channelLogin ?: "Streamer"
            Toast.makeText(layout.context, if (favorite) "$name added to favourites" else "$name removed from favourites", Toast.LENGTH_SHORT).show()
            fragment.activity?.let { RefreshButton.refreshCurrentScreen(it, showMessage = false) } // re-sort
            true
        }
    }

    private fun dp(view: View, value: Int) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), view.resources.displayMetrics).toInt()
}
