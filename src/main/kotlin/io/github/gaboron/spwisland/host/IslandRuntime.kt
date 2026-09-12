// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(com.xuncorp.spw.workshop.api.UnstableSpwWorkshopApi::class)
package io.github.gaboron.spwisland.host

import com.xuncorp.spw.workshop.api.WorkshopApi
import io.github.gaboron.spwisland.core.LeadingContent
import io.github.gaboron.spwisland.core.PlaybackTimeline
import io.github.gaboron.spwisland.core.SpectrumMode
import io.github.gaboron.spwisland.core.performance
import io.github.gaboron.spwisland.ui.*
import io.github.gaboron.spwisland.platform.ProcessSpectrum
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import javax.swing.SwingUtilities

class IslandRuntime : AutoCloseable {
    val timeline = PlaybackTimeline()
    private val metadata = TrackMetadataLoader(timeline)
    private val playbackProbe = HostPlaybackProbe()
    private val currentTrackRecovery = CurrentTrackRecovery(timeline, playbackProbe::readTrack, metadata::load)
    fun trackChanged(track: io.github.gaboron.spwisland.core.Track) = metadata.load(track)
    fun lineChanged(line: io.github.gaboron.spwisland.core.LyricLine?) {
        timeline.lineChanged(line)
        val current = settings.read()
        if (current.experimentalMultiLine && current.performance.probeHostLyrics && line != null) {
            playbackProbe.readLyrics()?.takeIf { document ->
                document.any { it.startMs == line.startMs && it.text == line.text }
            }?.let(timeline::lyricsChanged)
        }
    }
    private var window: IslandWindow? = null
    private val spectrum = ProcessSpectrum()
    @Volatile private var closed = false
    private val settings = HostSettings(WorkshopApi.manager.createConfigManager()) {
        updateSpectrumMode()
        SwingUtilities.invokeLater { if (!closed) window?.reload() }
    }
    private val keyboard = KeyEventDispatcher { e ->
        if (!closed && e.id == KeyEvent.KEY_RELEASED && e.keyCode == KeyEvent.VK_D &&
            e.isControlDown && e.isShiftDown && !e.isAltDown && !e.isMetaDown) {
            safely { settings.set("enabled", !settings.read().enabled) }; true
        } else false
    }
    fun start() {
        updateSpectrumMode()
        currentTrackRecovery.start()
        onEdt {
            window = IslandWindow(timeline, settings, object : PlaybackActions {
                override fun previous() = safely { WorkshopApi.playback.previous() }
                override fun toggle() = safely {
                    if (timeline.snapshot().playing) WorkshopApi.playback.pause() else WorkshopApi.playback.play()
                }
                override fun next() = safely { WorkshopApi.playback.next() }
                override fun seek(positionMs: Long) = safely {
                    WorkshopApi.playback.seekTo(positionMs)
                    timeline.seek(positionMs)
                }
            }, ::report, spectrum::levels)
        }
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyboard)
    }
    private fun updateSpectrumMode() {
        val current = settings.read()
        spectrum.setEnabled(current.performance.spectrumMode == SpectrumMode.LIVE &&
            current.leadingContent == LeadingContent.SPECTRUM)
    }
    fun recover() = safely {
        settings.set("click_through", false); settings.set("enabled", true); settings.resetPosition()
    }
    fun about() { SwingUtilities.invokeLater { if (!closed) window?.about() } }

    fun openSource() { SwingUtilities.invokeLater { if (!closed) safely { ProjectLinks.openSource() } } }
    private fun safely(block: () -> Unit) { try { block() } catch (e: Exception) { report(e) } }
    private fun report(error: Throwable) {
        System.err.println("[SPW Island] ${error.message}"); error.printStackTrace()
        runCatching { WorkshopApi.ui.toast(error.message ?: "词岛操作失败", WorkshopApi.Ui.ToastType.Error) }
    }
    override fun close() {
        if (closed) return
        closed = true
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keyboard)
        try {
            currentTrackRecovery.close(); metadata.close(); settings.close(); spectrum.close()
        } finally { onEdt { window?.close(); window = null } }
    }
    private fun onEdt(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeAndWait(block)
    }
}
