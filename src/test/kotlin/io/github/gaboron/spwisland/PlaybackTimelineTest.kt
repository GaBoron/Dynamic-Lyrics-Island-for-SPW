// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland

import io.github.gaboron.spwisland.core.*
import org.junit.Assert.*
import org.junit.Test

class PlaybackTimelineTest {
    private var nanos = 0L
    private val timeline = PlaybackTimeline { nanos }
    private fun advance(ms: Long) { nanos += ms * 1_000_000 }
    private fun start() {
        timeline.trackChanged(Track("Song", "Artist", "song.flac"))
        timeline.stateChanged(PlaybackStatus.READY)
        timeline.playingChanged(true)
    }
    @Test fun interpolatesHostSecondsAndReanchorsOnCorrection() {
        start(); timeline.positionChanged(1000); advance(400)
        assertEquals(1400L, timeline.snapshot().positionMs)
        timeline.positionChanged(1200); advance(200)
        assertEquals(1400L, timeline.snapshot().positionMs)
    }
    @Test fun pauseAndBufferingFreezeThenResumeWithoutJump() {
        start(); advance(450); timeline.playingChanged(false); advance(2000)
        assertEquals(450L, timeline.snapshot().positionMs)
        timeline.playingChanged(true); advance(50)
        timeline.stateChanged(PlaybackStatus.BUFFERING); advance(1000)
        assertEquals(500L, timeline.snapshot().positionMs)
        assertFalse(timeline.snapshot().playing)
        timeline.stateChanged(PlaybackStatus.READY); advance(200)
        assertEquals(700L, timeline.snapshot().positionMs)
    }
    @Test fun seekDropsOldLineAndResetsAnchor() {
        start(); timeline.lineChanged(LyricLine(0, 5000, "old", null, emptyList())); advance(1000)
        timeline.seek(8000); assertNull(timeline.snapshot().line)
        advance(100); assertEquals(8100L, timeline.snapshot().positionMs)
    }
    @Test fun endAndTrackSwitchCannotKeepOldLyrics() {
        start(); timeline.lineChanged(LyricLine(0, 20000, "old", null, emptyList()))
        timeline.stateChanged(PlaybackStatus.ENDED)
        assertNull(timeline.snapshot().line); assertFalse(timeline.snapshot().playing)
        timeline.trackChanged(Track("New", "Other", "new.flac"))
        assertEquals(0L, timeline.snapshot().positionMs)
        assertEquals("New", timeline.snapshot().track?.title)
        timeline.stateChanged(PlaybackStatus.IDLE); assertNull(timeline.snapshot().track)
    }
    @Test fun expiredLineBecomesInstrumentalAndNullClearsImmediately() {
        start(); timeline.lineChanged(LyricLine(0, 100, "short", null, emptyList()))
        advance(100); assertNull(timeline.snapshot().line)
        timeline.lineChanged(LyricLine(100, 100, "untimed", null, emptyList()))
        assertNotNull(timeline.snapshot().line)
        timeline.lineChanged(null); assertNull(timeline.snapshot().line)
    }
    @Test fun staleHeartbeatDoesNotExtrapolateForever() {
        start(); timeline.positionChanged(1000); advance(60000)
        assertEquals(3500L, timeline.snapshot().positionMs)
    }
    @Test fun timedCellsRespectBoundariesAndMalformedCellsFallBack() {
        val word = Word(100, 300, "Hello")
        assertEquals(0.0, word.progress(50), 0.0)
        assertEquals(0.5, word.progress(200), 0.0)
        assertEquals(1.0, word.progress(400), 0.0)
        assertEquals(1.0, Word(100, 100, "!").progress(100), 0.0)
        assertTrue(LyricLine(0, 500, "Hello!", null, listOf(word)).timedWords.isEmpty())
        assertEquals(listOf(word), LyricLine(0, 500, "Hello", null, listOf(word)).timedWords)
        assertTrue(LyricLine(0, 500, "X", null, listOf(Word(300, 100, "X"))).timedWords.isEmpty())
    }
}
