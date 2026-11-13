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

    /** Saved highlight color for [login] (lower case), or null. */
    fun colorForLogin(context: Context, login: String): Int? = load(context)[login]

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
        val result = HashMap<String, Int>()
        parseEntries(source).forEach { (user, color) -> result[user.lowercase()] = color ?: defaultColor }
        return result
    }

    /** Entries in saved order: username (as typed) to its own color, or null for the default color. */
    fun parseEntries(source: String?): List<Pair<String, Int?>> {
        if (source.isNullOrBlank()) return emptyList()
        val result = ArrayList<Pair<String, Int?>>()
        source.split(',', ' ', '\n', '\r', '\t', ';').forEach { raw ->
            val entry = raw.trim().removePrefix("@")
            if (entry.isEmpty()) return@forEach
            val hash = entry.indexOf('#')
            val user = (if (hash >= 0) entry.substring(0, hash) else entry).trim()
            if (user.isEmpty() || result.any { it.first.equals(user, true) }) return@forEach
            result.add(user to if (hash >= 0) parseColor(entry.substring(hash)) else null)
        }
        return result
    }

    fun serializeEntries(entries: List<Pair<String, Int?>>): String =
        entries.joinToString("\n") { (user, color) -> if (color != null) "$user${toHex(color)}" else user }

    fun toHex(color: Int): String = String.format("#%08X", color)

    fun getDefaultColor(context: Context): Int =
        parseColor(context.prefs().getString(PREF_COLOR, null)) ?: parseColor(DEFAULT_COLOR)!!

    internal fun parseColor(value: String?): Int? {
        val hex = value?.trim()?.removePrefix("#") ?: return null
        return when (hex.length) {
            6 -> hex.toLongOrNull(16)?.let { (DEFAULT_ALPHA shl 24) or it.toInt() }
            8 -> hex.toLongOrNull(16)?.toInt()
            else -> null
        }?.let { Color.argb(Color.alpha(it), Color.red(it), Color.green(it), Color.blue(it)) }
    }
}
