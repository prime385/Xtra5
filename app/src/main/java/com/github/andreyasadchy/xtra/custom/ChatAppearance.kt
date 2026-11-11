package com.github.andreyasadchy.xtra.custom

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.util.prefs

/**
 * Xtra5 custom: chat message spacing, separator lines and chat font.
 *
 * Kept in its own package so upstream Xtra updates can be merged without conflicts.
 * Upstream hook: ChatFragment (chat list setup). Settings take effect when the chat is opened.
 */
object ChatAppearance {

    const val PREF_LINES = "xtra5_chat_lines"
    const val PREF_SPACING = "xtra5_chat_spacing"
    const val PREF_FONT = "xtra5_chat_font"
    private const val DEFAULT_SPACING = 4

    private val fonts = mapOf(
        "regular" to "sans-serif",
        "light" to "sans-serif-light",
        "medium" to "sans-serif-medium",
        "condensed" to "sans-serif-condensed",
        "condensed_medium" to "sans-serif-condensed-medium",
    )

    /** Called once when the chat list is set up. */
    fun apply(recyclerView: RecyclerView) {
        val prefs = recyclerView.context.prefs()
        val spacing = prefs.getInt(PREF_SPACING, DEFAULT_SPACING).coerceIn(1, 50)
        val lines = prefs.getBoolean(PREF_LINES, false)
        recyclerView.addItemDecoration(Spacing(recyclerView, spacing, lines))

        val family = fonts[prefs.getString(PREF_FONT, "default")]
        if (family != null) {
            val typeface = Typeface.create(family, Typeface.NORMAL)
            recyclerView.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(view: View) {
                    (view as? TextView)?.let { if (it.typeface != typeface) it.typeface = typeface }
                }

                override fun onChildViewDetachedFromWindow(view: View) {}
            })
        }
    }

    /** Adds [spacing] px between messages, with an optional 1 px line in the middle of the gap. */
    private class Spacing(recyclerView: RecyclerView, private val spacing: Int, private val lines: Boolean) : RecyclerView.ItemDecoration() {

        private val paint = Paint().apply {
            val tv = TypedValue()
            val context = recyclerView.context
            context.theme.resolveAttribute(android.R.attr.textColorPrimary, tv, true)
            val textColor = if (tv.resourceId != 0) context.getColorStateList(tv.resourceId).defaultColor else tv.data
            color = (textColor and 0x00FFFFFF) or 0x33000000 // ~20% of the text color
            strokeWidth = 1f
        }

        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            if (parent.getChildAdapterPosition(view) > 0) outRect.top = spacing
        }

        override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            if (!lines) return
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                if (parent.getChildAdapterPosition(child) <= 0) continue
                val y = child.top + child.translationY - spacing / 2f
                c.drawLine(parent.paddingLeft.toFloat(), y, (parent.width - parent.paddingRight).toFloat(), y, paint)
            }
        }
    }
}
