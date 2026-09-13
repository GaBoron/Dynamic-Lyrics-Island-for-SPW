// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.core

/** Selects every line that is genuinely active, while retaining the last line through instrumental gaps. */
object ActiveLyrics {
    fun select(snapshot: PlaybackSnapshot, experimental: Boolean): List<LyricLine> {
        if (!experimental || snapshot.lyrics.isEmpty()) return listOfNotNull(snapshot.line)
        // A line callback can arrive before the coarse host position heartbeat catches up. Treat the
        // callback's start as reached so simultaneous lines are selected together instead of one-by-one.
        val reachedPosition = maxOf(snapshot.positionMs, snapshot.line?.startMs ?: 0L)
        val started = snapshot.lyrics.filter { it.text.isNotBlank() && it.startMs <= reachedPosition }
        if (started.isEmpty()) return listOfNotNull(snapshot.line)
        val active = started.filter { line -> line.endMs > reachedPosition && line.endMs > line.startMs }
        // During a gap, retain the line that ended last. The latest-started line may have ended earlier.
        return active.ifEmpty { listOf(started.maxBy { it.endMs }) }
            .sortedWith(compareBy<LyricLine> { it.startMs }.thenBy { it.endMs })
    }
}
