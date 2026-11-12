package com.github.andreyasadchy.xtra.custom

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.View
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.chat.Image
import com.github.andreyasadchy.xtra.util.prefs
import java.lang.ref.WeakReference

/**
 * Xtra5 custom: synced animated emotes. Every copy of the same animated emote (same image, same
 * size) draws one shared animation, so all copies are always on the same frame.
 *
 * The first loaded animation for an emote becomes the shared one; later copies get a lightweight
 * wrapper that draws it. Wrappers are not Animatable, so official Xtra skips its per-message
 * animation setup for them. Up to MAX_SHARED emotes are kept; the least recently used are released.
 * 7TV overlay (zero-width) emotes are drawn through another path and are not synced. Only the
 * main chat list is synced; the shared animations are released when that chat closes.
 *
 * Kept in its own package so upstream Xtra updates can be merged without conflicts.
 * Upstream hook: ChatAdapterUtils.loadImages (after an emote is loaded and sized).
 */
object EmoteSync {

    const val PREF_ENABLED = "xtra5_sync_emotes"
    private const val MAX_SHARED = 100

    private val handler = Handler(Looper.getMainLooper())

    /** One shared animation and every chat row currently showing it. */
    private class Shared(val drawable: Drawable) : Drawable.Callback {
        val views = ArrayList<WeakReference<View>>()

        fun addView(view: View) {
            views.removeAll { it.get() == null }
            if (views.none { it.get() === view }) views.add(WeakReference(view))
        }

        override fun invalidateDrawable(who: Drawable) {
            var alive = false
            views.forEach { ref -> ref.get()?.let { alive = true; it.invalidate() } }
            if (!alive) views.clear()
        }

        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
            handler.postAtTime(what, who, `when`)
        }

        override fun unscheduleDrawable(who: Drawable, what: Runnable) {
            handler.removeCallbacks(what, who)
        }
    }

    private val shared = object : LinkedHashMap<String, Shared>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Shared>): Boolean {
            if (size > MAX_SHARED) {
                (eldest.value.drawable as? Animatable)?.stop()
                eldest.value.drawable.callback = null
                handler.removeCallbacksAndMessages(eldest.value.drawable)
                return true
            }
            return false
        }
    }

    /**
     * Draws the shared animation inside one chat row. A plain Drawable (not a DrawableWrapper,
     * which would take over the shared animation's callback) and not Animatable.
     */
    private class SyncedDrawable(private val target: Shared) : Drawable() {
        init {
            bounds = target.drawable.bounds
        }

        override fun draw(canvas: Canvas) {
            target.drawable.draw(canvas)
        }

        override fun getIntrinsicWidth() = target.drawable.intrinsicWidth
        override fun getIntrinsicHeight() = target.drawable.intrinsicHeight
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    /**
     * Marks [chatList] as the main chat. The shared animations are released when it goes away,
     * because the image loader frees them together with the chat.
     */
    fun attachTo(chatList: View) {
        chatList.setTag(R.id.xtra5_sync_emotes, true)
        chatList.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) = reset()
        })
    }

    fun reset() {
        shared.values.forEach {
            (it.drawable as? Animatable)?.stop()
            it.drawable.callback = null
            handler.removeCallbacksAndMessages(it.drawable)
        }
        shared.clear()
    }

    /**
     * Returns the drawable to show for [image] in [itemView]: a synced copy for animated emotes,
     * or [loaded] unchanged when syncing doesn't apply.
     */
    fun sync(context: Context, loaded: Drawable, image: Image, itemView: View, animate: Boolean): Drawable {
        if (!animate || !image.isAnimated || loaded !is Animatable) return loaded
        if (!context.prefs().getBoolean(PREF_ENABLED, true)) return loaded
        // Only the main chat list (see attachTo): its animations live as long as the chat does.
        if ((itemView.parent as? View)?.getTag(R.id.xtra5_sync_emotes) != true) return loaded
        val source = image.localData?.let { "local:" + it.contentHashCode() } ?: image.url1x ?: return loaded
        val key = "$source@${loaded.bounds.width()}x${loaded.bounds.height()}"
        val entry = shared[key] ?: Shared(loaded).also { entry ->
            shared[key] = entry
            loaded.callback = entry
            loaded.setVisible(true, false)
            (loaded as Animatable).start()
        }
        entry.addView(itemView)
        if (!(entry.drawable as Animatable).isRunning) (entry.drawable as Animatable).start()
        return SyncedDrawable(entry)
    }
}
