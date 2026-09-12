// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.core

/** Recovers a missing startup playing signal from consecutive host position heartbeats. */
internal class PlaybackHeartbeatRecovery {
    companion object {
        private const val MAX_HEARTBEAT_STEP_MS = 2_500L
    }

    private var previousPosition: Long? = null
    private var playingSignalObserved = false
    private var inferenceAllowed = true

    fun trackChanged() {
        previousPosition = null
        playingSignalObserved = false
        inferenceAllowed = true
    }

    fun stateChanged(value: PlaybackStatus) {
        inferenceAllowed = value != PlaybackStatus.IDLE && value != PlaybackStatus.ENDED
        if (!inferenceAllowed) previousPosition = null
    }

    fun playingChanged() {
        playingSignalObserved = true
    }

    fun seeked() {
        previousPosition = null
    }

    fun positionChanged(value: Long): Boolean {
        val previous = previousPosition
        previousPosition = value
        if (!inferenceAllowed || playingSignalObserved || previous == null) return false
        val step = value - previous
        return step in 1..MAX_HEARTBEAT_STEP_MS
    }
}
