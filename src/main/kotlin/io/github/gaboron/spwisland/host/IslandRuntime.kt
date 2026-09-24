// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(com.xuncorp.spw.workshop.api.UnstableSpwWorkshopApi::class)
package io.github.gaboron.spwisland.host

import com.xuncorp.spw.workshop.api.WorkshopApi
import io.github.gaboron.spwisland.core.PlaybackTimeline
import io.github.gaboron.spwisland.core.SpectrumMode
import io.github.gaboron.spwisland.core.performance
import io.github.gaboron.spwisland.ui.*
import io.github.gaboron.spwisland.platform.ProcessSpectrum
import io.github.gaboron.spwisland.platform.WindowsFontPicker
import com.sun.jna.Platform
import io.github.gaboron.spwisland.remote.LinuxIslandProcess
import io.github.gaboron.spwisland.remote.WindowsIslandProcess
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
        if (current.performance.probeHostLyrics && line != null) {
            playbackProbe.readLyrics()?.takeIf { document ->
                document.any { it.startMs == line.startMs && it.text == line.text }
            }?.let(timeline::lyricsChanged)
        }
    }
    private var linuxWindow: LinuxIslandProcess? = null
    private var nativeWindow: WindowsIslandProcess? = null
    private val aboutDialog = if (Platform.isWindows()) AboutDialog(null, ::report) else null
    private val spectrum = ProcessSpectrum(::notifySpectrumFallback)
    @Volatile private var closed = false
    private val settings = HostSettings(WorkshopApi.manager.createConfigManager()) {
        updateSpectrumMode()
    }
    private val fontPicker = if (Platform.isWindows()) WindowsFontPicker(
        { choice -> settings.setFont(choice.family, choice.weight, choice.size, choice.style, choice.stretch) }, ::report
    ) else null
    private val keyboard = KeyEventDispatcher { e ->
        if (!closed && e.id == KeyEvent.KEY_RELEASED && e.keyCode == KeyEvent.VK_D &&
            e.isControlDown && e.isShiftDown && !e.isAltDown && !e.isMetaDown) {
            safely { settings.set("enabled", !settings.read().enabled) }; true
        } else false
    }
    fun start() {
        updateSpectrumMode()
        currentTrackRecovery.start()
        val actions = object : PlaybackActions {
                override fun previous() = safely { WorkshopApi.playback.previous() }
                override fun toggle() = safely {
                    if (timeline.snapshot().playing) WorkshopApi.playback.pause() else WorkshopApi.playback.play()
                }
                override fun next() = safely { WorkshopApi.playback.next() }
                override fun seek(positionMs: Long) = safely {
                    WorkshopApi.playback.seekTo(positionMs)
                    timeline.seek(positionMs)
                }
        }
        if (Platform.isLinux()) {
            linuxWindow = LinuxIslandProcess(timeline, settings, actions, ::report)
        } else if (Platform.isWindows()) {
            nativeWindow = WindowsIslandProcess(timeline, settings, actions, spectrum::levels,
                ::notifyWindowsRuntimeMissing, ::about, ::openSource)
        } else error("当前系统不支持词岛")
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyboard)
    }
    private fun updateSpectrumMode() {
        val current = settings.read()
        spectrum.setEnabled(current.performance.spectrumMode == SpectrumMode.LIVE &&
            current.sideContent.showsSpectrum)
    }
    fun recover() = safely {
        settings.set("click_through", false); settings.set("enabled", true); settings.resetPosition()
    }
    fun resetSettings() = safely { settings.resetAll() }
    fun about() {
        if (closed) return
        if (Platform.isLinux()) linuxWindow?.about()
        else SwingUtilities.invokeLater { if (!closed) aboutDialog?.show() }
    }
    fun chooseFont() {
        if (!closed) fontPicker?.show(settings.read())
    }

    fun openSource() { SwingUtilities.invokeLater { if (!closed) safely { ProjectLinks.openSource() } } }
    private fun safely(block: () -> Unit) { try { block() } catch (e: Exception) { report(e) } }
    private fun report(error: Throwable) {
        System.err.println("[SPW Island] ${error.message}"); error.printStackTrace()
        runCatching { WorkshopApi.ui.toast(error.message ?: "词岛操作失败", WorkshopApi.Ui.ToastType.Error) }
    }
    private fun notifySpectrumFallback(message: String) {
        runCatching { WorkshopApi.ui.toast(message, WorkshopApi.Ui.ToastType.Warning) }
    }
    private fun notifyWindowsRuntimeMissing(message: String) {
        runCatching { WorkshopApi.ui.toast(message, WorkshopApi.Ui.ToastType.Warning) }
    }
    override fun close() {
        if (closed) return
        closed = true
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keyboard)
        linuxWindow?.close(); linuxWindow = null
        nativeWindow?.close(); nativeWindow = null
        fontPicker?.close(); currentTrackRecovery.close(); metadata.close(); settings.close(); spectrum.close()
        aboutDialog?.let { SwingUtilities.invokeLater { it.close() } }
    }
}
