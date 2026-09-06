// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.core

data class Track(val title: String, val artist: String, val path: String)
data class Word(val startMs: Long, val endMs: Long, val text: String) {
    fun progress(position: Long): Double = when {
        position < startMs -> 0.0
        endMs <= startMs -> 1.0
        else -> ((position - startMs).toDouble() / (endMs - startMs)).coerceIn(0.0, 1.0)
    }
}
data class LyricLine(val startMs: Long, val endMs: Long, val text: String,
                     val translation: String?, val words: List<Word>) {
    // Only render cell-level timing when cells describe the displayed string exactly.
    // Ordinary LRC or malformed timing stays readable without invented word timestamps.
    val timedWords: List<Word> = words.takeIf { cells ->
        cells.isNotEmpty() && !cells.all { it.startMs == startMs && it.endMs == endMs } &&
            cells.joinToString("") { it.text } == text &&
            cells.all { it.endMs >= it.startMs } &&
            cells.zipWithNext().all { (a, b) -> a.startMs <= b.startMs }
    }.orEmpty()
}

enum class PlaybackStatus { IDLE, BUFFERING, READY, ENDED }
data class PlaybackSnapshot(val track: Track?, val line: LyricLine?, val positionMs: Long,
                            val playing: Boolean, val status: PlaybackStatus,
                            val metadata: TrackMetadata = TrackMetadata())

class CoverArtwork(val width: Int, val height: Int, internal val argb: IntArray)
data class TrackMetadata(val durationMs: Long = 0, val coverRgb: Int? = null,
                         val cover: CoverArtwork? = null)
