package com.github.andreyasadchy.xtra.custom

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.text.style.StyleSpan
import android.text.style.ForegroundColorSpan
import android.text.Spanned
import android.text.SpannableStringBuilder
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.Keep
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.preference.Preference
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.prefs
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider

/**
 * Xtra5 custom: settings UI for user highlights (user list editor and color picker).
 * Referenced by class name from res/xml/chat_preferences.xml, so no upstream code hooks are needed.
 */
/** With TV mode shortcuts on, dialog margins above and below (Material's default is 80dp). */
private const val TV_INSET_DP = 20

private fun tvPanels(context: Context) = context.prefs().getBoolean(TvShortcuts.PREF_ENABLED, false)

/** Dialog builder for the highlight panels: with TV mode shortcuts on they may be taller. */
private fun panelBuilder(context: Context): AlertDialog.Builder = context.getAlertDialogBuilder().also { builder ->
    if (tvPanels(context) && builder is com.google.android.material.dialog.MaterialAlertDialogBuilder) {
        val inset = (TV_INSET_DP * context.resources.displayMetrics.density).toInt()
        builder.setBackgroundInsetTop(inset)
        builder.setBackgroundInsetBottom(inset)
    }
}

@Keep
class HighlightUsersPreference @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : Preference(context, attrs) {

    override fun onAttached() {
        super.onAttached()
        updateSummary()
    }

    override fun onClick() {
        HighlightDialogs.showUserList(context) { updateSummary() }
    }

    private fun updateSummary() {
        val users = UserHighlights.parseEntries(context.prefs().getString(UserHighlights.PREF_USERS, null))
        val groups = UserHighlights.parseGroups(context.prefs().getString(UserHighlights.PREF_GROUPS, null))
        val names = groups.map { "${it.name} (${it.members.size})" } + users.map { it.first }
        summary = when {
            names.isEmpty() -> "No users yet. Tap to add"
            names.size <= 4 -> names.joinToString(", ")
            else -> names.take(4).joinToString(", ") + " and ${names.size - 4} more"
        }
    }
}

@Keep
class HighlightColorPreference @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : Preference(context, attrs) {

    override fun onAttached() {
        super.onAttached()
        update()
    }

    override fun onClick() {
        HighlightDialogs.showColorPicker(context, title.toString(), "username", UserHighlights.getDefaultColor(context)) { color ->
            context.prefs().edit { putString(UserHighlights.PREF_COLOR, UserHighlights.toHex(color)) }
            update()
        }
    }

    private fun update() {
        val color = UserHighlights.getDefaultColor(context)
        icon = HighlightDialogs.swatch(context, color, 28)
        summary = "Used for newly added users"
    }
}

object HighlightDialogs {

    private val palette = intArrayOf(
        0xF44336, 0xE91E63, 0x9C27B0, 0x673AB7, 0x3F51B5, 0x2196F3,
        0x03A9F4, 0x00BCD4, 0x009688, 0x4CAF50, 0x8BC34A, 0xCDDC39,
        0xFFEB3B, 0xFFC107, 0xFF9800, 0xFF5722, 0x795548, 0x9E9E9E,
    )

