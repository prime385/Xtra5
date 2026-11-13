package com.github.andreyasadchy.xtra.custom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.os.BatteryManager
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.player.PlayerFragment
import com.github.andreyasadchy.xtra.util.prefs

/**
 * Xtra5 custom: battery percentage in fullscreen video (Settings > Player > Battery percentage).
 *
 * Shown at the top-right corner of the video, right of the ⋮ menu (the top-right player buttons
 * move left to make room), only while the player is full-size in fullscreen (landscape). It stays
 * in place when the controls hide: full opacity while the controls are showing, faded to the
 * chosen opacity 4 seconds after they were last shown. A white bolt shows while charging. Hidden
 * on devices without a battery and whenever TV mode shortcuts are on.
 *
 * Kept in its own package so upstream Xtra updates can be merged without conflicts.
 * Started from UpstreamUpdates.onMainActivityCreate (no extra upstream hook).
 */
object BatteryIndicator {

    const val PREF_ENABLED = "xtra5_battery_show"
    const val PREF_FADED_OPACITY = "xtra5_battery_faded_opacity"
    private const val FADE_AFTER_MS = 4000L

    fun register(activity: MainActivity) {
        activity.supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?) {
                if (f is PlayerFragment) attach(f, v)
            }
        }, true)
    }

    private class Indicator(val player: PlayerFragment, val text: TextView, val container: FrameLayout, val controls: View, val buttons: View) {
        var controlsShownAt = 0L
        var lastControlsVisible = false
        val originalButtonsMarginEnd = (buttons.layoutParams as? ViewGroup.MarginLayoutParams)?.marginEnd ?: 0
        val fade = Runnable { text.animate().alpha(fadedAlpha()).setDuration(400L).start() }

        fun fadedAlpha() = text.context.prefs().getInt(PREF_FADED_OPACITY, 40).coerceIn(0, 100) / 100f
    }

    private fun attach(player: PlayerFragment, view: View) {
        val container = view.findViewById<FrameLayout>(R.id.playerLayout) ?: return
        val controls = view.findViewById<View>(R.id.playerControls) ?: return
        val buttons = view.findViewById<View>(R.id.topRightLayout) ?: return
        if (container.findViewById<View>(R.id.xtra5_battery) != null) return
        val context = view.context
        fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics).toInt()
        val text = TextView(context).apply {
            id = R.id.xtra5_battery
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setShadowLayer(3f, 0f, 0f, 0x99000000.toInt())
            gravity = Gravity.CENTER_VERTICAL
            compoundDrawablePadding = dp(2)
            visibility = View.GONE
            isClickable = false
            isFocusable = false
        }
        container.addView(text, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.END
            marginEnd = dp(10)
        })
        val indicator = Indicator(player, text, container, controls, buttons)

        // Battery level / charging state.
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) = updateBattery(indicator, intent)
        }
        text.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { updateBattery(indicator, it) }
            }

            override fun onViewDetachedFromWindow(v: View) {
                try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
                text.removeCallbacks(indicator.fade)
            }
        })
        // Fullscreen / controls changes.
        container.viewTreeObserver.addOnGlobalLayoutListener { update(indicator) }
    }

    private fun updateBattery(indicator: Indicator, intent: Intent) {
        val present = intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true)
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val text = indicator.text
        text.tag = present && level >= 0 && scale > 0
        if (text.tag == true) {
            text.text = "${level * 100 / scale}%"
            val size = (text.textSize * 1.05f).toInt()
            val bolt = if (charging) text.context.getDrawable(R.drawable.xtra5_bolt)?.mutate()?.apply {
                setTint(Color.WHITE)
                setBounds(0, 0, size, size)
            } else null
            text.setCompoundDrawables(bolt, null, null, null)
        }
        update(indicator)
    }

    private fun update(indicator: Indicator) {
        val text = indicator.text
        val player = indicator.player
        val context = text.context
        val prefs = context.prefs()
        val show = prefs.getBoolean(PREF_ENABLED, false) &&
            !prefs.getBoolean(TvShortcuts.PREF_ENABLED, false) &&
            text.tag == true && player.isAdded &&
            player.xtra5PlayerState().isMaximized &&
            context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        // Make room right of the ⋮ button (or give it back).
        val params = indicator.buttons.layoutParams as? ViewGroup.MarginLayoutParams
        val wantedMargin = if (show) indicator.originalButtonsMarginEnd + text.width + (6 * context.resources.displayMetrics.density).toInt() else indicator.originalButtonsMarginEnd
        if (params != null && params.marginEnd != wantedMargin && (!show || text.width > 0)) {
            params.marginEnd = wantedMargin
            indicator.buttons.layoutParams = params
        }
        if (!show) {
            if (text.isVisible) {
                text.visibility = View.GONE
                text.removeCallbacks(indicator.fade)
            }
            return
        }
        if (!text.isVisible) {
            text.visibility = View.VISIBLE
            text.alpha = 1f
            indicator.lastControlsVisible = !indicator.controls.isVisible // force the state below
        }
        // Line up with the top-right buttons.
        val buttonsTop = indicator.buttons.top + indicator.controls.top
        val top = buttonsTop + (indicator.buttons.height - text.height) / 2
        (text.layoutParams as? FrameLayout.LayoutParams)?.let {
            if (it.topMargin != top && top >= 0) {
                it.topMargin = top
                text.layoutParams = it
            }
        }
        // Opacity follows the controls: full while showing, faded 4 s after they were last shown.
        val controlsVisible = indicator.controls.isVisible && indicator.controls.alpha > 0f
        if (controlsVisible != indicator.lastControlsVisible) {
            indicator.lastControlsVisible = controlsVisible
            text.removeCallbacks(indicator.fade)
            if (controlsVisible) {
                indicator.controlsShownAt = System.currentTimeMillis()
                text.animate().cancel()
                text.alpha = 1f
            } else {
                val elapsed = System.currentTimeMillis() - indicator.controlsShownAt
                text.postDelayed(indicator.fade, (FADE_AFTER_MS - elapsed).coerceAtLeast(0L))
            }
        }
    }
}
