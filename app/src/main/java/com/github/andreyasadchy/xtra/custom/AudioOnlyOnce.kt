package com.github.andreyasadchy.xtra.custom

import android.os.SystemClock
import com.github.andreyasadchy.xtra.model.VideoQuality

/**
 * Xtra5 custom: open one stream in audio-only quality (long-press on a stream card) without
 * changing the default or saved quality. The request is used by the next stream's default quality
 * choice (BasePlaybackService.setDefaultQuality), and expires if that doesn't happen soon.
 *
 * Kept in its own package so upstream Xtra updates can be merged without conflicts.
 * Upstream hooks: end of BasePlaybackService.setDefaultQuality(); the "save quality" line in
 * ExoPlayerService and MediaPlayerService; requested from StreamCards.
 */
object AudioOnlyOnce {

    private const val EXPIRES_MS = 30_000L
    private var requestedAt = 0L

    fun request() {
        requestedAt = SystemClock.uptimeMillis()
    }

    /** Whether the current stream's audio-only quality came from a long-press (not the menu). */
    private var active = false

    /** True once, for the stream opened right after request(). */
    fun consume(): Boolean {
        val pending = requestedAt != 0L && SystemClock.uptimeMillis() - requestedAt < EXPIRES_MS
        requestedAt = 0L
        active = pending
        return pending
    }

    /**
     * Called before official Xtra saves the current quality as the "saved" quality: true (don't
     * save) for the one-off audio-only quality, so it never becomes the default for later streams.
     */
    fun skipSave(qualityName: String?): Boolean {
        if (!active) return false
        if (qualityName == VideoQuality.AUDIO_ONLY_QUALITY) return true
        active = false // the user picked another quality: save it as normal
        return false
    }
}
