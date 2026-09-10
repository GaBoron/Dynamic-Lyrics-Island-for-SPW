// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.host

import com.xuncorp.spw.workshop.api.PlaybackExtensionPoint
import io.github.gaboron.spwisland.core.*

/** Registered through META-INF/extensions.idx; never supplies or replaces the host's lyrics. */
class IslandPlaybackExtension : PlaybackExtensionPoint {
    override fun onBeforeLoadLyrics(mediaItem: PlaybackExtensionPoint.MediaItem): String? {
        IslandPlugin.active()?.trackChanged(Track(mediaItem.title, mediaItem.artist, mediaItem.path))
        return null
    }
    override fun onStateChanged(state: PlaybackExtensionPoint.State) {
        IslandPlugin.active()?.timeline?.stateChanged(when (state) {
            PlaybackExtensionPoint.State.Idle -> PlaybackStatus.IDLE
            PlaybackExtensionPoint.State.Buffering -> PlaybackStatus.BUFFERING
            PlaybackExtensionPoint.State.Ready -> PlaybackStatus.READY
            PlaybackExtensionPoint.State.Ended -> PlaybackStatus.ENDED
        })
    }
    override fun onIsPlayingChanged(isPlaying: Boolean) { IslandPlugin.active()?.timeline?.playingChanged(isPlaying) }
    override fun onPositionUpdated(position: Long) { IslandPlugin.active()?.timeline?.positionChanged(position) }
    override fun onSeekTo(position: Long) { IslandPlugin.active()?.timeline?.seek(position) }
    override fun onLyricsLineUpdated(lyricsLine: PlaybackExtensionPoint.LyricsLine?) {
        IslandPlugin.active()?.lineChanged(lyricsLine?.let { line ->
            LyricLine(line.startTime, line.endTime, line.pureMainText, line.pureSubText,
                line.lyricsCells.map { Word(it.startTime, it.endTime, it.text) })
        })
    }
}
