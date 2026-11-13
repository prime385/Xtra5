package com.github.andreyasadchy.xtra.custom

import android.os.SystemClock

/**
 * Xtra5 custom: open one stream in audio-only quality (long-press on a stream card) without
 * changing the default or saved quality. The request is used by the next stream's default quality
 * choice (BasePlaybackService.setDefaultQuality), and expires if that doesn't happen soon.
 *
 * Kept in its own package so upstream Xtra updates can be merged without conflicts.
 * Upstream hook: end of BasePlaybackService.setDefaultQuality(); requested from StreamCards.
 */
object AudioOnlyOnce {

    private const val EXPIRES_MS = 30_000L
    private var requestedAt = 0L

    fun request() {
        requestedAt = SystemClock.uptimeMillis()
    }

    /** True once, for the stream opened right after request(). */
    fun consume(): Boolean {
        val pending = requestedAt != 0L && SystemClock.uptimeMillis() - requestedAt < EXPIRES_MS
        requestedAt = 0L
        return pending
    }
}
