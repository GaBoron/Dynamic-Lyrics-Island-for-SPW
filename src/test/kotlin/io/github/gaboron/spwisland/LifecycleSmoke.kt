// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(com.xuncorp.spw.workshop.api.UnstableSpwWorkshopApi::class)
package io.github.gaboron.spwisland

import com.sun.jna.Native
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.xuncorp.spw.workshop.api.*
import io.github.gaboron.spwisland.host.*
import java.awt.SystemTray
import java.awt.Window
import javax.swing.SwingUtilities

/** Runs in its own JVM, acting as the host (never injects into a real SPW process). */
fun main() {
    val config = MemoryConfig().apply { values["enabled"] = false; values["hide_fullscreen"] = false }
    val errors = mutableListOf<String>()
    WorkshopApi.instance = object : WorkshopApi {
        override val playback = object : WorkshopApi.Playback {
            override fun changeExclusive(exclusive: Boolean) {}
            override fun pause() {}; override fun play() {}; override fun previous() {}; override fun next() {}
            override fun seekTo(position: Long) {}
        }
        override val ui = object : WorkshopApi.Ui {
            override fun toast(text: String, type: WorkshopApi.Ui.ToastType) { if (type == WorkshopApi.Ui.ToastType.Error) errors.add(text) }
        }
        override val manager = object : WorkshopApi.Manager {
            override fun createConfigManager() = config
            @Suppress("OVERRIDE_DEPRECATION") override fun createConfigManager(pluginId: String) = config
        }
    }
    val trayCount = if (SystemTray.isSupported()) SystemTray.getSystemTray().trayIcons.size else 0
    val context = PluginContext("io.github.gaboron.spwisland", "0.1.0", ".", "smoke", Channel.MS)
    val plugin = IslandPlugin(context)
    repeat(2) {
        try {
            plugin.start()
            val extension = IslandPlaybackExtension()
            val media = PlaybackExtensionPoint.MediaItem("Synthetic", "Test", "Album", "Test", "synthetic.flac")
            check(extension.onBeforeLoadLyrics(media) == null)
            extension.onStateChanged(PlaybackExtensionPoint.State.Ready)
            extension.onIsPlayingChanged(true)
            extension.onPositionUpdated(1000)
            extension.onLyricsLineUpdated(PlaybackExtensionPoint.LyricsLine(0, 5000, emptyList(), "Synthetic lyrics", null))
            check(IslandPlugin.active()?.timeline?.snapshot()?.line?.text == "Synthetic lyrics")
            extension.onSeekTo(3000)
            check(IslandPlugin.active()?.timeline?.snapshot()?.line == null)
            for (enabled in listOf(true, false)) {
                config.values["click_through"] = enabled
                config.listeners.forEach { listener -> listener.accept(config) }
                // Flush the configuration reload already enqueued on the EDT.
                SwingUtilities.invokeAndWait {
                    val windows = Window.getWindows().filter { w -> w.isDisplayable && w.name == "Dynamic Lyrics Island for SPW" }
                    check(windows.size == 1)
                    val island = windows.single(); check(!island.isVisible)
                    val flags = User32.INSTANCE.GetWindowLong(HWND(Native.getWindowPointer(island)), -20)
                    check((flags and 0x20 != 0) == enabled) { "Click-through style mismatch" }
                }
            }
        } finally { plugin.stop() }
        SwingUtilities.invokeAndWait {
            check(Window.getWindows().none { w -> w.isDisplayable && w.name == "Dynamic Lyrics Island for SPW" })
        }
        check(config.listeners.isEmpty())
        check(ProcessHandle.current().descendants().noneMatch { child ->
            child.info().command().orElse("").endsWith("spw-spectrum.exe", ignoreCase = true)
        }) { "Audio helper survived plugin stop" }
        check(Thread.getAllStackTraces().keys.none { thread -> thread.isAlive && thread.name == "SPW Island settings sync" })
        check(IslandPlugin.active() == null)
        if (SystemTray.isSupported()) check(SystemTray.getSystemTray().trayIcons.size == trayCount)
    }
    check(errors.isEmpty()) { errors.joinToString() }
    println("PASS: two host lifecycle cycles, callback routing, native click-through, window/tray disposal, config sync and audio helper shutdown")
}
