// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.host

import com.xuncorp.spw.workshop.api.PlaybackExtensionPoint
import io.github.gaboron.spwisland.core.*

/** Registered through META-INF/extensions.idx; never supplies or replaces the host's lyrics. */
class IslandPlaybackExtension : PlaybackExtensionPoint {
    override fun onBeforeLoadLyrics(mediaItem: PlaybackExtensionPoint.MediaItem): String? {
        IslandPlugin.playback.trackChanged(Track(mediaItem.title, mediaItem.artist, mediaItem.path))
        return null
    }
    override fun onStateChanged(state: PlaybackExtensionPoint.State) {
        IslandPlugin.playback.stateChanged(when (state) {
            PlaybackExtensionPoint.State.Idle -> PlaybackStatus.IDLE
            PlaybackExtensionPoint.State.Buffering -> PlaybackStatus.BUFFERING
            PlaybackExtensionPoint.State.Ready -> PlaybackStatus.READY
            PlaybackExtensionPoint.State.Ended -> PlaybackStatus.ENDED
        })
    }
    override fun onIsPlayingChanged(isPlaying: Boolean) { IslandPlugin.playback.playingChanged(isPlaying) }
    override fun onPositionUpdated(position: Long) { IslandPlugin.playback.positionChanged(position) }
    override fun onSeekTo(position: Long) { IslandPlugin.playback.seek(position) }
    override fun onLyricsLineUpdated(lyricsLine: PlaybackExtensionPoint.LyricsLine?) {
        IslandPlugin.playback.lineChanged(lyricsLine?.let { line ->
            LyricLine(line.startTime, line.endTime, line.pureMainText, line.pureSubText,
                line.lyricsCells.map { Word(it.startTime, it.endTime, it.text) })
        })
    }
}
