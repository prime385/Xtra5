package com.github.andreyasadchy.xtra.custom

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.media3.ui.DefaultTimeBar
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.player.BasePlaybackService
import com.github.andreyasadchy.xtra.ui.player.PlayerFragment
import com.github.andreyasadchy.xtra.util.prefs

/**
 * Xtra5 custom: VOD / clip timeline extras.
 *  - Chapter markers: on VODs with chapters, a marker for each chapter start on the seek bar.
 *  - End time (both timelines): "−time left in the current chapter / total length" (no
 *    chapters, or the last chapter: time left in the video).
 *  - Persistent progress bar (Settings > Player): on VODs, downloads and clips in fullscreen
 *    (landscape), a copy of the seek bar and its start / end times stays in exactly the same
 *    place when the controls hide. It fades with the controls, frame by frame: as they fade out
 *    it fades in to the chosen opacity, and back out as they return. Touches pass through.
 *
 * Kept in its own package so upstream Xtra updates can be merged without conflicts.
 * Started from UpstreamUpdates.onMainActivityCreate; player info from PlayerFragment.xtra5PlayerState.
 */
object ProgressOverlay {

    const val PREF_ENABLED = "xtra5_progress_bar_show"
    const val PREF_FADED_OPACITY = "xtra5_progress_bar_faded_opacity"
    private const val FADE_AFTER_MS = 4000L
    private const val TICK_MS = 1000L

