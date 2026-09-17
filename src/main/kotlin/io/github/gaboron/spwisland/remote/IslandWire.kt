// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.remote

import io.github.gaboron.spwisland.core.*
import java.io.Serializable

/** Private, anonymous parent/child pipes. No sockets, host objects, or arbitrary class loading. */
internal data class IslandState(
    val snapshot: PlaybackSnapshot,
    val settings: IslandSettings?,
    val metadata: TrackMetadata?,
    val lyrics: List<LyricLine>?,
    val about: Boolean = false
) : Serializable

internal data class IslandCommand(val action: String, val arguments: List<String> = emptyList()) : Serializable

internal class RemotePlayback : PlaybackSource {
    private var value = PlaybackSnapshot(null, null, 0, false, PlaybackStatus.IDLE)
    private var received = System.nanoTime()
    @Synchronized fun accept(state: IslandState) {
        value = state.snapshot.copy(metadata = state.metadata ?: value.metadata, lyrics = state.lyrics ?: value.lyrics)
        received = System.nanoTime()
    }
    @Synchronized override fun snapshot(): PlaybackSnapshot = value.copy(positionMs = value.positionMs +
        if (value.playing) ((System.nanoTime() - received) / 1_000_000).coerceIn(0, 2500) else 0)
}
