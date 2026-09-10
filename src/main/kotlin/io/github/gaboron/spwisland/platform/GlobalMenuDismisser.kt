// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.platform

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.LRESULT
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinUser.HHOOK
import com.sun.jna.platform.win32.WinUser.LowLevelMouseProc
import com.sun.jna.platform.win32.WinUser.MSG
import com.sun.jna.platform.win32.WinUser.MSLLHOOKSTRUCT
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities

/** Dismisses one heavyweight popup after a mouse press outside its native window. */
class GlobalMenuDismisser(private val onOutsidePress: () -> Unit) {
    private class HookSession {
        lateinit var callback: LowLevelMouseProc
        @Volatile var hook: HHOOK? = null
        @Volatile var threadId = 0
    }

    private val user32 = User32.INSTANCE
    private val kernel32 = Kernel32.INSTANCE
    @Volatile private var activeSession: HookSession? = null

    fun arm(window: java.awt.Window): Boolean {
        disarm()
        if (!window.isDisplayable) return false
        val pointer = Native.getWindowPointer(window) ?: return false
        if (Pointer.nativeValue(pointer) == 0L) return false
        val rect = com.sun.jna.platform.win32.WinDef.RECT()
        if (!user32.GetWindowRect(HWND(pointer), rect)) return false

        val session = HookSession()
        session.callback = object : LowLevelMouseProc {
            override fun callback(nCode: Int, wParam: WPARAM, info: MSLLHOOKSTRUCT): LRESULT {
                if (nCode >= 0 && activeSession === session && wParam.toInt() in PRESS_MESSAGES &&
                    (info.pt.x < rect.left || info.pt.x >= rect.right ||
                        info.pt.y < rect.top || info.pt.y >= rect.bottom)
                ) {
                    SwingUtilities.invokeLater {
                        if (activeSession === session) onOutsidePress()
                    }
                }
                return user32.CallNextHookEx(
                    null, nCode, wParam, LPARAM(Pointer.nativeValue(info.pointer))
                )
            }
        }
        activeSession = session

        val ready = CountDownLatch(1)
        val installed = AtomicInteger(-1)
        Thread({
            val message = MSG()
            // Force creation of this thread's queue before publishing its id, so
            // disarm() can always wake GetMessage with WM_QUIT.
            user32.PeekMessage(message, null, 0, 0, PM_NOREMOVE)
            session.threadId = kernel32.GetCurrentThreadId()
            val hook = user32.SetWindowsHookEx(
                WH_MOUSE_LL, session.callback, kernel32.GetModuleHandle(null), 0
            )
            if (hook == null) {
                installed.set(0)
                ready.countDown()
                return@Thread
            }
            session.hook = hook
            if (activeSession !== session) {
                user32.UnhookWindowsHookEx(hook)
                session.hook = null
                installed.set(0)
                ready.countDown()
                return@Thread
            }
            installed.set(1)
            ready.countDown()
            try {
                while (true) {
                    val result = user32.GetMessage(message, null, 0, 0)
                    if (result <= 0) break
                    user32.TranslateMessage(message)
                    user32.DispatchMessage(message)
                }
            } finally {
                session.hook?.let(user32::UnhookWindowsHookEx)
                session.hook = null
            }
        }, "spw-menu-hook-pump").apply { isDaemon = true }.start()

        val ok = try {
            ready.await(2, TimeUnit.SECONDS) && installed.get() == 1
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        if (!ok) disarm()
        return ok
    }

    fun disarm() {
        val session = activeSession ?: return
        activeSession = null
        session.hook?.let(user32::UnhookWindowsHookEx)
        session.hook = null
        val threadId = session.threadId
        if (threadId != 0) user32.PostThreadMessage(threadId, WM_QUIT, WPARAM(0), LPARAM(0))
    }

    fun close() = disarm()

    companion object {
        private const val WH_MOUSE_LL = 14
        private const val WM_LBUTTONDOWN = 0x0201
        private const val WM_RBUTTONDOWN = 0x0204
        private const val WM_MBUTTONDOWN = 0x0207
        private const val WM_QUIT = 0x0012
        private const val PM_NOREMOVE = 0
        private val PRESS_MESSAGES = setOf(WM_LBUTTONDOWN, WM_RBUTTONDOWN, WM_MBUTTONDOWN)
    }
}