    fun register(activity: MainActivity) {
        activity.supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?) {
                if (f is PlayerFragment) attach(f, v)
            }
        }, true)
    }

    /** A seek bar that only shows progress: touches go to the player underneath. */
    private class DisplayTimeBar(context: Context) : DefaultTimeBar(context) {
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean = false
        override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean = false
    }

    private class State(
        val player: PlayerFragment, val container: FrameLayout, val controls: View, val realBar: DefaultTimeBar, val bar: DisplayTimeBar,
        val realPosition: TextView?, val realDuration: TextView?, val position: TextView, val duration: TextView,
    ) {
        var shown = false
        val handler = Handler(Looper.getMainLooper())
        var chapters: List<Long> = emptyList()
        var attached = false
        val tick = object : Runnable {
            override fun run() {
                if (!attached) return
                update(this@State)
                handler.postDelayed(this, TICK_MS)
            }
        }

        fun fadedAlpha() = bar.context.prefs().getInt(PREF_FADED_OPACITY, 40).coerceIn(0, 100) / 100f
    }

    private fun attach(player: PlayerFragment, view: View) {
        val container = view.findViewById<FrameLayout>(R.id.playerLayout) ?: return
        val controls = view.findViewById<View>(R.id.playerControls) ?: return
        val realBar = view.findViewById<DefaultTimeBar>(R.id.progressBar) ?: return
        if (container.findViewById<View>(R.id.xtra5_progress_bar) != null) return
        val bar = DisplayTimeBar(view.context).apply {
            id = R.id.xtra5_progress_bar
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            visibility = View.GONE
        }
        container.addView(bar, FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.WRAP_CONTENT))
        val realPosition = view.findViewById<TextView>(R.id.position)
        val realDuration = view.findViewById<TextView>(R.id.duration)
        fun timeText(like: TextView?) = TextView(view.context).apply {
            visibility = View.GONE
            setSingleLine() // Xtra5: never wraps onto two lines when the text grows
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            if (like != null) {
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, like.textSize)
                setTextColor(like.textColors)
                typeface = like.typeface
                setShadowLayer(like.shadowRadius, like.shadowDx, like.shadowDy, like.shadowColor)
                setPadding(like.paddingLeft, like.paddingTop, like.paddingRight, like.paddingBottom)
                gravity = like.gravity
            }
            container.addView(this, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        }
        val state = State(player, container, controls, realBar, bar, realPosition, realDuration, timeText(realPosition), timeText(realDuration))
        // Same place as the real seek bar and times: copy their positions whenever laid out.
        realBar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> matchPosition(state) }
        realPosition?.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> matchPosition(state) }
        realDuration?.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> matchPosition(state) }
        container.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = start(state)
            override fun onViewDetachedFromWindow(v: View) {
                state.attached = false
                state.handler.removeCallbacksAndMessages(null)
            }
        })
        if (container.isAttachedToWindow) start(state)
        // Controls shown / hidden, fullscreen changes.
        container.viewTreeObserver.addOnGlobalLayoutListener { if (state.attached) update(state) }
        // Every frame the controls fade in or out, cross-fade with the real seek bar.
        container.viewTreeObserver.addOnPreDrawListener {
            if (state.shown) applyAlpha(state)
            true
        }
    }

    private fun start(state: State) {
        if (state.attached) return
        state.attached = true
        state.handler.removeCallbacks(state.tick)
        state.handler.post(state.tick)
    }

    private fun matchPosition(state: State) {
        copyPlace(state, state.realBar, state.bar, matchWidth = true)
        state.realPosition?.let { copyPlace(state, it, state.position, matchWidth = false) }
        // Xtra5: the end time is held by its right edge (like the real one), so a longer
        // countdown grows leftwards instead of running off the edge and wrapping.
        state.realDuration?.let { copyPlace(state, it, state.duration, matchWidth = false, alignEnd = true) }
    }

    /** Puts [copy] exactly where [real] is (relative to the video area). */
    private fun copyPlace(state: State, real: View, copy: View, matchWidth: Boolean, alignEnd: Boolean = false) {
        if (!real.isShown || real.width == 0) return
        val realLoc = IntArray(2).also { real.getLocationInWindow(it) }
        val boxLoc = IntArray(2).also { state.container.getLocationInWindow(it) }
        val params = copy.layoutParams as FrameLayout.LayoutParams
        val left = realLoc[0] - boxLoc[0]
        val top = realLoc[1] - boxLoc[1]
        val width = if (matchWidth) real.width else FrameLayout.LayoutParams.WRAP_CONTENT
        val height = if (matchWidth) real.height else FrameLayout.LayoutParams.WRAP_CONTENT
        if (alignEnd) {
            val right = state.container.width - (left + real.width)
            if (params.width != width || params.height != height || params.rightMargin != right || params.topMargin != top || params.leftMargin != 0) {
                params.width = width
                params.height = height
                params.leftMargin = 0
                params.rightMargin = right
                params.topMargin = top
                params.gravity = android.view.Gravity.TOP or android.view.Gravity.END
                copy.layoutParams = params
            }
            return
        }
        if (params.width != width || params.height != height || params.leftMargin != left || params.topMargin != top) {
            params.width = width
            params.height = height
            params.leftMargin = left
            params.topMargin = top
            params.gravity = android.view.Gravity.TOP or android.view.Gravity.START
            copy.layoutParams = params
        }
    }

    /** Hidden while the controls show (the real bar is there), the faded opacity once they're gone. */
    private fun applyAlpha(state: State) {
        val controls = state.controls
        val shown = if (controls.isVisible) controls.alpha.coerceIn(0f, 1f) else 0f
        val alpha = state.fadedAlpha() * (1f - shown)
        listOf(state.bar, state.position, state.duration).forEach { if (it.alpha != alpha) it.alpha = alpha }
    }

    private fun update(state: State) {
        val player = state.player
        if (!player.isAdded || player.view == null) return
        val ps = player.xtra5PlayerState()
        val bar = state.bar
        // Chapter markers on both bars (VODs with chapters).
        val chapters = if (ps.type == BasePlaybackService.VIDEO || ps.type == BasePlaybackService.OFFLINE_VIDEO) ps.chapters.filter { it > 0 }.sorted() else emptyList()
        if (chapters != state.chapters) {
            state.chapters = chapters
            val times = chapters.toLongArray()
            val played = BooleanArray(times.size)
            state.realBar.setAdGroupTimesMs(times, played, times.size)
            bar.setAdGroupTimesMs(times, played, times.size)
        }
        // End time on both timelines: "−time left in this chapter / total length".
        val isVod = ps.type == BasePlaybackService.VIDEO || ps.type == BasePlaybackService.OFFLINE_VIDEO || ps.type == BasePlaybackService.CLIP
        val total = player.getTotalDuration()?.takeIf { it > 0 }
        val endText = if (isVod && total != null) {
            val now = (player.getCurrentPosition() ?: 0L).coerceIn(0L, total)
            val chapterEnd = state.chapters.firstOrNull { it > now } ?: total
            "−" + android.text.format.DateUtils.formatElapsedTime((chapterEnd - now) / 1000) + " / " + android.text.format.DateUtils.formatElapsedTime(total / 1000)
        } else null
        if (endText != null) state.realDuration?.let { if (it.text.toString() != endText) it.text = endText }
        val context = bar.context
        val show = context.prefs().getBoolean(PREF_ENABLED, false) &&
            ps.isMaximized && player.isResumed &&
            (ps.type == BasePlaybackService.VIDEO || ps.type == BasePlaybackService.OFFLINE_VIDEO || ps.type == BasePlaybackService.CLIP) &&
            context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
            (state.bar.layoutParams.width > 0)
        val duration = player.getTotalDuration()?.takeIf { it > 0 }
        val views = listOf(bar, state.position, state.duration)
        if (!show || duration == null) {
            if (state.shown) {
                state.shown = false
                views.forEach { it.visibility = View.GONE }
            }
            return
        }
        if (state.controls.isVisible) matchPosition(state)
        val position = player.getCurrentPosition() ?: 0L
        bar.setDuration(duration)
        bar.setPosition(position)
        state.position.text = android.text.format.DateUtils.formatElapsedTime(position / 1000)
        state.duration.text = endText ?: android.text.format.DateUtils.formatElapsedTime(duration / 1000)
        if (!state.shown) {
            state.shown = true
            views.forEach { it.visibility = View.VISIBLE }
            applyAlpha(state)
        }
    }
}
