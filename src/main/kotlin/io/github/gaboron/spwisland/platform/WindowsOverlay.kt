// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.platform

import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.RECT
import com.sun.jna.platform.win32.WinUser.MONITORINFO
import java.awt.Window

/** Windows-only capabilities. All access is from the Swing event thread. */
class WindowsOverlay {
    private val user32 = if (Platform.isWindows()) User32.INSTANCE else null
    private fun handle(window: Window) = HWND(Native.getWindowPointer(window))

    fun clickThrough(window: Window, enabled: Boolean) {
        val api = user32 ?: return
        if (!window.isDisplayable) return
        val hwnd = handle(window)
        val old = api.GetWindowLong(hwnd, -20) // GWL_EXSTYLE
        val flags = 0x20 or 0x80000 // WS_EX_TRANSPARENT | WS_EX_LAYERED
        // Keep LAYERED: AWT uses it for per-pixel transparency even when unlocked.
        val next = if (enabled) old or flags else old and 0x20.inv()
        if (next != old) {
            Native.setLastError(0)
            val result = api.SetWindowLong(hwnd, -20, next)
            check(result != 0 || Native.getLastError() == 0) { "无法更新词岛鼠标穿透：${Native.getLastError()}" }
        }
    }

    fun foregroundIsFullscreen(window: Window): Boolean {
        val api = user32 ?: return false
        if (!window.isDisplayable) return false
        val foreground = api.GetForegroundWindow() ?: return false
        if (foreground == handle(window)) return false
        val className = CharArray(256)
        api.GetClassName(foreground, className, className.size)
        if (Native.toString(className) in setOf("Progman", "WorkerW", "Shell_TrayWnd")) return false
        val monitor = api.MonitorFromWindow(foreground, 2) ?: return false
        // Use native monitor coordinates on both sides, avoiding mixed-DPI coordinate comparisons.
        if (monitor != api.MonitorFromWindow(handle(window), 2)) return false
        val info = MONITORINFO()
        if (!api.GetMonitorInfo(monitor, info).booleanValue()) return false
        val rect = RECT()
        if (!api.GetWindowRect(foreground, rect)) return false
        val screen = info.rcMonitor
        return rect.left <= screen.left && rect.top <= screen.top &&
            rect.right >= screen.right && rect.bottom >= screen.bottom
    }
}
