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
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
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
        var updating = false

        val preview = TextView(ctx).apply {
            text = "$previewName: Hello chat!"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10))
        }
        val wheel = ColorWheelView(ctx)
        val swatches = ArrayList<Pair<View, Int>>()
        val swatchRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
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
        fun refresh(source: Any?) {
            updating = true
            val color = current()
            preview.setBackgroundColor(color)
            val rgb = color and 0xFFFFFF
            swatches.forEach { (view, c) -> view.background = swatch(ctx, c or (0xFF shl 24), 32, selected = c == rgb) }
            wheel.setColor(hsv[0], hsv[1], hsv[2])
            if (source !== bright) bright.value = (hsv[2] * 100).coerceIn(0f, 100f)
            if (source !== opacity) opacity.value = (alpha * 100f / 255f).coerceIn(0f, 100f)
            if (source !== hex) {
                hex.setText(String.format("#%06X", rgb))
                hex.setSelection(hex.text.length)
            }
            updating = false
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
            swatchRow.addView(view, LinearLayout.LayoutParams(dp(ctx, 32), dp(ctx, 32)).apply {
                setMargins(dp(ctx, 4), dp(ctx, 4), dp(ctx, 4), dp(ctx, 4))
            })
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
        }
        val metrics = ctx.resources.displayMetrics
        val landscape = metrics.widthPixels > metrics.heightPixels
        // Room left for the wheel after the dialog title, buttons and the other controls.
        wheel.maxSizePx = if (landscape) {
            (metrics.heightPixels - dp(ctx, 190)).coerceIn(dp(ctx, 140), dp(ctx, 260))
        } else {
            (metrics.heightPixels - dp(ctx, 470)).coerceIn(dp(ctx, 120), dp(ctx, 240))
        }
        fun matchWidth(top: Int = 0) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = top }
        val presetsLabel = TextView(ctx).apply {
            text = "Presets"
            setPadding(dp(ctx, 4), dp(ctx, 8), 0, 0)
        }
        val presets = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            addView(swatchRow)
        }
        val controls = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            if (landscape) addView(preview, matchWidth())
            addView(hexRow, matchWidth(dp(ctx, if (landscape) 4 else 8)))
            addView(brightLabel)
            addView(bright, matchWidth())
            addView(opacityLabel)
            addView(opacity, matchWidth())
            addView(presetsLabel)
            addView(presets)
        }
        val content = if (landscape) {
            // Wheel on the left, everything else on the right: no scrolling needed.
            LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(ctx, 20), dp(ctx, 4), dp(ctx, 20), 0)
                addView(wheel, LinearLayout.LayoutParams(wheel.maxSizePx, wheel.maxSizePx).apply { marginEnd = dp(ctx, 20) })
                addView(controls, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
        } else {
            LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(ctx, 20), dp(ctx, 8), dp(ctx, 20), 0)
                addView(preview, matchWidth().apply { bottomMargin = dp(ctx, 8) })
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
        if (landscape) {
            dialog.window?.setLayout(
                (metrics.widthPixels * 0.8f).toInt().coerceAtMost(dp(ctx, 720)),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
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
