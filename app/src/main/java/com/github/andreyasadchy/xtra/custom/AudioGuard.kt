package com.github.andreyasadchy.xtra.custom

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Xtra5 custom: keep playing when headphones / Bluetooth audio disconnect.
 *
 * Many Bluetooth earbuds send a "pause" media button as they disconnect. Pauses arriving through
 * the media session within WINDOW_MS of a Bluetooth / wired / USB audio output disconnecting are
 * treated as part of the disconnect: ignored if they come just after it, undone (playback
 * resumed) if they come just before it. Other pauses are unaffected. Android's "audio becoming
 * noisy" pause is switched off separately (see UpstreamUpdates.onMainActivityCreate).
 *
 * Kept in its own package so upstream Xtra updates can be merged without conflicts.
 * Upstream hooks: media session onPause() in ExoPlayerService and MediaPlayerService.
 */
object AudioGuard {

    private const val WINDOW_MS = 2000L

    private val handler = Handler(Looper.getMainLooper())
    private var registered = false
    private var lastDisconnect = 0L
    private var pendingResume: (() -> Unit)? = null
    private val clearPending = Runnable { pendingResume = null }

    private val headphoneTypes = buildSet {
        add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        add(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
        add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
        add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
        add(AudioDeviceInfo.TYPE_USB_HEADSET)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(AudioDeviceInfo.TYPE_BLE_HEADSET)
            add(AudioDeviceInfo.TYPE_BLE_SPEAKER)
        }
    }

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            if (removedDevices.none { it.isSink && it.type in headphoneTypes }) return
            lastDisconnect = SystemClock.uptimeMillis()
            // A pause that arrived just before the disconnect: undo it.
            pendingResume?.let {
                pendingResume = null
                handler.removeCallbacks(clearPending)
                it()
            }
        }
    }

    /** Starts watching audio outputs (once). */
    fun ensureRegistered(context: Context) {
        if (registered) return
        val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        audioManager.registerAudioDeviceCallback(callback, handler)
        registered = true
    }

    /**
     * Called by the media session's onPause(). Returns true when the pause should be ignored.
     * [resume] restarts playback if a disconnect follows shortly after this pause.
     */
    fun onSessionPause(context: Context, resume: () -> Unit): Boolean {
        ensureRegistered(context)
        val now = SystemClock.uptimeMillis()
        if (now - lastDisconnect < WINDOW_MS) return true
        pendingResume = resume
        handler.removeCallbacks(clearPending)
        handler.postDelayed(clearPending, WINDOW_MS)
        return false
    }
}