    /** Highlighted users: groups (name + color, members use the group color) and ungrouped users. */
    fun showUserList(context: Context, onChanged: () -> Unit) {
        val prefs = context.prefs()
        val entries = UserHighlights.parseEntries(prefs.getString(UserHighlights.PREF_USERS, null)).toMutableList()
        val groups = UserHighlights.parseGroups(prefs.getString(UserHighlights.PREF_GROUPS, null))
        fun save() {
            prefs.edit {
                putString(UserHighlights.PREF_USERS, UserHighlights.serializeEntries(entries))
                putString(UserHighlights.PREF_GROUPS, UserHighlights.serializeGroups(groups))
            }
            onChanged()
            ChatLive.refreshChat() // the open chat shows the change right away
        }
        /** Takes [name] out of wherever it is (ungrouped or a group). */
        fun detach(name: String) {
            entries.removeAll { it.first.equals(name, true) }
            groups.forEach { g -> g.members.removeAll { it.equals(name, true) } }
        }
        val builder = panelBuilder(context)
        val ctx = builder.context
        // Beside the chat in landscape with room to spare: two columns (Groups | Ungrouped).
        val wide = (ChatLive.usableWidthBesideChat(ctx) ?: 0) >= dp(ctx, 600)
        val panelWidth = if (wide) dp(ctx, 900) else dp(ctx, 420)
        fun column() = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val list = column()
        val groupColumn = column()
        val userColumn = column()
        val content = ScrollView(ctx).apply {
            setPadding(dp(ctx, 16), dp(ctx, 8), dp(ctx, 16), 0)
            if (wide) {
                list.addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(groupColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(ctx, 8) })
                    addView(userColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(ctx, 8) })
                })
            }
            addView(list)
        }
        var defaultGroup = UserHighlights.defaultGroupName(ctx)
        fun isDefault(group: UserHighlights.Group) = group.name == defaultGroup
        fun setDefault(name: String?) {
            defaultGroup = name
            prefs.edit { if (name == null) remove(UserHighlights.PREF_DEFAULT_GROUP) else putString(UserHighlights.PREF_DEFAULT_GROUP, name) }
        }
        fun groupTitle(group: UserHighlights.Group) = group.name + if (isDefault(group)) " (default)" else ""
        /** Fills [target] with [rows], in two columns when there is room. */
        fun fillColumns(target: LinearLayout, rows: List<View>) {
            if (!wide || rows.size < 2) {
                rows.forEach { target.addView(it) }
                return
            }
            val left = column()
            val right = column()
            val half = (rows.size + 1) / 2
            rows.forEachIndexed { i, row -> (if (i < half) left else right).addView(row) }
            target.addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(ctx, 8) })
                addView(right, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(ctx, 8) })
            })
        }
        lateinit var render: () -> Unit

        fun header(text: String) = TextView(ctx).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(ctx, 8), dp(ctx, 12), dp(ctx, 8), dp(ctx, 4))
        }

        /** A list row: color circle (opens the color picker), optional folder icon, name, ✕. */
        fun row(color: Int, title: String, onClick: () -> Unit, onRemove: (() -> Unit)?, onColor: () -> Unit, folder: Boolean = false) = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(ctx, 52)
            setPadding(dp(ctx, 4), 0, 0, 0)
            background = selectableBackground(ctx)
            isClickable = true
            setOnClickListener { onClick() }
            // Bigger touch area around the circle.
            addView(android.widget.FrameLayout(ctx).apply {
                background = selectableBackground(ctx, borderless = true)
                isClickable = true
                contentDescription = "Change color of $title"
                setOnClickListener { onColor() }
                addView(View(ctx).apply { background = swatch(ctx, color, 28) },
                    android.widget.FrameLayout.LayoutParams(dp(ctx, 28), dp(ctx, 28), Gravity.CENTER))
            }, LinearLayout.LayoutParams(dp(ctx, 44), dp(ctx, 44)))
            if (folder) addView(android.widget.ImageView(ctx).apply {
                setImageResource(R.drawable.xtra5_folder)
            }, LinearLayout.LayoutParams(dp(ctx, 22), dp(ctx, 22)).apply { marginStart = dp(ctx, 8) })
            addView(TextView(ctx).apply {
                text = title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setPadding(dp(ctx, 16), 0, dp(ctx, 8), 0)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (onRemove != null) addView(TextView(ctx).apply {
                text = "✕"
                contentDescription = "Remove $title"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                gravity = Gravity.CENTER
                background = selectableBackground(ctx, borderless = true)
                setOnClickListener { onRemove() }
            }, LinearLayout.LayoutParams(dp(ctx, 48), dp(ctx, 48)))
        }

        fun choose(title: String, options: List<String>, onChosen: (Int) -> Unit) {
            panelBuilder(ctx).setTitle(title)
                .setItems(options.toTypedArray()) { _, which -> onChosen(which) }
                .setNegativeButton(android.R.string.cancel, null)
                .show().also { ChatLive.placeBesideChat(it, dp(ctx, 420)) }
        }

        /** Move [name] into a group (uses its color) or to ungrouped (keeps [currentColor]). */
        fun moveUser(name: String, currentColor: Int, after: () -> Unit) {
            val targets = listOf("Ungrouped (keep color)") + groups.map { it.name }
            choose("Move $name to", targets) { which ->
                detach(name)
                if (which == 0) entries.add(name to currentColor) else groups[which - 1].members.add(name)
                save(); render(); after()
            }
        }

        /** Color circle of a user. In a group, a new color moves them to Ungrouped (group color is master). */
        fun userColor(name: String, group: UserHighlights.Group?, after: () -> Unit) {
            val currentColor = group?.color ?: entries.firstOrNull { it.first.equals(name, true) }?.second ?: UserHighlights.getDefaultColor(ctx)
            showColorPicker(ctx, name, name, currentColor, liveUser = name) { picked ->
                if (group == null) {
                    val index = entries.indexOfFirst { it.first.equals(name, true) }
                    if (index >= 0) entries[index] = entries[index].first to picked else entries.add(name to picked)
                    save(); render(); after()
                } else {
                    panelBuilder(ctx)
                        .setMessage("$name will be moved to Ungrouped")
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            detach(name)
                            entries.add(name to picked)
                            save(); render(); after()
                        }
                        .setNegativeButton(android.R.string.cancel) { _, _ -> ChatLive.refreshChat() }
                        .show().also { ChatLive.placeBesideChat(it, dp(ctx, 420)) }
                }
            }
        }

        /** Tapping a user's name: rename, move or remove. */
        fun userOptions(name: String, group: UserHighlights.Group?, after: () -> Unit) {
            val currentColor = group?.color ?: entries.firstOrNull { it.first.equals(name, true) }?.second ?: UserHighlights.getDefaultColor(ctx)
            choose(name, listOf("Rename", "Move to…", "Remove")) { which ->
                when (which) {
                    0 -> showTextInput(ctx, "Rename $name", name, "Username") { input ->
                        val newName = input.removePrefix("@").replace(Regex("[\\s,;#|]"), "")
                        if (newName.isEmpty() || newName == name) return@showTextInput
                        // Same place, same color; drop any other entry that already had the new name.
                        groups.forEach { g -> g.members.removeAll { it.equals(newName, true) && !it.equals(name, true) } }
                        entries.removeAll { it.first.equals(newName, true) && !it.first.equals(name, true) }
                        if (group != null) {
                            val i = group.members.indexOfFirst { it.equals(name, true) }
                            if (i >= 0) group.members[i] = newName
                        } else {
                            val i = entries.indexOfFirst { it.first.equals(name, true) }
                            if (i >= 0) entries[i] = newName to entries[i].second
                        }
                        save(); render(); after()
                    }
                    1 -> moveUser(name, currentColor, after)
                    2 -> { detach(name); save(); render(); after() }
                }
            }
        }

        fun addUser(defaultGroup: UserHighlights.Group?, after: () -> Unit) {
            showAddUser(ctx) { name ->
                fun addTo(group: UserHighlights.Group?) {
                    if (group != null) {
                        detach(name); group.members.add(name); save(); render(); after()
                    } else {
                        showColorPicker(ctx, name, name, UserHighlights.getDefaultColor(ctx), liveUser = name) { picked ->
                            detach(name); entries.add(name to picked); save(); render(); after()
                        }
                    }
                }
                when {
                    defaultGroup != null -> addTo(defaultGroup)
                    groups.isEmpty() -> addTo(null)
                    else -> choose("Add $name to", listOf("Ungrouped (pick a color)") + groups.map { it.name }) { which ->
                        addTo(if (which == 0) null else groups[which - 1])
                    }
                }
            }
        }

        /** Color circle of a group: recolors every member (live in the chat). */
        fun groupColor(group: UserHighlights.Group, after: () -> Unit) {
            showColorPicker(ctx, group.name, group.members.firstOrNull() ?: group.name, group.color, liveUsers = group.members.toList()) { picked ->
                group.color = picked; save(); render(); after()
            }
        }

        fun showGroup(group: UserHighlights.Group) {
            val gBuilder = panelBuilder(ctx)
            val gctx = gBuilder.context
            val members = LinearLayout(gctx).apply { orientation = LinearLayout.VERTICAL }
            lateinit var renderGroup: () -> Unit
            renderGroup = {
                members.removeAllViews()
                if (group.members.isEmpty()) members.addView(TextView(gctx).apply {
                    text = "No users in this group yet."
                    setPadding(dp(gctx, 8), dp(gctx, 16), dp(gctx, 8), dp(gctx, 16))
                })
                fillColumns(members, group.members.toList().map { member ->
                    row(group.color, member, { userOptions(member, group) { renderGroup() } }, {
                        group.members.remove(member); save(); render(); renderGroup()
                    }, { userColor(member, group) { renderGroup() } })
                })
            }
            renderGroup()
            val dialog = gBuilder.setTitle(groupTitle(group))
                .setView(ScrollView(gctx).apply {
                    setPadding(dp(gctx, 16), dp(gctx, 8), dp(gctx, 16), 0)
                    addView(members)
                })
                .setPositiveButton("Done", null)
                .setNeutralButton("Add user", null)
                .setNegativeButton("Group…", null)
                .show()
            ChatLive.placeBesideChat(dialog, if (wide && group.members.size > 1) panelWidth else dp(gctx, 420))
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { addUser(group) { renderGroup() } }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                val actions = listOf("Change group color", "Rename group", if (isDefault(group)) "Unset default group" else "Set as default group", "Delete group")
                choose(group.name, actions) { which ->
                    when (which) {
                        0 -> groupColor(group) { renderGroup() }
                        1 -> showTextInput(gctx, "Rename group", group.name, "Group name") { name ->
                            val wasDefault = isDefault(group)
                            group.name = name
                            if (wasDefault) setDefault(name)
                            save(); render(); dialog.setTitle(groupTitle(group))
                        }
                        // Long-pressing a chatter in the chat adds them to (or removes them from) this group.
                        2 -> {
                            setDefault(if (isDefault(group)) null else group.name)
                            render(); dialog.setTitle(groupTitle(group))
                        }
                        3 -> choose("Delete ${group.name}?", listOf("Keep its users as ungrouped", "Remove its users too")) { keep ->
                            if (keep == 0) group.members.forEach { m -> entries.add(m to group.color) }
                            if (isDefault(group)) setDefault(null)
                            groups.remove(group); save(); render(); dialog.dismiss()
                        }
                    }
                }
            }
        }

        render = {
            val empty = groups.isEmpty() && entries.isEmpty()
            if (wide) {
                groupColumn.removeAllViews()
                userColumn.removeAllViews()
                while (list.childCount > 1) list.removeViewAt(1)
            } else {
                list.removeAllViews()
            }
            val groupTarget = if (wide) groupColumn else list
            val userTarget = if (wide) userColumn else list
            fun note(target: LinearLayout, message: String) = target.addView(TextView(ctx).apply {
                text = message
                setPadding(dp(ctx, 8), dp(ctx, if (wide) 4 else 16), dp(ctx, 8), dp(ctx, 16))
            })
            if (empty && !wide) note(list, "No highlighted users yet.")
            if (groups.isNotEmpty() || wide) groupTarget.addView(header("Groups"))
            if (wide && groups.isEmpty()) note(groupTarget, "No groups yet.")
            groups.forEach { group ->
                val count = group.members.size
                groupTarget.addView(row(group.color, "${groupTitle(group)} ($count user${if (count == 1) "" else "s"})", { showGroup(group) }, null, { groupColor(group) {} }, folder = true))
            }
            if (entries.isNotEmpty() || wide) {
                if (groups.isNotEmpty() || wide) userTarget.addView(header("Ungrouped"))
            }
            if (wide && entries.isEmpty()) note(userTarget, "No ungrouped users.")
            entries.toList().forEach { (user, color) ->
                val rowColor = color ?: UserHighlights.getDefaultColor(ctx)
                userTarget.addView(row(rowColor, user, { userOptions(user, null) {} }, {
                    detach(user); save(); render()
                }, { userColor(user, null) {} }))
            }
        }
        render()
        val dialog = builder
            .setTitle("Highlighted users")
            .setView(content)
            .setPositiveButton("Done", null)
            .setNeutralButton("Add user", null)
            .setNegativeButton("New group", null)
            .show()
        ChatLive.placeBesideChat(dialog, panelWidth)
        // Keep the list open after these.
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { addUser(null) {} }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
            showTextInput(ctx, "New group", "", "Group name") { name ->
                showColorPicker(ctx, name, name, UserHighlights.getDefaultColor(ctx)) { picked ->
                    val group = UserHighlights.Group(name, picked, mutableListOf())
                    groups.add(group); save(); render()
                    showGroup(group)
                }
            }
        }
    }

    private fun showTextInput(context: Context, title: String, initial: String, hintText: String, onDone: (String) -> Unit) {
        val builder = panelBuilder(context)
        val ctx = builder.context
        val input = EditText(ctx).apply {
            hint = hintText
            isSingleLine = true
            setText(initial)
            setSelection(initial.length)
        }
        val frame = LinearLayout(ctx).apply {
            setPadding(dp(ctx, 24), dp(ctx, 8), dp(ctx, 24), 0)
            addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        builder.setTitle(title)
            .setView(frame)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) onDone(text)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
            .also { ChatLive.placeBesideChat(it, dp(ctx, 420)) }
        input.requestFocus()
    }

    private fun showAddUser(context: Context, onAdded: (String) -> Unit) {
        val builder = panelBuilder(context)
        val ctx = builder.context
        val input = EditText(ctx).apply {
            hint = "Username"
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        val frame = LinearLayout(ctx).apply {
            setPadding(dp(ctx, 24), dp(ctx, 8), dp(ctx, 24), 0)
            addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        builder.setTitle("Add user")
            .setView(frame)
            .setPositiveButton("Next") { _, _ ->
                val name = input.text.toString().trim().removePrefix("@").replace(Regex("[\\s,;#]"), "")
                if (name.isNotEmpty()) onAdded(name)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
            .also { ChatLive.placeBesideChat(it, dp(ctx, 420)) }
        input.requestFocus()
    }

    fun showColorPicker(context: Context, title: String, previewName: String, initial: Int, liveUser: String? = null, liveUsers: List<String> = emptyList(), onPicked: (Int) -> Unit) {
        val live = (listOfNotNull(liveUser) + liveUsers).map { it.lowercase() }
        val builder = panelBuilder(context)
        val ctx = builder.context
        val hsv = FloatArray(3).also { Color.colorToHSV(initial, it) }
        var alpha = Color.alpha(initial)
        var updating = false

        val metrics = ctx.resources.displayMetrics
        // Beside an open chat the picker only gets the space left of it: three columns only if
        // that space is wide enough, otherwise the stacked layout.
        val besideChat = ChatLive.usableWidthBesideChat(ctx)
        val landscape = metrics.widthPixels > metrics.heightPixels && (besideChat == null || besideChat >= dp(ctx, 720))
        // TV mode shortcuts: the panel may be nearly screen-tall, and everything is fitted inside
        // it (smaller wheel, wheel beside the controls on a wide screen) so nothing needs scrolling.
        val tv = tvPanels(ctx)
        val fitHeight = metrics.heightPixels - dp(ctx, 2 * TV_INSET_DP + 140) // minus title and buttons
        val sideBySide = tv && !landscape && metrics.widthPixels > metrics.heightPixels

        // Chat preview: a normal message, the highlighted one, another normal message, styled like
        // the real chat (background, font, username size and bold setting).
        fun chatLine(name: String, nameColor: Int) = TextView(ctx).apply {
            val bold = ctx.prefs().getBoolean("chat_boldnames", false)
            text = SpannableStringBuilder().apply {
                append(name)
                setSpan(ForegroundColorSpan(nameColor), 0, name.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (bold) setSpan(StyleSpan(Typeface.BOLD), 0, name.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                ChatAppearance.styleUserName(ctx, this, 0, name.length)
                append(": Hello chat!")
            }
            // Same text size as the chat (chat text size x chat size modifier).
            val chatTextSize = (ctx.prefs().getString("chat_text_size", "14")?.toFloatOrNull() ?: 14f) *
                (ctx.prefs().getInt("chat_size_modifier", 100) / 100f)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, chatTextSize)
            ChatAppearance.chatTypeface(ctx)?.let { typeface = it }
            setPadding(dp(ctx, 8), dp(ctx, 4), dp(ctx, 8), dp(ctx, 4))
        }
        val preview = chatLine(previewName, 0xFF1E90FF.toInt())
        val previewBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ChatLive.chatBackground() ?: themeColor(ctx, android.R.attr.colorBackground))
            setPadding(0, dp(ctx, 4), 0, dp(ctx, 4))
            addView(chatLine("chatter_one", 0xFFFF7F50.toInt()))
            addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(chatLine("chatter_two", 0xFF9ACD32.toInt()))
        }
        val wheel = ColorWheelView(ctx)
        val swatches = ArrayList<Pair<View, Int>>()
        // Landscape: color circles in a grid 5 across; portrait: one scrollable row.
        fun circleGrid() = GridLayout(ctx).apply { columnCount = if (landscape) 5 else 99 }
        fun circleParams() = GridLayout.LayoutParams().apply {
            width = dp(ctx, 32); height = dp(ctx, 32)
            setMargins(dp(ctx, 4), dp(ctx, 4), dp(ctx, 4), dp(ctx, 4))
        }
        fun circleHolder(grid: View): ViewGroup = if (landscape) {
            LinearLayout(ctx).apply { addView(grid) }
        } else {
            HorizontalScrollView(ctx).apply {
                isHorizontalScrollBarEnabled = false
                addView(grid)
            }
        }
        val swatchRow = circleGrid()
        val hex = EditText(ctx).apply {
            isSingleLine = true
            typeface = Typeface.MONOSPACE
            hint = "#RRGGBB"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            filters = arrayOf(InputFilter.LengthFilter(9))
        }
        lateinit var bright: Slider
        lateinit var opacity: Slider

        fun current() = Color.HSVToColor(alpha, hsv)
        /** Pushes the current color to every control except [source], which the user is touching. */
        // Saved colors (color + opacity), shared by every picker.
        val savedRow = circleGrid()
        val savedLabel = TextView(ctx).apply {
            text = "Saved (long-press to remove)"
            setPadding(dp(ctx, 4), dp(ctx, 8), 0, 0)
        }
        val savedScroll = circleHolder(savedRow)
        lateinit var applySaved: (Int) -> Unit
        lateinit var removeSaved: (Int) -> Unit
        fun renderSaved(current: Int) {
            val saved = getSavedColors(ctx)
            savedLabel.visibility = if (saved.isEmpty()) View.GONE else View.VISIBLE
            savedScroll.visibility = savedLabel.visibility
            savedRow.removeAllViews()
            saved.forEach { color ->
                savedRow.addView(View(ctx).apply {
                    background = swatch(ctx, color, 32, selected = color == current)
                    contentDescription = UserHighlights.toHex(color)
                    setOnClickListener { applySaved(color) }
                    setOnLongClickListener { removeSaved(color); true }
                }, circleParams())
            }
        }

        fun refresh(source: Any?) {
            updating = true
            val color = current()
            live.forEach { ChatLive.previewUser(ctx, it, color) } // the real chat, live
            preview.setBackgroundColor(color)
            val rgb = color and 0xFFFFFF
            swatches.forEach { (view, c) -> view.background = swatch(ctx, c or (0xFF shl 24), 32, selected = c == rgb) }
            wheel.setColor(hsv[0], hsv[1], hsv[2])
            if (source !== bright) bright.value = Math.round(hsv[2] * 100).toFloat().coerceIn(0f, 100f)
            if (source !== opacity) opacity.value = Math.round(alpha * 100f / 255f).toFloat().coerceIn(0f, 100f)
            renderSaved(color)
            if (source !== hex) {
                hex.setText(String.format("#%06X", rgb))
                hex.setSelection(hex.text.length)
            }
            updating = false
        }

        applySaved = { color ->
            Color.colorToHSV(color or (0xFF shl 24), hsv)
            alpha = Color.alpha(color)
            refresh(null)
        }
        removeSaved = { color ->
            panelBuilder(context)
                .setMessage("Remove this saved color?")
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    setSavedColors(ctx, getSavedColors(ctx).filter { it != color })
                    refresh(null)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        wheel.onChanged = { h, sat ->
            hsv[0] = h
            hsv[1] = sat
            if (hsv[2] < 0.05f) hsv[2] = 1f // picking a color while fully dark would show nothing
            refresh(wheel)
        }
        palette.forEach { rgb ->
            val view = View(ctx).apply {
                contentDescription = String.format("#%06X", rgb)
                setOnClickListener {
                    Color.colorToHSV(rgb or (0xFF shl 24), hsv)
                    refresh(null)
                }
            }
            swatches.add(view to rgb)
            swatchRow.addView(view, circleParams())
        }
        hex.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (updating) return
                val value = s?.toString()?.trim()?.removePrefix("#") ?: return
                val parsed = value.toLongOrNull(16) ?: return
                when (value.length) {
                    6 -> Color.colorToHSV((parsed.toInt()) or (0xFF shl 24), hsv)
                    8 -> {
                        alpha = (parsed shr 24).toInt() and 0xFF
                        Color.colorToHSV(parsed.toInt() or (0xFF shl 24), hsv)
                    }
                    else -> return
                }
                refresh(hex)
            }
        })
        fun slider(label: String, onChange: (Float) -> Unit): Pair<TextView, Slider> {
            val text = TextView(ctx).apply {
                this.text = label
                setPadding(dp(ctx, 4), dp(ctx, 8), 0, 0)
            }
            val s = Slider(ctx).apply {
                valueFrom = 0f
                valueTo = 100f
                stepSize = 1f // whole numbers
                setLabelFormatter { "${it.toInt()}%" }
            }
            s.addOnChangeListener { _, value, fromUser ->
                if (fromUser && !updating) {
                    onChange(value)
                    refresh(s)
                }
            }
            return text to s
        }
        val (brightLabel, b) = slider("Brightness") { hsv[2] = it / 100f }; bright = b
        val (opacityLabel, o) = slider("Opacity (lower keeps text readable)") { alpha = (it * 255f / 100f).toInt() }; opacity = o

        val hexRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(ctx).apply {
                text = "Hex"
                setPadding(dp(ctx, 4), 0, dp(ctx, 12), 0)
            })
            addView(hex, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "Save color"
                setOnClickListener {
                    val color = current()
                    setSavedColors(ctx, (listOf(color) + getSavedColors(ctx).filter { it != color }).take(MAX_SAVED))
                    refresh(null)
                    Toast.makeText(ctx, "Color saved", Toast.LENGTH_SHORT).show()
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(ctx, 8) })
        }
        // Room left for the wheel after the dialog title, buttons and the other controls.
        wheel.maxSizePx = if (landscape) {
            (metrics.heightPixels - dp(ctx, 190)).coerceIn(dp(ctx, 140), dp(ctx, 260)).let { if (tv) minOf(it, fitHeight).coerceAtLeast(dp(ctx, 100)) else it }
        } else if (sideBySide) {
            previewBox.measure(View.MeasureSpec.makeMeasureSpec(dp(ctx, 240), View.MeasureSpec.AT_MOST), View.MeasureSpec.UNSPECIFIED)
            (fitHeight - previewBox.measuredHeight - dp(ctx, 16)).coerceIn(dp(ctx, 100), dp(ctx, 240))
        } else {
            (metrics.heightPixels - dp(ctx, if (besideChat != null) 470 else 590)).coerceIn(dp(ctx, 120), dp(ctx, 240))
        }
        fun matchWidth(top: Int = 0) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = top }
        val presetsLabel = TextView(ctx).apply {
            text = "Presets"
            setPadding(dp(ctx, 4), dp(ctx, 8), 0, 0)
        }
        val presets = circleHolder(swatchRow)
        val controls = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            if (landscape) addView(previewBox, matchWidth())
            addView(hexRow, matchWidth(dp(ctx, if (landscape) 4 else 8)))
            addView(brightLabel)
            addView(bright, matchWidth())
            addView(opacityLabel)
            addView(opacity, matchWidth())
            if (!landscape) {
                addView(savedLabel)
                addView(savedScroll)
                addView(presetsLabel)
                addView(presets)
            }
        }
        val content = if (landscape) {
            // Wheel | preview, hex and sliders | saved and preset colors (5 across). Only the
            // colors column scrolls, if it is ever taller than the screen.
            val colors = MaxHeightScrollView(ctx, (if (tv) fitHeight else metrics.heightPixels - dp(ctx, 170)).coerceAtLeast(dp(ctx, 200))).apply {
                addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(savedLabel)
                    addView(savedScroll)
                    addView(presetsLabel)
                    addView(presets)
                })
            }
            LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP // all three columns start at the same height
                setPadding(dp(ctx, 20), dp(ctx, 4), dp(ctx, 20), 0)
                addView(wheel, LinearLayout.LayoutParams(wheel.maxSizePx, wheel.maxSizePx).apply { marginEnd = dp(ctx, 20) })
                addView(controls, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(colors, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(ctx, 20) })
            }
        } else if (sideBySide) {
            // Preview and wheel | hex, sliders, saved and preset colors.
            LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
                setPadding(dp(ctx, 20), dp(ctx, 8), dp(ctx, 20), 0)
                addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(previewBox, LinearLayout.LayoutParams(wheel.maxSizePx, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(ctx, 8) })
                    addView(wheel, LinearLayout.LayoutParams(wheel.maxSizePx, wheel.maxSizePx))
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(ctx, 20) })
                addView(MaxHeightScrollView(ctx, fitHeight.coerceAtLeast(dp(ctx, 200))).apply { addView(controls) },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
        } else {
            LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(ctx, 20), dp(ctx, 8), dp(ctx, 20), 0)
                addView(previewBox, matchWidth().apply { bottomMargin = dp(ctx, 8) })
                addView(wheel, matchWidth().apply { gravity = Gravity.CENTER_HORIZONTAL })
                addView(controls, matchWidth())
            }
        }
        refresh(null)
        val dialog = builder.setTitle(title)
            .setView(ScrollView(ctx).apply { addView(content) }) // fallback for very small screens
            .setPositiveButton(android.R.string.ok) { _, _ -> onPicked(current()) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        dialog.setOnDismissListener { if (live.isNotEmpty()) ChatLive.refreshChat() } // OK saved it; Cancel puts it back
        if (besideChat != null) {
            ChatLive.placeBesideChat(dialog, if (landscape) dp(ctx, 1000) else if (sideBySide) dp(ctx, 720) else dp(ctx, 460))
        } else if (landscape || sideBySide) {
            dialog.window?.setLayout(
                (metrics.widthPixels * 0.9f).toInt().coerceAtMost(dp(ctx, 1000)),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private const val PREF_SAVED_COLORS = "xtra5_saved_colors"
    private const val MAX_SAVED = 12

    private fun getSavedColors(context: Context): List<Int> =
        context.prefs().getString(PREF_SAVED_COLORS, null)?.split(',')?.mapNotNull { UserHighlights.parseColor(it) }?.distinct() ?: emptyList()

    private fun setSavedColors(context: Context, colors: List<Int>) {
        context.prefs().edit { putString(PREF_SAVED_COLORS, colors.joinToString(",") { UserHighlights.toHex(it) }) }
    }

    fun swatch(context: Context, color: Int, sizeDp: Int, selected: Boolean = false): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setSize(dp(context, sizeDp), dp(context, sizeDp))
            val stroke = TypedValue().let { tv ->
                context.theme.resolveAttribute(android.R.attr.textColorPrimary, tv, true)
                if (tv.resourceId != 0) context.getColorStateList(tv.resourceId).defaultColor else tv.data
            }
            setStroke(dp(context, if (selected) 3 else 1), if (selected) stroke else (stroke and 0x00FFFFFF) or 0x40000000)
        }

    /** A ScrollView that is only as tall as its content, up to [maxHeight] (then it scrolls). */
    private class MaxHeightScrollView(context: Context, private val maxHeight: Int) : ScrollView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST))
        }
    }

    private fun themeColor(context: Context, attr: Int): Int {
        val tv = TypedValue()
        context.theme.resolveAttribute(attr, tv, true)
        return if (tv.resourceId != 0) context.getColor(tv.resourceId) else tv.data
    }

    private fun selectableBackground(context: Context, borderless: Boolean = false) = TypedValue().let { tv ->
        context.theme.resolveAttribute(if (borderless) android.R.attr.selectableItemBackgroundBorderless else android.R.attr.selectableItemBackground, tv, true)
        context.getDrawable(tv.resourceId)
    }

    private fun dp(context: Context, value: Int) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics).toInt()
}
