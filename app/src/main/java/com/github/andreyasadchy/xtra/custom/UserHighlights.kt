package com.github.andreyasadchy.xtra.custom

import android.content.Context
import android.graphics.Color
import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.util.prefs

/**
 * Xtra5 custom feature: highlight chat messages from specific users.
 *
 * Kept in its own package so upstream Xtra updates can be merged without conflicts.
 * Upstream hook: ChatAdapterUtils.prepareChatMessage (background selection).
 *
 * Preference format (one entry per line, or separated by commas/spaces):
 *   username            -> uses the default highlight color
 *   username#RRGGBB     -> custom color (alpha is added automatically)
 *   username#AARRGGBB   -> custom color with explicit alpha
 */
object UserHighlights {

    const val PREF_USERS = "xtra5_highlight_users"
    const val PREF_COLOR = "xtra5_highlight_color"
    const val DEFAULT_COLOR = "#2196F3"
    private const val DEFAULT_ALPHA = 0x59 // ~35% so text stays readable

    private var cachedSource: String? = null
    private var cachedDefaultColor: String? = null
    private var cachedMap: Map<String, Int> = emptyMap()

    /** Returns the background color for this message's sender, or null if not highlighted. */
    fun colorFor(context: Context, chatMessage: ChatMessage): Int? {
        val login = chatMessage.userLogin
        val name = chatMessage.userName
        if (login == null && name == null) return null
        val map = load(context)
        if (map.isEmpty()) return null
        return login?.let { map[it.lowercase()] } ?: name?.let { map[it.lowercase()] }
    }

    private fun load(context: Context): Map<String, Int> {
        val prefs = context.prefs()
        val source = prefs.getString(PREF_USERS, null)
        val defaultColor = prefs.getString(PREF_COLOR, null)
        if (source == cachedSource && defaultColor == cachedDefaultColor) return cachedMap
        cachedSource = source
        cachedDefaultColor = defaultColor
        cachedMap = parse(source, parseColor(defaultColor) ?: parseColor(DEFAULT_COLOR)!!)
        return cachedMap
    }

    internal fun parse(source: String?, defaultColor: Int): Map<String, Int> {
        if (source.isNullOrBlank()) return emptyMap()
        val result = HashMap<String, Int>()
        source.split(',', ' ', '\n', '\r', '\t', ';').forEach { raw ->
            val entry = raw.trim().removePrefix("@")
            if (entry.isEmpty()) return@forEach
            val hash = entry.indexOf('#')
            val user = (if (hash >= 0) entry.substring(0, hash) else entry).trim().lowercase()
            if (user.isEmpty()) return@forEach
            val color = if (hash >= 0) parseColor(entry.substring(hash)) ?: defaultColor else defaultColor
            result[user] = color
        }
        return result
    }

    internal fun parseColor(value: String?): Int? {
        val hex = value?.trim()?.removePrefix("#") ?: return null
        return when (hex.length) {
            6 -> hex.toLongOrNull(16)?.let { (DEFAULT_ALPHA shl 24) or it.toInt() }
            8 -> hex.toLongOrNull(16)?.toInt()
            else -> null
        }?.let { Color.argb(Color.alpha(it), Color.red(it), Color.green(it), Color.blue(it)) }
    }
}
