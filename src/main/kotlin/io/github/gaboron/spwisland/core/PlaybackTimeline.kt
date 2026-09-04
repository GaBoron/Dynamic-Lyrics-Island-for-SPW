// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.core

/** Serializes host callbacks and interpolates the host's one-second clock on a monotonic clock. */
class PlaybackTimeline(private val nanoTime: () -> Long = System::nanoTime) {
    private var track: Track? = null
    private var line: LyricLine? = null
    private var position = 0L
    private var anchor = nanoTime()
    private var playing = false
    private var status = PlaybackStatus.IDLE

    @Synchronized fun trackChanged(value: Track) {
        if (track != value) {
            track = value
            line = null
            position = 0
            anchor = nanoTime()
        }
    }
    @Synchronized fun lineChanged(value: LyricLine?) {
        // Null/blank callbacks mark instrumental gaps, not a request to erase the last lyric.
        if (value != null && value.text.isNotBlank()) line = value
    }
    @Synchronized fun positionChanged(value: Long) {
        position = value.coerceAtLeast(0)
        anchor = nanoTime()
    }
    @Synchronized fun seek(value: Long) {
        positionChanged(value)
        // Await the host's replacement line; do not retain text from before a seek.
        line = null
    }
    @Synchronized fun playingChanged(value: Boolean) {
        position = currentPosition()
        anchor = nanoTime()
        playing = value
    }
    @Synchronized fun stateChanged(value: PlaybackStatus) {
        position = currentPosition()
        anchor = nanoTime()
        status = value
        if (value == PlaybackStatus.IDLE || value == PlaybackStatus.ENDED) {
            playing = false
            line = null
            if (value == PlaybackStatus.IDLE) { track = null; position = 0 }
        }
    }
    @Synchronized fun snapshot(): PlaybackSnapshot {
        val now = currentPosition()
        return PlaybackSnapshot(track, line, now, playing && status == PlaybackStatus.READY, status)
    }
    private fun currentPosition(): Long = position + if (playing && status == PlaybackStatus.READY) {
        // Freeze on a missing host heartbeat rather than letting stale lyrics run indefinitely.
        ((nanoTime() - anchor) / 1_000_000).coerceIn(0, 2500)
    } else 0
}
