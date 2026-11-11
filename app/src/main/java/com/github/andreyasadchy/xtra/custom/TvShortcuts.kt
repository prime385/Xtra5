package com.github.andreyasadchy.xtra.custom

import android.app.Activity
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.player.PlayerFragment
import com.github.andreyasadchy.xtra.util.prefs

/**
 * Xtra5 custom: remote / D-pad shortcuts in the full-screen player.
 *
 * Only active when enabled in Settings, the player is maximized and its on-screen controls are hidden:
 *   Left / Right  rewind / fast-forward by a fixed step (VODs, clips and downloads; not live streams)
 *   Up            show the on-screen controls
 *   Down          open the Chapters menu, if the VOD has chapters
 * Holding a button counts as one press.
 *
 * Kept in its own package so upstream Xtra updates can be merged without conflicts.
 * Upstream hooks: MainActivity.dispatchKeyEvent and PlayerFragment.xtra5PlayerState.
 */
object TvShortcuts {

    const val PREF_ENABLED = "xtra5_tv_shortcuts"
    const val PREF_SEEK_STEP = "xtra5_tv_seek_step"
    private const val DEFAULT_SEEK_STEP = 10

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

    /** Returns true if the key event was handled and should not reach the rest of the app. */
    fun handle(activity: Activity, playerFragment: Fragment?, event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT &&
            keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN) return false
        if (event.action == KeyEvent.ACTION_UP) return consumed.remove(keyCode)
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (event.repeatCount > 0) return consumed.contains(keyCode) // holding = a single press

        if (!activity.prefs().getBoolean(PREF_ENABLED, false)) return false
        val player = playerFragment as? PlayerFragment ?: return false
        if (!player.isAdded || !player.isResumed) return false
        val state = player.xtra5PlayerState()
        if (!state.isMaximized) return false
        if (activity.currentFocus is EditText) return false // typing in chat
        val controls = player.view?.findViewById<View>(R.id.playerControls)
        if (controls?.isVisible == true) return false // normal D-pad navigation of the controls

        val handled = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (state.isLiveStream) false else {
                    seek(activity, player, if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1)
                    true
                }
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                state.showController()
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

    private fun seek(activity: Activity, player: PlayerFragment, direction: Int) {
        val position = player.getCurrentPosition() ?: return
        val stepMs = getSeekStep(activity) * 1000L
        var target = (position + direction * stepMs).coerceAtLeast(0L)
        player.getTotalDuration()?.takeIf { it > 0 }?.let { target = target.coerceAtMost(it) }
        player.seek(target)
    }

    fun getSeekStep(activity: Activity): Int =
        activity.prefs().getInt(PREF_SEEK_STEP, DEFAULT_SEEK_STEP).coerceIn(5, 60)
}
