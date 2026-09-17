// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.platform

import com.sun.jna.platform.unix.X11
import com.sun.jna.ptr.IntByReference
import java.awt.MouseInfo
import java.awt.Window
import javax.swing.Timer

/** XToolkit fallback for desktop clicks, which never enter the JVM's AWT event queue.
 * Owns a separate X connection only while the menu is open; does not grab or consume input.
 * All calls (including close) run on the EDT. Native Wayland is deliberately excluded.
 */
internal class X11MenuDismisser(private val window: Window, private val outsidePress: () -> Unit) : AutoCloseable {
    private val api = X11.INSTANCE
    private val display = checkNotNull(api.XOpenDisplay(null)) { "Cannot open X11 display" }
    private val root = api.XDefaultRootWindow(display)
    private val rootReturn = X11.WindowByReference()
    private val childReturn = X11.WindowByReference()
    private val rootX = IntByReference()
    private val rootY = IntByReference()
    private val windowX = IntByReference()
    private val windowY = IntByReference()
    private val mask = IntByReference()
    private var previousButtons = 0
    private var closed = false
    private val timer = Timer(8) { poll() }

    init {
        readButtons()?.let { previousButtons = it }
        timer.start()
    }

    private fun readButtons(): Int? = if (api.XQueryPointer(display, root, rootReturn, childReturn,
            rootX, rootY, windowX, windowY, mask)) mask.value and 0x1f00 else null

    private fun poll() {
        if (closed) return
        val buttons = readButtons() ?: return
        val pressed = buttons and previousButtons.inv()
        previousButtons = buttons
        // AWT supplies logical screen coordinates, matching Window.bounds on scaled displays.
        if (pressed != 0) {
            val point = MouseInfo.getPointerInfo()?.location ?: return
            if (!window.bounds.contains(point)) outsidePress()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        timer.stop()
        api.XCloseDisplay(display)
    }
}
