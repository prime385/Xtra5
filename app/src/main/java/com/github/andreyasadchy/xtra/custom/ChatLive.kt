package com.github.andreyasadchy.xtra.custom

import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.player.PlayerFragment
import java.lang.ref.WeakReference

/**
 * Xtra5 custom: the open chat, for live highlight updates and for placing dialogs beside it.
 *  - refreshChat(): re-draws the chat's messages (e.g. after highlights change).
 *  - previewUser(): recolors one user's messages on screen right away, while picking a color.
 *  - placeBesideChat(): with the chat beside the video (landscape), centres a dialog left of it.
 *
 * Kept in its own package so upstream Xtra updates can be merged without conflicts.
 * The chat list is registered by ChatAppearance.apply; rows carry their sender from markRow.
 */
object ChatLive {

    private const val GAP_DP = 4
    private val chats = ArrayList<WeakReference<RecyclerView>>()

    fun register(chatList: RecyclerView) {
        chats.removeAll { it.get() == null }
        if (chats.none { it.get() === chatList }) chats.add(WeakReference(chatList))
    }

    private fun alive() = chats.mapNotNull { it.get() }.filter { it.isAttachedToWindow }

    /** The chat currently on screen, if any. */
    fun visibleChat(): RecyclerView? = alive().firstOrNull { it.isShown }

    /** Re-draws every message of the open chat(s), so highlight changes show at once. */
    fun refreshChat() {
        alive().forEach { list ->
            // Re-drawing every message must not move the chat: at the bottom it stays at the
            // bottom, scrolled up the same message stays at the same spot.
            val manager = list.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
            val atBottom = !list.canScrollVertically(1)
            val first = manager?.findFirstVisibleItemPosition() ?: RecyclerView.NO_POSITION
            val offset = if (first != RecyclerView.NO_POSITION) (manager?.findViewByPosition(first)?.top ?: 0) - list.paddingTop else 0
            list.adapter?.let { it.notifyItemRangeChanged(0, it.itemCount) }
            if (manager == null) return@forEach
            val restore = Runnable {
                val count = list.adapter?.itemCount ?: 0
                when {
                    count == 0 || list.scrollState != RecyclerView.SCROLL_STATE_IDLE -> Unit // you're scrolling: leave it
                    atBottom -> list.scrollToPosition(count - 1)
                    first != RecyclerView.NO_POSITION && first < count -> manager.scrollToPositionWithOffset(first, offset)
                }
            }
            list.post(restore)
            // Once the re-drawn emotes have their sizes.
            list.postDelayed(restore, 300)
        }
    }

    /**
     * Shows [color] (or no highlight, if null) behind [login]'s messages on screen, without saving.
     * Rows with their own special background (mentions, first messages, rewards, notices) keep it.
     */
    fun previewUser(context: Context, login: String, color: Int?) {
        val saved = UserHighlights.colorForLogin(context, login)
        alive().forEach { list ->
            for (i in 0 until list.childCount) {
                val row = list.getChildAt(i)
                if (row.getTag(R.id.xtra5_row_login) != login) continue
                val current = (row.background as? ColorDrawable)?.color
                val previewed = row.getTag(R.id.xtra5_row_preview) as? Int
                val eligible = row.background == null || current == saved || (previewed != null && current == previewed)
                if (!eligible) continue
                if (color != null) {
                    row.setBackgroundColor(color)
                    row.setTag(R.id.xtra5_row_preview, color)
                } else {
                    row.background = null
                    row.setTag(R.id.xtra5_row_preview, null)
                }
            }
        }
    }

