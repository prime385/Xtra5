package com.github.andreyasadchy.xtra.custom

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import android.view.KeyEvent
import android.view.View
import android.view.ViewTreeObserver
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.media3.ui.DefaultTimeBar
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.player.PlayerFragment
import com.github.andreyasadchy.xtra.util.prefs
import java.lang.ref.WeakReference

/**
 * Xtra5 custom: remote / D-pad shortcuts in the full-screen player.
 *
 * Only active when enabled in Settings, the player is maximized and its on-screen controls are hidden:
 *   Left / Right  rewind / fast-forward by a fixed step (VODs, clips and downloads; not live streams)
 *   Up            show the controls and "hold" the seek bar (live streams: just show the controls)
 *   Down          open the Chapters menu, if the VOD has chapters
 * While the seek bar is held (like a finger on the marker):
 *   Left / Right  move the marker by a percentage of the video length; hold to keep moving
 *   OK            let go: seek to the marker
 *   Back          cancel; also cancels by itself after 6 seconds without input
 * After letting go, while the controls are still showing, Left / Right hold the seek bar again.
 * Holding a button counts as one press.
 * Long-press Back (player full-size or minimized): close the player, like swiping it away.
 * Long-press OK (live stream, controls hidden): reload the stream, like Restart player.
 *
 * Kept in its own package so upstream Xtra updates can be merged without conflicts.
 * Upstream hooks: MainActivity.dispatchKeyEvent and PlayerFragment.xtra5PlayerState.
 */
object TvShortcuts {

    const val PREF_ENABLED = "xtra5_tv_shortcuts"
    const val PREF_SEEK_STEP = "xtra5_tv_seek_step"
    const val PREF_SCRUB_PERCENT = "xtra5_tv_scrub_percent"
    private const val DEFAULT_SEEK_STEP = 10
    private const val DEFAULT_SCRUB_PERCENT = 5
    private const val SCRUB_TIMEOUT_MS = 6000L
    private const val HOLD_DELAY_MS = 400L // holding Left / Right in held mode: start repeating after
    private const val HOLD_REPEAT_MS = 150L // then one step every
    private const val LONG_PRESS_REPEATS = 8 // fallback if a remote never flags the long press

    /** Player state the shortcuts need; built by PlayerFragment.xtra5PlayerState(). */
    class PlayerState(
        val isMaximized: Boolean,
        val isLiveStream: Boolean,
        val hasChapters: Boolean,
        val showController: () -> Unit,
        val showChapters: () -> Unit,
    )

    /** Keys whose ACTION_DOWN we consumed, so their ACTION_UP is consumed too. */
    private val consumed = HashSet<Int>()

    // Seek bar "held" state (only while scrubbing).
    private class Scrub(val player: WeakReference<PlayerFragment>, val showController: () -> Unit, val duration: Long, var position: Long)
    private var scrub: Scrub? = null
    /** Player whose seek bar was just let go of, while its controls are still showing. */
    private var afterScrub: WeakReference<PlayerFragment>? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { endScrub(commit = false) }

