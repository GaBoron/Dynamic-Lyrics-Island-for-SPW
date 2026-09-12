// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.platform

import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.LRESULT
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinUser.HHOOK
import com.sun.jna.platform.win32.WinUser.KBDLLHOOKSTRUCT
import com.sun.jna.platform.win32.WinUser.LowLevelKeyboardProc
import com.sun.jna.platform.win32.WinUser.MSG
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Desktop-wide low-level keyboard hook (WH_KEYBOARD_LL), armed only while an inline
 * number editor is open.
 *
 * The island is a non-activatable window, so clicking it never brings this process to
 * the foreground: keystrokes go to whatever window really has the focus (the host's
 * main window, or an unrelated application). Java only receives keystrokes delivered
 * to its own windows, so a pure Swing listener is unreliable here — the hook is not
 * tied to the foreground window, and it can swallow the keys the editor consumes
 * before any window sees them.
 *
 * [onKeyDown] runs on the hook thread, so it must be fast and must not touch Swing
 * state; return `true` to swallow the key (its key-up is swallowed as well).
 */
class KeyboardCapture(private val onKeyDown: (Int) -> Boolean) : AutoCloseable {
    private class HookSession {
        lateinit var callback: LowLevelKeyboardProc
        @Volatile var hook: HHOOK? = null
        @Volatile var threadId = 0

        /** Virtual keys whose key-down was swallowed, so their key-up is swallowed too. */
        val swallowed = HashSet<Int>()
    }

    private val user32 = User32.INSTANCE
    private val kernel32 = Kernel32.INSTANCE
    @Volatile private var activeSession: HookSession? = null

    /** Installs the hook. Returns false when the hook could not be armed. */
    fun arm(): Boolean {
        close()
        val session = HookSession()
        session.callback = object : LowLevelKeyboardProc {
            override fun callback(nCode: Int, wParam: WPARAM, info: KBDLLHOOKSTRUCT): LRESULT {
                if (nCode >= 0 && activeSession === session) {
                    when (wParam.toInt()) {
                        WM_KEYDOWN, WM_SYSKEYDOWN ->
                            if (onKeyDown(info.vkCode)) {
                                session.swallowed.add(info.vkCode)
                                return CONSUMED
                            }
                        WM_KEYUP, WM_SYSKEYUP ->
                            if (session.swallowed.remove(info.vkCode)) return CONSUMED
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
            // Create this thread's queue before publishing its id so that close()
            // can always wake GetMessage with WM_QUIT.
            user32.PeekMessage(message, null, 0, 0, PM_NOREMOVE)
            session.threadId = kernel32.GetCurrentThreadId()
            val hook = user32.SetWindowsHookEx(
                WH_KEYBOARD_LL, session.callback, kernel32.GetModuleHandle(null), 0
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
        }, "spw-key-hook-pump").apply { isDaemon = true }.start()

        val ok = try {
            ready.await(2, TimeUnit.SECONDS) && installed.get() == 1
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        if (!ok) close()
        return ok
    }

    /** True while Ctrl is physically held; safe to call from the hook thread. */
    fun isControlDown(): Boolean = user32.GetAsyncKeyState(VK_CONTROL).toInt() < 0

    /** True while Shift is physically held; safe to call from the hook thread. */
    fun isShiftDown(): Boolean = user32.GetAsyncKeyState(VK_SHIFT).toInt() < 0

    /** True while Alt is physically held; safe to call from the hook thread. */
    fun isAltDown(): Boolean = user32.GetAsyncKeyState(VK_MENU).toInt() < 0

    override fun close() {
        val session = activeSession ?: return
        activeSession = null
        session.hook?.let(user32::UnhookWindowsHookEx)
        session.hook = null
        val threadId = session.threadId
        if (threadId != 0) user32.PostThreadMessage(threadId, WM_QUIT, WPARAM(0), LPARAM(0))
    }

    companion object {
        private const val WH_KEYBOARD_LL = 13
        private const val VK_CONTROL = 0x11
        private const val VK_SHIFT = 0x10
        private const val VK_MENU = 0x12
        private const val WM_KEYDOWN = 0x0100
        private const val WM_KEYUP = 0x0101
        private const val WM_SYSKEYDOWN = 0x0104
        private const val WM_SYSKEYUP = 0x0105
        private const val WM_QUIT = 0x0012
        private const val PM_NOREMOVE = 0
        private val CONSUMED = LRESULT(1)
    }
}