    /**
     * Xtra5: [login]'s highlight changed from [before]: recolours only their rows on screen (in
     * every open chat) instead of re-drawing the whole chat, which made it jump. Rows with their own
     * special background keep it. Rows kept aside for quick re-use are dropped so they're drawn
     * afresh; the rest pick the new colour up when they scroll into view.
     */
    fun recolorUser(context: Context, login: String, before: Int?) {
        val after = UserHighlights.colorForLogin(context, login)
        alive().forEach { list ->
            for (i in 0 until list.childCount) {
                val row = list.getChildAt(i)
                if (row.getTag(R.id.xtra5_row_login) != login) continue
                val current = (row.background as? ColorDrawable)?.color
                val previewed = row.getTag(R.id.xtra5_row_preview) as? Int
                val eligible = row.background == null || current == before || (previewed != null && current == previewed)
                if (!eligible) continue
                row.setTag(R.id.xtra5_row_preview, null)
                if (after != null) row.setBackgroundColor(after) else row.background = null
            }
            list.setItemViewCacheSize(0)
            list.setItemViewCacheSize(2)
        }
    }

    /** The chat's background color, for an accurate preview. */
    fun chatBackground(): Int? {
        var view: View? = visibleChat()
        while (view != null) {
            (view.background as? ColorDrawable)?.let { return it.color }
            view = view.parent as? View
        }
        return null
    }

    /** Width available left of the chat (landscape, chat beside the video), or null. */
    fun widthLeftOfChat(context: Context): Int? {
        if (context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) return null
        val chat = visibleChat() ?: return null
        val chatPanel = generateSequence(chat as View) { it.parent as? View }.firstOrNull { it.id == R.id.chatLayout } ?: chat
        val left = IntArray(2).also { chatPanel.getLocationOnScreen(it) }[0]
        val available = left - (GAP_DP * context.resources.displayMetrics.density).toInt()
        return available.takeIf { it > (200 * context.resources.displayMetrics.density) }
    }

    /** Share of the space left of the chat that a dialog may use (it is centred in that space). */
    private const val USABLE = 0.9f

    /** Widest a dialog beside the chat can be (landscape, chat beside the video), or null. */
    fun usableWidthBesideChat(context: Context): Int? = widthLeftOfChat(context)?.let { (it * USABLE).toInt() }

    /**
     * Centres [dialog] in the space left of the chat, at most [maxWidth] wide (and at most 90% of
     * that space). No-op without a chat beside.
     * The chat stays bright: Android's whole-screen dimming is switched off and only the video is
     * dimmed (same strength) while the dialog is open.
     */
    fun placeBesideChat(dialog: Dialog, maxWidth: Int) {
        val window = dialog.window ?: return
        val available = widthLeftOfChat(dialog.context) ?: return
        val width = minOf(maxWidth, (available * USABLE).toInt())
        val dim = window.attributes.dimAmount.takeIf { it > 0f } ?: 0.32f
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        window.attributes = window.attributes.apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            x = (available - width) / 2
        }
        dimVideo(dialog, dim)
    }

    // One shared dim layer over the video, kept while any dialog beside the chat is open.
    private var videoDim: WeakReference<View>? = null
    private var dimUsers = 0

    private fun dimVideo(dialog: Dialog, amount: Float) {
        val activity = generateSequence(dialog.context) { (it as? ContextWrapper)?.baseContext }.firstNotNullOfOrNull { it as? MainActivity } ?: return
        val video = (activity.playerFragment as? PlayerFragment)?.view?.findViewById<FrameLayout>(R.id.playerLayout) ?: return
        val layer = videoDim?.get()?.takeIf { it.parent === video } ?: View(video.context).apply {
            setBackgroundColor(Color.argb((amount * 255).toInt(), 0, 0, 0))
            isClickable = false
            isFocusable = false
            video.addView(this, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            videoDim = WeakReference(this)
            dimUsers = 0
        }
        layer.bringToFront()
        dimUsers++
        window(dialog)?.decorView?.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                v.removeOnAttachStateChangeListener(this)
                dimUsers--
                if (dimUsers <= 0) {
                    (layer.parent as? ViewGroup)?.removeView(layer)
                    videoDim = null
                    dimUsers = 0
                }
            }
        })
    }

    private fun window(dialog: Dialog) = dialog.window
}
