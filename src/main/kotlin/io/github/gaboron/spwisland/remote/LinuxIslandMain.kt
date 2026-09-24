// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.remote

import io.github.gaboron.spwisland.core.*
import io.github.gaboron.spwisland.ui.*
import java.io.*
import javax.swing.SwingUtilities

/** Entry point loaded with the host's bundled JVM, even when jpackage omits bin/java. */
object LinuxIslandMain {
    @JvmStatic fun main(args: Array<String>) {
        val output = ObjectOutputStream(BufferedOutputStream(FileOutputStream(FileDescriptor.out))).apply { flush() }
        // Libraries must not write text into the binary command pipe.
        System.setOut(System.err)
        fun send(action: String, vararg arguments: String) = synchronized(output) {
            output.writeObject(IslandCommand(action, arguments.toList())); output.reset(); output.flush()
        }
        val timeline = RemotePlayback()
        val store = object : SettingsStore {
            @Volatile var current = IslandSettings()
            override fun read() = current
            override fun set(key: String, value: Any) = send("set", key, if (value is Boolean) "boolean" else "string", value.toString())
            override fun savePosition(screen: String, x: Int, y: Int, anchor: IslandAnchor) =
                send("position", screen, x.toString(), y.toString(), anchor.storageName)
            override fun resetPosition() = send("reset")
            override fun resetAll() = send("reset_all")
        }
        var window: IslandWindow? = null
        var lastHealth = 0L
        try {
            ObjectInputStream(BufferedInputStream(System.`in`)).use { input ->
                while (true) {
                    val state = input.readObject() as IslandState
                    timeline.accept(state)
                    state.settings?.let { store.current = it }
                    SwingUtilities.invokeAndWait {
                        if (window == null) window = IslandWindow(timeline, store, object : PlaybackActions {
                            override fun previous() = send("previous")
                            override fun toggle() = send("toggle")
                            override fun next() = send("next")
                            override fun seek(positionMs: Long) = send("seek", positionMs.toString())
                        }, { send("error", it.toString()) })
                        else if (state.settings != null) window.reload()
                        if (state.about) window.about()
                        val now = System.nanoTime()
                        if (now - lastHealth >= 1_000_000_000L) {
                            send("frames", window.presentedFrames.toString())
                            lastHealth = now
                        }
                    }
                }
            }
        } catch (_: EOFException) {
            // The host unloaded the plugin or exited. No orphan overlay.
        } finally {
            SwingUtilities.invokeAndWait { window?.close() }
        }
    }
}
