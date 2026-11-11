package com.github.andreyasadchy.xtra.custom

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.Keep
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.preference.Preference
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.prefs
import com.google.android.material.slider.Slider

/**
 * Xtra5 custom: settings UI for user highlights (user list editor and color picker).
 * Referenced by class name from res/xml/chat_preferences.xml, so no upstream code hooks are needed.
 */
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
        summary = when {
            users.isEmpty() -> "No users yet. Tap to add"
            users.size <= 4 -> users.joinToString(", ") { it.first }
            else -> users.take(4).joinToString(", ") { it.first } + " and ${users.size - 4} more"
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

    fun showUserList(context: Context, onChanged: () -> Unit) {
        val prefs = context.prefs()
        val entries = UserHighlights.parseEntries(prefs.getString(UserHighlights.PREF_USERS, null)).toMutableList()
        fun save() {
            prefs.edit { putString(UserHighlights.PREF_USERS, UserHighlights.serializeEntries(entries)) }
            onChanged()
        }
        val builder = context.getAlertDialogBuilder()
        val ctx = builder.context
        val list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val content = ScrollView(ctx).apply {
            setPadding(dp(ctx, 16), dp(ctx, 8), dp(ctx, 16), 0)
            addView(list)
        }
        lateinit var render: () -> Unit
        render = {
            list.removeAllViews()
            if (entries.isEmpty()) {
                list.addView(TextView(ctx).apply {
                    text = "No highlighted users yet."
                    setPadding(dp(ctx, 8), dp(ctx, 16), dp(ctx, 8), dp(ctx, 16))
                })
            }
            entries.forEachIndexed { index, (user, color) ->
                val rowColor = color ?: UserHighlights.getDefaultColor(ctx)
                list.addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = dp(ctx, 52)
                    setPadding(dp(ctx, 8), 0, 0, 0)
                    background = selectableBackground(ctx)
                    isClickable = true
                    setOnClickListener {
                        showColorPicker(ctx, user, user, rowColor) { picked ->
                            entries[index] = user to picked
                            save()
                            render()
                        }
                    }
                    addView(View(ctx).apply { background = swatch(ctx, rowColor, 28) }, LinearLayout.LayoutParams(dp(ctx, 28), dp(ctx, 28)))
                    addView(TextView(ctx).apply {
                        text = user
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                        setPadding(dp(ctx, 16), 0, dp(ctx, 8), 0)
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(TextView(ctx).apply {
                        text = "✕"
                        contentDescription = "Remove $user"
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                        gravity = Gravity.CENTER
                        background = selectableBackground(ctx, borderless = true)
                        setOnClickListener {
                            entries.removeAt(index)
                            save()
                            render()
                        }
                    }, LinearLayout.LayoutParams(dp(ctx, 48), dp(ctx, 48)))
                })
            }
        }
        render()
        val dialog = builder
            .setTitle("Highlighted users")
            .setView(content)
            .setPositiveButton("Done", null)
            .setNeutralButton("Add user", null)
            .show()
        // Keep the list open after adding a user.
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            showAddUser(ctx) { name ->
                if (entries.none { it.first.equals(name, true) }) {
                    val initial = UserHighlights.getDefaultColor(ctx)
                    showColorPicker(ctx, name, name, initial) { picked ->
                        entries.add(name to picked)
                        save()
                        render()
                    }
                }
            }
        }
    }

    private fun showAddUser(context: Context, onAdded: (String) -> Unit) {
        val builder = context.getAlertDialogBuilder()
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
        input.requestFocus()
    }

    fun showColorPicker(context: Context, title: String, previewName: String, initial: Int, onPicked: (Int) -> Unit) {
        val builder = context.getAlertDialogBuilder()
        val ctx = builder.context
        val hsv = FloatArray(3).also { Color.colorToHSV(initial, it) }
        var alpha = Color.alpha(initial)

        val preview = TextView(ctx).apply {
            text = "$previewName: Hello chat!"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10))
        }
        val swatches = ArrayList<Pair<View, Int>>()
        val grid = GridLayout(ctx).apply {
            columnCount = 6
            useDefaultMargins = false
        }
        lateinit var hue: Slider
        lateinit var sat: Slider
        lateinit var bright: Slider
        lateinit var opacity: Slider
        var updatingSliders = false
        fun current() = Color.HSVToColor(alpha, hsv)
        fun refresh(fromSlider: Boolean) {
            val color = current()
            preview.setBackgroundColor(color)
            val rgb = color and 0xFFFFFF
            swatches.forEach { (view, c) -> view.background = swatch(ctx, Color.HSVToColor(255, FloatArray(3).also { Color.colorToHSV(c or (0xFF shl 24), it) }), 36, selected = c == rgb) }
            if (!fromSlider) {
                updatingSliders = true
                hue.value = hsv[0].coerceIn(0f, 360f)
                sat.value = (hsv[1] * 100).coerceIn(0f, 100f)
                bright.value = (hsv[2] * 100).coerceIn(0f, 100f)
                opacity.value = (alpha * 100f / 255f).coerceIn(0f, 100f)
                updatingSliders = false
            }
        }
        palette.forEach { rgb ->
            val view = View(ctx).apply {
                contentDescription = UserHighlights.toHex(rgb or (0xFF shl 24))
                setOnClickListener {
                    Color.colorToHSV(rgb or (0xFF shl 24), hsv)
                    refresh(false)
                }
            }
            swatches.add(view to rgb)
            grid.addView(view, GridLayout.LayoutParams().apply {
                width = dp(ctx, 36); height = dp(ctx, 36)
                setMargins(dp(ctx, 5), dp(ctx, 5), dp(ctx, 5), dp(ctx, 5))
            })
        }
        fun slider(label: String, max: Float, onChange: (Float) -> Unit): Pair<TextView, Slider> {
            val text = TextView(ctx).apply {
                this.text = label
                setPadding(dp(ctx, 4), dp(ctx, 8), 0, 0)
            }
            val s = Slider(ctx).apply {
                valueFrom = 0f
                valueTo = max
                addOnChangeListener { _, value, fromUser ->
                    if (fromUser && !updatingSliders) {
                        onChange(value)
                        refresh(true)
                    }
                }
            }
            return text to s
        }
        val (hueLabel, h) = slider("Hue", 360f) { hsv[0] = it }; hue = h
        val (satLabel, sa) = slider("Saturation", 100f) { hsv[1] = it / 100f }; sat = sa
        val (brightLabel, b) = slider("Brightness", 100f) { hsv[2] = it / 100f }; bright = b
        val (opacityLabel, o) = slider("Opacity (lower keeps text readable)", 100f) { alpha = (it * 255f / 100f).toInt() }; opacity = o

        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 20), dp(ctx, 8), dp(ctx, 20), 0)
            addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(ctx, 12) })
            addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER_HORIZONTAL })
            listOf(hueLabel to hue, satLabel to sat, brightLabel to bright, opacityLabel to opacity).forEach { (label, s) ->
                addView(label)
                addView(s, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
        }
        refresh(false)
        builder.setTitle(title)
            .setView(ScrollView(ctx).apply { addView(content) })
            .setPositiveButton(android.R.string.ok) { _, _ -> onPicked(current()) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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

    private fun selectableBackground(context: Context, borderless: Boolean = false) = TypedValue().let { tv ->
        context.theme.resolveAttribute(if (borderless) android.R.attr.selectableItemBackgroundBorderless else android.R.attr.selectableItemBackground, tv, true)
        context.getDrawable(tv.resourceId)
    }

    private fun dp(context: Context, value: Int) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics).toInt()
}
