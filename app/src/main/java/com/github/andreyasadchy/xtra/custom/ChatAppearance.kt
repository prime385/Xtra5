package com.github.andreyasadchy.xtra.custom

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.content.Context
import android.graphics.Typeface
import android.annotation.SuppressLint
import android.text.Spannable
import android.text.Spanned
import android.text.style.ClickableSpan
import android.view.MotionEvent
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.util.prefs

/**
 * Xtra5 custom: chat message spacing, separator lines, chat font and username size.
 *
 * Kept in its own package so upstream Xtra updates can be merged without conflicts.
 * Upstream hooks: ChatFragment (chat list setup; spacing, lines and font take effect when the
 * chat is opened) and ChatAdapterUtils.prepareChatMessage (username size, applied per message).
 */
object ChatAppearance {

    const val PREF_LINES = "xtra5_chat_lines"
    const val PREF_LINE_OPACITY = "xtra5_chat_line_opacity"
    const val PREF_SPACING = "xtra5_chat_spacing"
    const val PREF_FONT = "xtra5_chat_font"
    const val PREF_USERNAME_SIZE = "xtra5_chat_username_size"
    private const val DEFAULT_SPACING = 4

    private val fonts = mapOf(
        "regular" to "sans-serif",
        "light" to "sans-serif-light",
        "medium" to "sans-serif-medium",
        "condensed" to "sans-serif-condensed",
        "condensed_medium" to "sans-serif-condensed-medium",
    )

    /** The chat font chosen in Settings, or null for the default. */
    fun chatTypeface(context: Context): Typeface? =
        fonts[context.prefs().getString(PREF_FONT, "default")]?.let { Typeface.create(it, Typeface.NORMAL) }

    /** Called once when the chat list is set up. */
    fun apply(recyclerView: RecyclerView) {
        EmoteSync.attachTo(recyclerView)
        ChatLive.register(recyclerView)
        val prefs = recyclerView.context.prefs()
        val spacing = prefs.getInt(PREF_SPACING, DEFAULT_SPACING).coerceIn(1, 50)
        val lines = prefs.getBoolean(PREF_LINES, false)
        recyclerView.addItemDecoration(Spacing(recyclerView, spacing, lines))

        val typeface = fonts[prefs.getString(PREF_FONT, "default")]?.let { Typeface.create(it, Typeface.NORMAL) }
        recyclerView.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                val textView = view as? TextView ?: return
                if (typeface != null && textView.typeface != typeface) textView.typeface = typeface
                watchSpanTaps(textView)
            }

            override fun onChildViewDetachedFromWindow(view: View) {}
        })
    }

    /** Remembers whether the latest tap on a chat row landed on an emote, badge or link. */
    @SuppressLint("ClickableViewAccessibility")
    private fun watchSpanTaps(textView: TextView) {
        if (textView.getTag(R.id.xtra5_span_tap_watch) == true) return
        textView.setTag(R.id.xtra5_span_tap_watch, true)
        var downX = 0f
        var downY = 0f
        var longPressed = false
        textView.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    longPressed = false
                    v.setTag(R.id.xtra5_span_tap, hasClickableSpanAt(v as TextView, event.x, event.y))
                }
                MotionEvent.ACTION_UP -> if (longPressed) {
                    // After a long-press the release must not also open the emote or message popup.
                    longPressed = false
                    val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                    v.onTouchEvent(cancel)
                    cancel.recycle()
                    return@setOnTouchListener true
                }
            }
            false // normal handling continues (the emote's own popup still opens)
        }
        // Long-press: emote -> chat input, otherwise the sender in/out of the default group.
        textView.setOnLongClickListener { v ->
            ChatLongPress.onLongPress(v as TextView, downX, downY).also { longPressed = it }
        }
    }

    private fun hasClickableSpanAt(textView: TextView, x: Float, y: Float): Boolean {
        val text = textView.text as? Spanned ?: return false
        val layout = textView.layout ?: return false
        val px = x - textView.totalPaddingLeft + textView.scrollX
        val py = y - textView.totalPaddingTop + textView.scrollY
        val line = layout.getLineForVertical(py.toInt())
        if (px < layout.getLineLeft(line) || px > layout.getLineRight(line)) return false
        val offset = layout.getOffsetForHorizontal(line, px)
        return text.getSpans(offset, offset, ClickableSpan::class.java).isNotEmpty()
    }

    /**
     * Called at the start of the chat row's click handler: true when the tap was on an emote,
     * badge or link, so only that item's own action runs (not the message popup).
     */
    fun tappedSpan(view: View): Boolean {
        val tapped = view.getTag(R.id.xtra5_span_tap) == true
        view.setTag(R.id.xtra5_span_tap, false) // D-pad / keyboard clicks never count as span taps
        return tapped
    }

    /** Marks grey "replying to" rows so no spacing or line separates them from their message. */
    fun markRow(itemView: View, chatMessage: ChatMessage) {
        itemView.setTag(R.id.xtra5_reply_row, chatMessage.type == ChatMessage.REPLY_MESSAGE)
        // Sender, for live highlight previews (ChatLive.previewUser).
        itemView.setTag(R.id.xtra5_row_login, if (chatMessage.type == ChatMessage.REPLY_MESSAGE) null else chatMessage.userLogin?.lowercase())
        itemView.setTag(R.id.xtra5_row_name, if (chatMessage.type == ChatMessage.REPLY_MESSAGE) null else chatMessage.userName)
        itemView.setTag(R.id.xtra5_row_preview, null)
    }

    private fun isReplyRow(view: View) = view.getTag(R.id.xtra5_reply_row) == true

    /** Sizes the username at the start of a message (50-150% of the message text). */
    fun styleUserName(context: Context, builder: Spannable, start: Int, end: Int) {
        val percent = context.prefs().getInt(PREF_USERNAME_SIZE, 100).coerceIn(50, 150)
        if (percent != 100 && end > start) {
            builder.setSpan(RelativeSizeSpan(percent / 100f), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    /**
     * Adds [spacing] px below each message, with an optional 1 px line in the middle of the gap.
     * Reply rows get no gap below, so they stay attached to the message they belong to.
     */
    private class Spacing(recyclerView: RecyclerView, private val spacing: Int, private val lines: Boolean) : RecyclerView.ItemDecoration() {

        private val paint = Paint().apply {
            val tv = TypedValue()
            val context = recyclerView.context
            context.theme.resolveAttribute(android.R.attr.textColorPrimary, tv, true)
            val textColor = if (tv.resourceId != 0) context.getColorStateList(tv.resourceId).defaultColor else tv.data
            val opacity = recyclerView.context.prefs().getInt(PREF_LINE_OPACITY, 20).coerceIn(5, 100)
            color = (textColor and 0x00FFFFFF) or ((opacity * 255 / 100) shl 24)
            strokeWidth = 1f
        }

        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            if (!isReplyRow(view)) outRect.bottom = spacing
        }

        override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            if (!lines) return
            val last = (parent.adapter?.itemCount ?: 0) - 1
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                val position = parent.getChildAdapterPosition(child)
                if (position == RecyclerView.NO_POSITION || position >= last || isReplyRow(child)) continue
                val y = child.bottom + child.translationY + spacing / 2f
                c.drawLine(parent.paddingLeft.toFloat(), y, (parent.width - parent.paddingRight).toFloat(), y, paint)
            }
        }
    }
}
