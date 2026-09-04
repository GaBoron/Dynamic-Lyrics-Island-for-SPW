// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(com.xuncorp.spw.workshop.api.UnstableSpwWorkshopApi::class)
package io.github.gaboron.spwisland.host

import com.xuncorp.spw.workshop.api.WorkshopApi
import io.github.gaboron.spwisland.core.PlaybackTimeline
import io.github.gaboron.spwisland.ui.*
import io.github.gaboron.spwisland.platform.ProcessSpectrum
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import javax.swing.SwingUtilities

class IslandRuntime : AutoCloseable {
    val timeline = PlaybackTimeline()
    private var window: IslandWindow? = null
    private val spectrum = ProcessSpectrum()
    @Volatile private var closed = false
    private val settings = HostSettings(WorkshopApi.manager.createConfigManager()) {
        SwingUtilities.invokeLater { if (!closed) window?.reload() }
    }
    private val keyboard = KeyEventDispatcher { e ->
        if (!closed && e.id == KeyEvent.KEY_RELEASED && e.keyCode == KeyEvent.VK_D &&
            e.isControlDown && e.isShiftDown && !e.isAltDown && !e.isMetaDown) {
            safely { settings.set("enabled", !settings.read().enabled) }; true
        } else false
    }
    fun start() {
        onEdt {
            window = IslandWindow(timeline, settings, object : PlaybackActions {
                override fun previous() = safely { WorkshopApi.playback.previous() }
                override fun toggle() = safely {
                    if (timeline.snapshot().playing) WorkshopApi.playback.pause() else WorkshopApi.playback.play()
                }
                override fun next() = safely { WorkshopApi.playback.next() }
            }, ::report, spectrum::levels, { spectrum.status })
        }
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyboard)
    }
    fun recover() = safely {
        settings.set("click_through", false); settings.set("enabled", true); settings.resetPosition()
    }
    fun about() { SwingUtilities.invokeLater { if (!closed) window?.about() } }
    fun showSettings() { SwingUtilities.invokeLater { if (!closed) window?.showSettings() } }
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
        try { settings.close(); spectrum.close() } finally { onEdt { window?.close(); window = null } }
    }
    private fun onEdt(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeAndWait(block)
    }
}
