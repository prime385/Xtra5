package com.github.andreyasadchy.xtra.custom

import android.os.SystemClock
import android.view.KeyEvent
import androidx.media3.common.Player
import com.github.andreyasadchy.xtra.ui.player.PlayerFragment

/**
 * Xtra5 custom: live streams keep playing and stay live.
 *
 *  - On live streams, pauses that reach the player's media session from outside the app are
 *    ignored: earbuds / headphones (taps, taking them out, putting them in the case) and Android's
 *    system media controls. Media keys from the TV remote go through the app first, so they still
 *    pause. On-screen controls and the notification don't use the media session and work as
 *    normal. VODs and clips are unaffected.
 *  - Unpausing a paused live stream (any way) jumps to the live edge: ExoPlayer seeks to live,
 *    MediaPlayer (which can't seek live streams) restarts the stream.
 *  Android's "audio becoming noisy" pause is switched off in UpstreamUpdates.onMainActivityCreate.
 *
 * Kept in its own package so upstream Xtra updates can be merged without conflicts.
 * Upstream hooks: media session onPause() and player setup in ExoPlayerService and
 * MediaPlayerService (updatePlayingState); TvShortcuts.handle reports the app's own media keys.
 */
object AudioGuard {

    private const val APP_KEY_WINDOW_MS = 1000L

    /** Whether the current video is paused (reported by both player engines). */
    var isPaused = false
        private set
    private var lastAppMediaKey = 0L

    private val mediaKeys = setOf(
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY,
        KeyEvent.KEYCODE_MEDIA_STOP, KeyEvent.KEYCODE_HEADSETHOOK,
    )

    /** Called for every key the app's window receives (e.g. the TV remote's play/pause). */
    fun onAppKey(event: KeyEvent) {
        if (event.keyCode in mediaKeys) lastAppMediaKey = SystemClock.uptimeMillis()
    }

    /** Called by the media session's onPause(). Returns true when the pause should be ignored. */
    fun onSessionPause(isLive: Boolean): Boolean {
        if (!isLive) return false
        return SystemClock.uptimeMillis() - lastAppMediaKey > APP_KEY_WINDOW_MS
    }

    /** ExoPlayer: when a paused live stream is unpaused, jump to the live edge. */
    fun watchLiveResume(player: Player, isLive: () -> Boolean) {
        player.addListener(object : Player.Listener {
            private var paused = false

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                isPaused = !playWhenReady
                if (!playWhenReady) {
                    paused = true
                } else if (paused) {
                    paused = false
                    if (isLive() && player.isCurrentMediaItemLive) player.seekToDefaultPosition()
                }
            }
        })
    }

    /** Plays [player] if it is paused. Live streams then jump to live (see above). */
    fun resumeIfPaused(player: PlayerFragment) {
        if (isPaused && player.isAdded) player.playPause()
    }

    private var mediaPlayerPaused = false

    /** MediaPlayer: called on every play/pause change; restarts a live stream when unpaused. */
    fun onMediaPlayerState(isPlaying: Boolean, isLive: Boolean, restart: () -> Unit) {
        isPaused = !isPlaying
        if (!isPlaying) {
            mediaPlayerPaused = true
        } else if (mediaPlayerPaused) {
            mediaPlayerPaused = false
            if (isLive) restart()
        }
    }
}
