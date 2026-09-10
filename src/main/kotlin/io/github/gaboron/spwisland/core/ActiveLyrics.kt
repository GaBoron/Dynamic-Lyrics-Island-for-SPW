// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.core

/** Selects every line that is genuinely active, while retaining the last line through instrumental gaps. */
object ActiveLyrics {
    fun select(snapshot: PlaybackSnapshot, experimental: Boolean): List<LyricLine> {
        if (!experimental || snapshot.lyrics.isEmpty()) return listOfNotNull(snapshot.line)
        val started = snapshot.lyrics.filter { it.text.isNotBlank() && it.startMs <= snapshot.positionMs }
        if (started.isEmpty()) return listOfNotNull(snapshot.line)
        val active = started.filter { line -> line.endMs > snapshot.positionMs && line.endMs > line.startMs }
        return active.ifEmpty { listOf(started.maxBy { it.startMs }) }
            .sortedWith(compareBy<LyricLine> { it.startMs }.thenBy { it.endMs })
    }
}
