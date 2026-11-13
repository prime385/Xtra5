package com.github.andreyasadchy.xtra.custom

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.player.PlayerFragment
import java.lang.ref.WeakReference

/**
 * Xtra5 custom: automatic refreshes.
 *  - Minimizing the player while using the app refreshes every section of the screen behind it.
 *  - Coming back to the app after leaving it with no video on screen (not picture-in-picture)
 *    reloads an open live stream (minimized or full-size), like Restart player (VODs and clips
 *    are not reloaded), and refreshes the lists if a main screen is visible (no video, or the
 *    video minimized).
 *  - Coming back from another screen of the app (e.g. Settings) plays the video again if it was
 *    playing before; maximizing a paused minimized video plays it (live streams jump to live).
 *
 * Kept in its own package so upstream Xtra updates can be merged without conflicts.
 * Upstream hook: PlayerFragment.minimize(); the rest starts from UpstreamUpdates.onMainActivityCreate.
 */
object AutoRefresh {

    private const val MINIMIZE_ANIMATION_MS = 400L
    private const val RESUME_DELAY_MS = 500L

    private val handler = Handler(Looper.getMainLooper())
    private var registered = false
    private var startedActivities = 0
    private var main: WeakReference<MainActivity>? = null

    /** Called from PlayerFragment.maximize(): a paused video starts playing (live jumps to live). */
    fun onMaximized(player: PlayerFragment) {
        handler.postDelayed({ AudioGuard.resumeIfPaused(player) }, 300L)
    }

    /** Called from PlayerFragment.minimize(). */
    fun onMinimized(player: PlayerFragment) {
        val activity = player.activity as? MainActivity ?: return
        // Only when minimized by the user inside the app, not while the app is going away.
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        handler.postDelayed({
            if (!activity.isFinishing && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                RefreshButton.refreshCurrentScreen(activity, showMessage = false)
            }
        }, MINIMIZE_ANIMATION_MS)
    }

    /** Watches the app going to the background and coming back. */
    fun register(activity: MainActivity) {
        main = WeakReference(activity)
        if (registered) return
        registered = true
        activity.application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(a: Activity) {
                if (a is MainActivity) main = WeakReference(a)
                startedActivities++
                // Back from another screen of the app (e.g. Settings): show changed highlights.
                if (a is MainActivity && startedActivities > 1) handler.post { ChatLive.refreshChat() }
                // ...and play again if it was playing.
                if (a is MainActivity && startedActivities > 1 && playingWhenLeft) {
                    playingWhenLeft = false
                    handler.postDelayed({
                        (main?.get()?.playerFragment as? PlayerFragment)?.let { AudioGuard.resumeIfPaused(it) }
                    }, RESUME_DELAY_MS)
                }
                // In picture-in-picture the app is never stopped, so this only runs after the app
                // was fully in the background (no video on screen).
                if (startedActivities == 1 && wasInBackground) {
                    wasInBackground = false
                    handler.postDelayed({ onReturnFromBackground() }, RESUME_DELAY_MS)
                }
            }

            override fun onActivityStopped(a: Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
                if (a is MainActivity && !a.isChangingConfigurations) {
                    // Another screen of the app is on top (e.g. Settings) if anything is still started.
                    playingWhenLeft = startedActivities > 0 && !AudioGuard.isPaused &&
                        (a.playerFragment as? PlayerFragment)?.isAdded == true
                }
                if (startedActivities == 0 && !a.isChangingConfigurations) wasInBackground = true
            }

            override fun onActivityCreated(a: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
        startedActivities = if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) 1 else 0
    }

    private var wasInBackground = false
    private var playingWhenLeft = false

    /** After the app was in the background (or asleep). */
    private fun onReturnFromBackground() {
        val activity = main?.get() ?: return
        val player = (activity.playerFragment as? PlayerFragment)?.takeIf { it.isAdded && it.view != null }
        val state = player?.xtra5PlayerState()
        // Refresh the lists when a main screen is visible: no video, or the video minimized.
        if (state == null || !state.isMaximized) {
            RefreshButton.refreshCurrentScreen(activity, showMessage = false)
        }
        // Reload an open live stream, minimized or full-size.
        if (player != null && state?.isLiveStream == true) player.restartPlayer()
    }
}
