package com.github.andreyasadchy.xtra.custom

import android.content.Context
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.annotation.Keep
import androidx.core.content.edit
import androidx.preference.Preference
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.ui.view.CenteredImageSpan
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.prefs

/**
 * Xtra5 custom: special chat messages.
 *  - Custom background colors for subs/notices, first-time chatters, channel point rewards,
 *    mentions of you and bot messages (Settings > Chat > Special messages). Defaults are the
 *    official colors; bots get a new color.
 *  - Bot accounts: a built-in list of common bots plus your own additions (editable).
 *  - Reply lines start with a small reply icon instead of "Replying to".
 *
 * Kept in its own package so upstream Xtra updates can be merged without conflicts.
 * Upstream hooks: ChatAdapterUtils.prepareChatMessage (after the background is chosen; the
 * "Replying to" text).
 */
object SpecialMessages {

    const val PREF_NOTICE = "xtra5_color_notice"
    const val PREF_FIRST = "xtra5_color_first"
    const val PREF_REWARD = "xtra5_color_reward"
    const val PREF_MENTION = "xtra5_color_mention"
    const val PREF_BOT = "xtra5_color_bot"
    const val PREF_BOTS = "xtra5_bot_accounts"
    private const val DEFAULT_BOT_COLOR = 0x80455A64.toInt()

    val builtInBots = listOf(
        "nightbot", "streamelements", "moobot", "fossabot", "streamlabs", "wizebot", "sery_bot",
        "soundalerts", "pokemoncommunitygame", "botrixoficial", "kofistreambot", "blerp", "own3d",
        "commanderroot", "lolrankbot", "deepbot", "coebot", "phantombot", "streamcaptainbot",
    )

    fun defaultColor(context: Context, key: String): Int = when (key) {
        PREF_NOTICE -> context.getColor(R.color.chatMessageNotice)
        PREF_FIRST -> context.getColor(R.color.chatMessageFirst)
        PREF_REWARD -> context.getColor(R.color.chatMessageReward)
        PREF_MENTION -> context.getColor(R.color.chatMessageMention)
        else -> DEFAULT_BOT_COLOR
    }

    fun color(context: Context, key: String): Int =
        UserHighlights.parseColor(context.prefs().getString(key, null)) ?: defaultColor(context, key)

    fun setColor(context: Context, key: String, color: Int) {
        context.prefs().edit { putString(key, UserHighlights.toHex(color)) }
    }

    fun bots(context: Context): List<String> =
        context.prefs().getString(PREF_BOTS, null)?.lines()?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() } ?: builtInBots

    private var cachedBotsSource: String? = "unset"
    private var cachedBots: Set<String> = emptySet()

    private fun isBot(context: Context, login: String?): Boolean {
        if (login == null) return false
        val source = context.prefs().getString(PREF_BOTS, null)
        if (source != cachedBotsSource) {
            cachedBotsSource = source
            cachedBots = bots(context).toSet()
        }
        return login.lowercase() in cachedBots
    }

    /**
     * Called right after official Xtra picks a message's background: applies the custom colors.
     * Order as official: first message, reward, notice, mention; then the user's own highlight;
     * then bots.
     */
    fun applyColors(context: Context, itemView: View, chatMessage: ChatMessage, wasMentioned: Boolean, firstMsgVisibility: Int) {
        when {
            chatMessage.isFirst && firstMsgVisibility < 2 -> itemView.setBackgroundColor(color(context, PREF_FIRST))
            chatMessage.reward?.id != null && firstMsgVisibility < 2 -> itemView.setBackgroundColor(color(context, PREF_REWARD))
            chatMessage.systemMsg != null || chatMessage.msgId != null -> itemView.setBackgroundColor(color(context, PREF_NOTICE))
            wasMentioned -> itemView.setBackgroundColor(color(context, PREF_MENTION))
            UserHighlights.colorFor(context, chatMessage) != null -> Unit // already highlighted
            isBot(context, chatMessage.userLogin) -> itemView.setBackgroundColor(color(context, PREF_BOT))
        }
    }

    /** "Replying to name: " with a small reply icon in place of "Replying to". */
    fun replyPrefix(context: Context, replyMessage: String, userName: String?): CharSequence {
        val full = replyMessage.format(userName, "")
        val before = replyMessage.substringBefore("%1\$s")
        if (before.isEmpty() || !full.startsWith(before)) return full
        val text = "\uFFFC " + full.removePrefix(before)
        val icon = context.getDrawable(R.drawable.xtra5_reply_right)?.mutate() ?: return full
        icon.setTint(Color.parseColor("#999999"))
        val prefs = context.prefs()
        val sp = (prefs.getString("chat_text_size", "14")?.toFloatOrNull() ?: 14f) * (prefs.getInt("chat_size_modifier", 100) / 100f)
        val size = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.resources.displayMetrics).toInt()
        icon.setBounds(0, 0, size, size)
        return SpannableString(text).apply { setSpan(CenteredImageSpan(icon), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
    }
}

/** A special message color in Settings: shows a swatch, opens the color picker. */
@Keep
class SpecialColorPreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : Preference(context, attrs) {

    override fun onAttached() {
        super.onAttached()
        update()
    }

    override fun onClick() {
        HighlightDialogs.showColorPicker(context, title.toString(), "chatter", SpecialMessages.color(context, key)) { color ->
            SpecialMessages.setColor(context, key, color)
            update()
            ChatLive.refreshChat()
        }
    }

    private fun update() {
        icon = HighlightDialogs.swatch(context, SpecialMessages.color(context, key), 28)
    }

    // Xtra's settings theme tints every icon grey; the swatch must show its real color.
    override fun onBindViewHolder(holder: androidx.preference.PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        HighlightDialogs.showSwatchColors(holder)
    }
}

/** The bot accounts list in Settings: one name per line. */
@Keep
class BotAccountsPreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : Preference(context, attrs) {

    override fun onAttached() {
        super.onAttached()
        update()
    }

    override fun onClick() {
        val builder = context.getAlertDialogBuilder()
        val ctx = builder.context
        val density = ctx.resources.displayMetrics.density
        val input = EditText(ctx).apply {
            setText(SpecialMessages.bots(ctx).joinToString("\n"))
            minLines = 6
            maxLines = 14
            isSingleLine = false
        }
        val frame = LinearLayout(ctx).apply {
            setPadding((24 * density).toInt(), (8 * density).toInt(), (24 * density).toInt(), 0)
            addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        builder.setTitle("Bot accounts (one per line)")
            .setView(frame)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val names = input.text.lines().map { it.trim().removePrefix("@") }.filter { it.isNotEmpty() }
                context.prefs().edit { putString(SpecialMessages.PREF_BOTS, names.joinToString("\n")) }
                update()
                ChatLive.refreshChat()
            }
            .setNeutralButton("Reset to built-in") { _, _ ->
                context.prefs().edit { remove(SpecialMessages.PREF_BOTS) }
                update()
                ChatLive.refreshChat()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun update() {
        summary = "${SpecialMessages.bots(context).size} accounts"
    }
}
