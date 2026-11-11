package com.github.andreyasadchy.xtra.custom

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.player.PlayerFragment

/**
 * Xtra5 custom: rewind / fast-forward indicator for TV mode Left/Right skips.
 * Rewind shows at the left center of the video, fast-forward at the right center.
 * Quick repeated presses add up ("10s", "20s", "30s"...). Fades out 0.7 s after the last press.
 * Drawn over the player by adding views to it at runtime, so no upstream layout changes are needed.
 */
object SeekIndicator {

    private const val TAG_REWIND = "xtra5_seek_rewind"
    private const val TAG_FORWARD = "xtra5_seek_forward"
    private const val VISIBLE_MS = 700L

    private class Indicator(val root: LinearLayout, val label: TextView) {
        var total = 0
        val hide = Runnable {
            root.animate().alpha(0f).setDuration(250L).withEndAction {
                root.visibility = View.GONE
                total = 0
            }.start()
        }
    }

    fun show(player: PlayerFragment, forward: Boolean, seconds: Int) {
        val container = player.view?.findViewById<FrameLayout>(R.id.playerLayout) ?: return
        val indicator = get(container, forward)
        get(container, !forward).let { other -> // switching direction hides the other side at once
            other.root.removeCallbacks(other.hide)
            other.root.animate().cancel()
            other.root.visibility = View.GONE
            other.total = 0
        }
        indicator.total = if (indicator.root.visibility == View.VISIBLE) indicator.total + seconds else seconds
        indicator.label.text = "${indicator.total}s"
        indicator.root.removeCallbacks(indicator.hide)
        indicator.root.animate().cancel()
        if (indicator.root.visibility != View.VISIBLE) {
            indicator.root.alpha = 0f
            indicator.root.visibility = View.VISIBLE
        }
        indicator.root.bringToFront()
        indicator.root.animate().alpha(1f).setDuration(100L).start()
        indicator.root.postDelayed(indicator.hide, VISIBLE_MS)
    }

    private fun get(container: FrameLayout, forward: Boolean): Indicator {
        val tag = if (forward) TAG_FORWARD else TAG_REWIND
        container.findViewWithTag<LinearLayout>(tag)?.let { return it.getTag(R.id.playerLayout) as Indicator }
        val context = container.context
        fun dp(value: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics).toInt()
        val size = dp(120)
        val label = TextView(context).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        val root = LinearLayout(context).apply {
            this.tag = tag
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            isClickable = false
            isFocusable = false
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x80000000.toInt())
            }
            addView(ImageView(context).apply {
                setImageResource(if (forward) androidx.media3.ui.R.drawable.exo_styled_controls_fastforward else androidx.media3.ui.R.drawable.exo_styled_controls_rewind)
                setColorFilter(Color.WHITE)
            }, LinearLayout.LayoutParams(dp(56), dp(56)))
            addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val indicator = Indicator(root, label)
        root.setTag(R.id.playerLayout, indicator)
        container.addView(root, FrameLayout.LayoutParams(size, size).apply {
            gravity = Gravity.CENTER_VERTICAL or if (forward) Gravity.END else Gravity.START
            marginStart = dp(48)
            marginEnd = dp(48)
        })
        return indicator
    }
}