    private val dpadKeys = setOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN)
    private val okKeys = setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)

    /** Returns true if the key event was handled and should not reach the rest of the app. */
    fun handle(activity: Activity, playerFragment: Fragment?, event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val scrubbing = scrub != null
        val isBack = keyCode == KeyEvent.KEYCODE_BACK
        if (keyCode !in dpadKeys && !isBack && keyCode !in okKeys) {
            return if (event.action == KeyEvent.ACTION_UP) consumed.remove(keyCode) else false
        }
        if (event.action == KeyEvent.ACTION_UP) {
            if (keyCode == holdKey) stopHold()
            return consumed.remove(keyCode)
        }
        if (event.action != KeyEvent.ACTION_DOWN) return false

        // Long-press Back: close the player completely, like swiping it away.
        if (isBack && !scrubbing && event.repeatCount > 0 && !consumed.contains(keyCode) &&
            (event.isLongPress || event.repeatCount >= LONG_PRESS_REPEATS)) {
            if (closePlayer(activity, playerFragment)) {
                consumed.add(keyCode)
                return true
            }
        }
        if (isBack && !scrubbing) return consumed.contains(keyCode) // short press: normal Back

        // Long-press OK on a live stream (controls hidden): reload it, like Restart player.
        if (keyCode in okKeys && !scrubbing) {
            if (event.repeatCount == 0) return false // short press: normal OK
            if (consumed.contains(keyCode)) return true
            if (!(event.isLongPress || event.repeatCount >= LONG_PRESS_REPEATS)) return false
            if (reloadStream(activity, playerFragment)) {
                consumed.add(keyCode)
                activity.currentFocus?.isPressed = false // its key up is swallowed
                return true
            }
            return false
        }

        if (event.repeatCount > 0) return consumed.contains(keyCode) // holding = a single press

        if (scrubbing) {
            val current = scrub!!
            if (current.player.get() !== playerFragment || playerFragment?.isResumed != true) {
                endScrub(commit = false)
            } else {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        val direction = if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1
                        moveScrub(activity, current, direction)
                        startHold(activity, keyCode, direction)
                    }
                    in okKeys -> endScrub(commit = true)
                    KeyEvent.KEYCODE_BACK -> endScrub(commit = false)
                    // Up / Down: ignored while the seek bar is held
                }
                consumed.add(keyCode)
                return true
            }
        }

        if (!activity.prefs().getBoolean(PREF_ENABLED, false)) return false
        val player = playerFragment as? PlayerFragment ?: return false
        if (!player.isAdded || !player.isResumed) return false
        val state = player.xtra5PlayerState()
        if (!state.isMaximized) return false
        if (activity.currentFocus is EditText) return false // typing in chat
        val controls = player.view?.findViewById<View>(R.id.playerControls)
        if (controls?.isVisible == true) {
            // Right after letting go of the seek bar (controls still up): Left / Right hold it again.
            if (afterScrub?.get() === player && !state.isLiveStream &&
                (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) &&
                startScrub(player, state)) {
                val direction = if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1
                scrub?.let { moveScrub(activity, it, direction) }
                startHold(activity, keyCode, direction)
                consumed.add(keyCode)
                return true
            }
            return false // normal D-pad navigation of the controls
        }
        afterScrub = null

        val handled = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (state.isLiveStream) false else {
                    seek(activity, player, if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1)
                    true
                }
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (state.isLiveStream || !startScrub(player, state)) state.showController()
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (state.hasChapters) {
                    state.showChapters()
                    true
                } else false
            }
            else -> false
        }
        if (handled) consumed.add(keyCode)
        return handled
    }

    /** Restarts the player of a live stream (same as Restart player in the player menu). */
    private fun reloadStream(activity: Activity, playerFragment: Fragment?): Boolean {
        if (!activity.prefs().getBoolean(PREF_ENABLED, false)) return false
        val player = playerFragment as? PlayerFragment ?: return false
        if (!player.isAdded || !player.isResumed) return false
        val state = player.xtra5PlayerState()
        if (!state.isMaximized || !state.isLiveStream) return false
        if (activity.currentFocus is EditText) return false
        if (player.view?.findViewById<View>(R.id.playerControls)?.isVisible == true) return false
        player.restartPlayer()
        Toast.makeText(activity, "Reloading stream", Toast.LENGTH_SHORT).show()
        return true
    }

    /** Closes the player the same way swiping it away does. */
    private fun closePlayer(activity: Activity, playerFragment: Fragment?): Boolean {
        if (!activity.prefs().getBoolean(PREF_ENABLED, false)) return false
        val player = playerFragment as? PlayerFragment ?: return false
        val main = activity as? MainActivity ?: return false
        if (!player.isAdded) return false
        player.close()
        main.closePlayer()
        return true
    }

    private fun seek(activity: Activity, player: PlayerFragment, direction: Int) {
        val position = player.getCurrentPosition() ?: return
        val stepMs = getSeekStep(activity) * 1000L
        var target = (position + direction * stepMs).coerceAtLeast(0L)
        player.getTotalDuration()?.takeIf { it > 0 }?.let { target = target.coerceAtMost(it) }
        player.seek(target)
        SeekIndicator.show(player, forward = direction > 0, seconds = getSeekStep(activity))
    }

    /** Shows the controls with the seek bar held at the current position. False if not possible. */
    private fun startScrub(player: PlayerFragment, state: PlayerState): Boolean {
        val view = player.view ?: return false
        val bar = view.findViewById<DefaultTimeBar>(R.id.progressBar) ?: return false
        val duration = player.getTotalDuration()?.takeIf { it > 0 } ?: return false
        val position = (player.getCurrentPosition() ?: return false).coerceIn(0L, duration)
        // A pressed seek bar stops the player from moving the marker and from auto-hiding the controls.
        bar.isPressed = true
        state.showController()
        val current = Scrub(WeakReference(player), state.showController, duration, position)
        scrub = current
        showScrubPosition(current)
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, SCRUB_TIMEOUT_MS)
        return true
    }

    private fun showScrubPosition(current: Scrub) {
        val view = current.player.get()?.view ?: return
        view.findViewById<DefaultTimeBar>(R.id.progressBar)?.setPosition(current.position)
        view.findViewById<TextView>(R.id.position)?.text = DateUtils.formatElapsedTime(current.position / 1000)
    }

    /** Lets go of the seek bar: seeks to the marker when [commit], otherwise puts it back. */
    private fun endScrub(commit: Boolean) {
        val current = scrub ?: return
        scrub = null
        stopHold()
        handler.removeCallbacks(timeout)
        val player = current.player.get() ?: return
        val view = player.view ?: return
        val bar = view.findViewById<DefaultTimeBar>(R.id.progressBar)
        bar?.isPressed = false
        if (commit) {
            player.seek(current.position)
        } else {
            val position = player.getCurrentPosition() ?: 0L
            bar?.setPosition(position)
            view.findViewById<TextView>(R.id.position)?.text = DateUtils.formatElapsedTime(position / 1000)
        }
        current.showController() // restarts the normal auto-hide timer
        // Until the controls hide, Left / Right hold the seek bar again (see handle()).
        afterScrub = WeakReference(player)
        view.findViewById<View>(R.id.playerControls)?.let { controls ->
            controls.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (!controls.isVisible || scrub != null) {
                        controls.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        if (!controls.isVisible && afterScrub?.get() === player) afterScrub = null
                    }
                }
            })
        }
    }

    // Holding Left / Right while the seek bar is held keeps moving it, one step at a time.
    private var holdKey = 0
    private var holdDirection = 0
    private var holdActivity: WeakReference<Activity>? = null
    private val holdStep = object : Runnable {
        override fun run() {
            val current = scrub
            val activity = holdActivity?.get()
            if (current == null || activity == null) {
                stopHold()
                return
            }
            moveScrub(activity, current, holdDirection) // also keeps the idle timeout from firing
            handler.postDelayed(this, HOLD_REPEAT_MS)
        }
    }

    private fun startHold(activity: Activity, keyCode: Int, direction: Int) {
        stopHold()
        holdKey = keyCode
        holdDirection = direction
        holdActivity = WeakReference(activity)
        handler.postDelayed(holdStep, HOLD_DELAY_MS)
    }

    private fun stopHold() {
        handler.removeCallbacks(holdStep)
        holdKey = 0
    }

    private fun moveScrub(activity: Activity, current: Scrub, direction: Int) {
        val step = current.duration * getScrubPercent(activity) / 100
        current.position = (current.position + direction * step).coerceIn(0L, current.duration)
        showScrubPosition(current)
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, SCRUB_TIMEOUT_MS)
    }

    fun getScrubPercent(activity: Activity): Int =
        activity.prefs().getInt(PREF_SCRUB_PERCENT, DEFAULT_SCRUB_PERCENT).coerceIn(1, 10)

    fun getSeekStep(activity: Activity): Int =
        activity.prefs().getInt(PREF_SEEK_STEP, DEFAULT_SEEK_STEP).coerceIn(5, 60)
}
