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
    private var lyrics: List<LyricLine> = emptyList()
    private var position = 0L
    private var anchor = nanoTime()
    private var playing = false
    private var status = PlaybackStatus.IDLE
    private var metadata = TrackMetadata()
    private var generation = 0L
    private var pendingSeek: Long? = null
    private var pendingSeekDeadline = 0L
    private val heartbeatRecovery = PlaybackHeartbeatRecovery()

    @Synchronized fun trackChanged(value: Track): Long {
        if (track != value) {
            track = value
            generation++
            metadata = TrackMetadata()
            line = null
            lyrics = emptyList()
            position = 0
            anchor = nanoTime()
            pendingSeek = null
            heartbeatRecovery.trackChanged()
        }
        return generation
    }
    @Synchronized fun metadataLoaded(token: Long, value: TrackMetadata) {
        if (generation == token && track != null) metadata = value
    }
    @Synchronized fun lineChanged(value: LyricLine?) {
        // Null/blank callbacks mark instrumental gaps, not a request to erase the last lyric.
        if (value != null && value.text.isNotBlank()) {
            line = value
            // Public callbacks already carry start/end time. Retaining emitted lines is enough to
            // reconstruct every overlap even when the experimental host-document probe is unavailable.
            lyrics = (lyrics.filterNot { it.startMs == value.startMs && it.text == value.text } + value)
                .sortedWith(compareBy<LyricLine> { it.startMs }.thenBy { it.endMs })
        }
    }
    @Synchronized fun lyricsChanged(value: List<LyricLine>) {
        lyrics = value.filter { it.text.isNotBlank() && it.startMs >= 0 && it.endMs >= it.startMs }
    }
    @Synchronized fun positionChanged(value: Long) {
        val now = nanoTime()
        val next = value.coerceAtLeast(0)
        pendingSeek?.let { target ->
            // SPW can deliver one last pre-seek clock update before acknowledging the new position.
            if (now < pendingSeekDeadline && kotlin.math.abs(next - target) > SEEK_ACK_TOLERANCE_MS) return
            pendingSeek = null
        }
        if (heartbeatRecovery.positionChanged(next)) {
            // A plugin installed while SPW is already playing misses the earlier state callbacks.
            // Consecutive forward heartbeats prove the clock is running without guessing from one seek.
            playing = true
            if (status == PlaybackStatus.IDLE) status = PlaybackStatus.READY
        }
        position = next
        anchor = now
    }
    @Synchronized fun seek(value: Long) {
        position = value.coerceAtLeast(0)
        anchor = nanoTime()
        pendingSeek = position
        pendingSeekDeadline = anchor + SEEK_ACK_WINDOW_NS
        heartbeatRecovery.seeked()
        // Await the host's replacement line; do not retain text from before a seek.
        line = null
    }
    @Synchronized fun playingChanged(value: Boolean) {
        position = currentPosition()
        anchor = nanoTime()
        heartbeatRecovery.playingChanged()
        playing = value
        if (value && status == PlaybackStatus.IDLE) status = PlaybackStatus.READY
    }
    @Synchronized fun stateChanged(value: PlaybackStatus) {
        position = currentPosition()
        anchor = nanoTime()
        status = value
        heartbeatRecovery.stateChanged(value)
        if (value == PlaybackStatus.IDLE || value == PlaybackStatus.ENDED) {
            playing = false
            line = null
            lyrics = emptyList()
            pendingSeek = null
            if (value == PlaybackStatus.IDLE) { track = null; position = 0; metadata = TrackMetadata(); generation++ }
        }
    }
    @Synchronized fun snapshot(): PlaybackSnapshot {
        val now = currentPosition()
        return PlaybackSnapshot(track, line, now, playing && status == PlaybackStatus.READY, status, metadata, lyrics)
    }
    private fun currentPosition(): Long = position + if (playing && status == PlaybackStatus.READY) {
        // Freeze on a missing host heartbeat rather than letting stale lyrics run indefinitely.
        ((nanoTime() - anchor) / 1_000_000).coerceIn(0, 2500)
    } else 0
}
