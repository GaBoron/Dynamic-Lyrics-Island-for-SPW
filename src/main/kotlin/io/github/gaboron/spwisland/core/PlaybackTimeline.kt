// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.core

/** Serializes host callbacks and interpolates the host's one-second clock on a monotonic clock. */
class PlaybackTimeline(private val nanoTime: () -> Long = System::nanoTime) {
    companion object {
        private const val SEEK_ACK_WINDOW_NS = 2_500_000_000L
        private const val SEEK_ACK_TOLERANCE_MS = 2_000L
    }
    private var track: Track? = null
    private var line: LyricLine? = null
    private var position = 0L
    private var anchor = nanoTime()
    private var playing = false
    private var status = PlaybackStatus.IDLE
    private var metadata = TrackMetadata()
    private var generation = 0L
    private var pendingSeek: Long? = null
    private var pendingSeekDeadline = 0L

    @Synchronized fun trackChanged(value: Track): Long {
        if (track != value) {
            track = value
            generation++
            metadata = TrackMetadata()
            line = null
            position = 0
            anchor = nanoTime()
            pendingSeek = null
        }
        return generation
    }
    @Synchronized fun metadataLoaded(token: Long, value: TrackMetadata) {
        if (generation == token && track != null) metadata = value
    }
    @Synchronized fun lineChanged(value: LyricLine?) {
        // Null/blank callbacks mark instrumental gaps, not a request to erase the last lyric.
        if (value != null && value.text.isNotBlank()) line = value
    }
    @Synchronized fun positionChanged(value: Long) {
        val now = nanoTime()
        val next = value.coerceAtLeast(0)
        pendingSeek?.let { target ->
            // SPW can deliver one last pre-seek clock update before acknowledging the new position.
            if (now < pendingSeekDeadline && kotlin.math.abs(next - target) > SEEK_ACK_TOLERANCE_MS) return
            pendingSeek = null
        }
        position = next
        anchor = now
    }
    @Synchronized fun seek(value: Long) {
        position = value.coerceAtLeast(0)
        anchor = nanoTime()
        pendingSeek = position
        pendingSeekDeadline = anchor + SEEK_ACK_WINDOW_NS
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
            pendingSeek = null
            if (value == PlaybackStatus.IDLE) { track = null; position = 0; metadata = TrackMetadata(); generation++ }
        }
    }
    @Synchronized fun snapshot(): PlaybackSnapshot {
        val now = currentPosition()
        return PlaybackSnapshot(track, line, now, playing && status == PlaybackStatus.READY, status, metadata)
    }
    private fun currentPosition(): Long = position + if (playing && status == PlaybackStatus.READY) {
        // Freeze on a missing host heartbeat rather than letting stale lyrics run indefinitely.
        ((nanoTime() - anchor) / 1_000_000).coerceIn(0, 2500)
    } else 0
}
