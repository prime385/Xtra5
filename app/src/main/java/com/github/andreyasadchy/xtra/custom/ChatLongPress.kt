package com.github.andreyasadchy.xtra.custom

import android.content.ContextWrapper
import android.text.Spanned
import android.text.style.ClickableSpan
import android.text.style.ReplacementSpan
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.player.PlayerFragment

/**
 * Xtra5 custom: long-press in the main chat.
 *  - On an emote: puts the emote's text into the chat input (not sent), showing the input if hidden.
 *  - Anywhere else on a message: moves the sender into the default highlight group, or out of it.
 *
 * Kept in its own package so upstream Xtra updates can be merged without conflicts.
 * Upstream hook: ChatAdapter passes its emote/badge click listener through [wrap]; the
 * long-press itself is attached by ChatAppearance.
 */
object ChatLongPress {

    private var capturing = false
    private var captured: String? = null

    /** While a long-press asks an emote which one it is, its normal popup is not opened. */
    fun wrap(listener: ((String?, String?, String?, Boolean?, Int?, Boolean?, String?) -> Unit)?): ((String?, String?, String?, Boolean?, Int?, Boolean?, String?) -> Unit)? {
        if (listener == null) return null
        return { url, name, format, isAnimated, source, thirdParty, emoteId ->
            if (capturing) {
                // Twitch emotes carry an id, 7TV/BTTV/FFZ emotes a source; badges, cheers and gifs neither.
                val isEmote = emoteId != null || source != null || (thirdParty == false && format != "gif")
                captured = if (isEmote) name else null
            } else {
                listener(url, name, format, isAnimated, source, thirdParty, emoteId)
            }
        }
    }

    /** Returns true when the long-press was used (so the release doesn't also count as a tap). */
    fun onLongPress(textView: TextView, x: Float, y: Float): Boolean {
        if (textView.getTag(R.id.xtra5_reply_row) == true) return false
        imageSpanAt(textView, x, y)?.let { span ->
            capturing = true
            captured = null
            try {
                span.onClick(textView)
            } finally {
                capturing = false
            }
            val emote = captured
            captured = null
            if (!emote.isNullOrBlank()) {
                insertIntoChatInput(textView, emote)
                return true
            }
        }
        val login = textView.getTag(R.id.xtra5_row_login) as? String ?: return false
        val name = (textView.getTag(R.id.xtra5_row_name) as? String)?.takeIf { it.equals(login, true) } ?: login
        Toast.makeText(textView.context, UserHighlights.toggleInDefaultGroup(textView.context, name), Toast.LENGTH_SHORT).show()
        ChatLive.refreshChat()
        return true
    }

    /** The clickable emote/badge image under the finger, if any (not links or text). */
    private fun imageSpanAt(textView: TextView, x: Float, y: Float): ClickableSpan? {
        val text = textView.text as? Spanned ?: return null
        val layout = textView.layout ?: return null
        val px = x - textView.totalPaddingLeft + textView.scrollX
        val py = y - textView.totalPaddingTop + textView.scrollY
        val line = layout.getLineForVertical(py.toInt())
        if (px < layout.getLineLeft(line) || px > layout.getLineRight(line)) return null
        val offset = layout.getOffsetForHorizontal(line, px)
        val spans = text.getSpans(offset, offset, ClickableSpan::class.java)
        return spans.firstOrNull { span ->
            val start = text.getSpanStart(span)
            val end = text.getSpanEnd(span)
            text.getSpans(start, end, ReplacementSpan::class.java).isNotEmpty()
        }
    }

    private fun insertIntoChatInput(textView: TextView, emote: String) {
        val root = generateSequence(textView.parent as? View) { it.parent as? View }.firstOrNull { it.findViewById<View>(R.id.editText) != null }
        val editText = root?.findViewById<EditText>(R.id.editText)
        val messageView = root?.findViewById<View>(R.id.messageView)
        val send = root?.findViewById<View>(R.id.send)
        if (editText == null || messageView == null || send?.hasOnClickListeners() != true) {
            Toast.makeText(textView.context, "Chat input not available", Toast.LENGTH_SHORT).show()
            return
        }
        if (messageView.visibility != View.VISIBLE) {
            val activity = generateSequence(textView.context) { (it as? ContextWrapper)?.baseContext }.firstNotNullOfOrNull { it as? MainActivity }
            (activity?.playerFragment as? PlayerFragment)?.takeIf { it.view != null }?.toggleChatBar()
            messageView.visibility = View.VISIBLE
        }
        val editable = editText.text
        val start = editText.selectionStart.takeIf { it >= 0 } ?: editable.length
        val end = editText.selectionEnd.takeIf { it >= 0 } ?: start
        val from = minOf(start, end)
        val to = maxOf(start, end)
        val before = if (from > 0 && !editable[from - 1].isWhitespace()) " " else ""
        val after = if (to < editable.length && editable[to].isWhitespace()) "" else " "
        val insert = before + emote + after
        editable.replace(from, to, insert)
        editText.setSelection((from + insert.length).coerceAtMost(editable.length))
    }
}
