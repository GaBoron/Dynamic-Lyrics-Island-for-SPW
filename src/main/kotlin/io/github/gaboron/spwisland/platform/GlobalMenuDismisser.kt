// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.platform

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinDef.LRESULT
import com.sun.jna.platform.win32.WinUser.MSG
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities

/**
 * Global low-level mouse hook (WH_MOUSE_LL) that dismisses the tray menu on an
 * outside click without swallowing the click.
 *
 * While [arm]ed it calls [onOutsidePress] whenever any mouse button is pressed
 * at a screen point OUTSIDE the pinned menu window. The hook never consumes the
 * event - it always returns `CallNextHookEx` - so the original click keeps
 * reaching the window underneath. That is exactly the native tray-menu behaviour:
 * clicking anywhere dismisses the menu, and the same click still acts on whatever
 * the user aimed at (an input box receives focus and can be typed into, a button
 * fires, etc.).
 *
 * A low-level hook is driven by the message pump of the thread that installed it:
 * the system posts every mouse event to that thread's queue and waits for the
 * callback to return. Installing the hook on the EDT would therefore funnel every
 * mouse move through the busy Swing event loop and make the cursor feel laggy
 * while the menu is up. Instead the hook is installed on a dedicated pump thread
 * that does nothing but GetMessage/DispatchMessage, so mouse events are answered
 * immediately and the EDT only receives a lightweight "dismiss" notification.
 */
class GlobalMenuDismisser(private val onOutsidePress: () -> Unit) {
    private fun interface LowLevelMouseProc : Callback {
        fun callback(nCode: Int, wParam: Int, lParam: Pointer): LRESULT
    }

    private interface User32HookLib : Library {
        companion object {
            val INSTANCE: User32HookLib = Native.load("user32", User32HookLib::class.java)
        }

        fun SetWindowsHookExW(idHook: Int, lpfn: LowLevelMouseProc, hMod: Pointer?, dwThreadId: Int): Pointer?
        fun UnhookWindowsHookEx(hhk: Pointer): Boolean
        fun CallNextHookEx(hhk: Pointer?, nCode: Int, wParam: Int, lParam: Pointer): LRESULT
        fun GetWindowRect(hwnd: WinDef.HWND, rect: WinDef.RECT): Boolean
        fun GetMessageW(lpMsg: MSG, hWnd: WinDef.HWND?, wMsgFilterMin: Int, wMsgFilterMax: Int): Int
        fun TranslateMessage(lpMsg: MSG): Boolean
        fun DispatchMessageW(lpMsg: MSG): Int
        fun PostThreadMessageW(idThread: Int, msg: Int, wParam: Int, lParam: Int): Boolean
    }

    // GetModuleHandleW/GetCurrentThreadId live in kernel32, not user32.
    private interface Kernel32HookLib : Library {
        companion object {
            val INSTANCE: Kernel32HookLib = Native.load("kernel32", Kernel32HookLib::class.java)
        }

        fun GetModuleHandleW(lpModuleName: String?): Pointer?
        fun GetCurrentThreadId(): Int
    }

    private val api = User32HookLib.INSTANCE
    private val kernel = Kernel32HookLib.INSTANCE

    // Monotonic epoch; only the most recently armed hook may dismiss the menu.
    // A stale pump thread winding down for an older arm() must never clobber a
    // newer one, so shared state is never written back by the pump thread on exit.
    private val epoch = AtomicInteger(0)

    @Volatile private var activeCallback: LowLevelMouseProc? = null // strong ref so JNA never GCs the proxy
    @Volatile private var hookHandle: Pointer? = null
    @Volatile private var pumpThreadId = 0

    // Menu window rect in physical screen pixels, captured at arm() time.
    @Volatile private var menuLeft = 0; @Volatile private var menuTop = 0
    @Volatile private var menuRight = 0; @Volatile private var menuBottom = 0

    /** Pins [window] (the heavyweight tray-menu window) and arms the global hook on a dedicated pump thread. */
    fun arm(window: java.awt.Window): Boolean {
        disarm()
        if (!window.isDisplayable) return false
        val hwnd = WinDef.HWND(Native.getWindowPointer(window))
        val rect = WinDef.RECT()
        if (!api.GetWindowRect(hwnd, rect)) return false
        menuLeft = rect.left; menuTop = rect.top
        menuRight = rect.right; menuBottom = rect.bottom

        val gen = epoch.incrementAndGet()
        val callback = LowLevelMouseProc { nCode: Int, wParam: Int, lParam: Pointer ->
            if (nCode >= 0 && epoch.get() == gen &&
                (wParam == WM_LBUTTONDOWN || wParam == WM_RBUTTONDOWN || wParam == WM_MBUTTONDOWN)
            ) {
                val x = lParam.getInt(0L)   // MSLLHOOKSTRUCT.pt (physical screen pixels)
                val y = lParam.getInt(4L)
                if (x < menuLeft || x > menuRight || y < menuTop || y > menuBottom) {
                    SwingUtilities.invokeLater { onOutsidePress() }
                }
            }
            // hhk is ignored for low-level hooks; NULL is valid and safe.
            api.CallNextHookEx(null, nCode, wParam, lParam)
        }
        activeCallback = callback

        val ready = CountDownLatch(1)
        val ok = AtomicInteger(-1)   // -1 pending, 0 failed, 1 armed
        val thread = Thread({
            pumpThreadId = kernel.GetCurrentThreadId()
            val handle = api.SetWindowsHookExW(WH_MOUSE_LL, callback, kernel.GetModuleHandleW(null), 0)
            if (handle == null) {
                ok.set(0)
                ready.countDown()
                return@Thread
            }
            hookHandle = handle
            ok.set(1)
            ready.countDown()
            // Pump this thread's queue: this is what delivers the hook messages.
            val msg = MSG()
            while (true) {
                val r = api.GetMessageW(msg, null, 0, 0)
                if (r <= 0) break   // -1 error, 0 = WM_QUIT
                api.TranslateMessage(msg)
                api.DispatchMessageW(msg)
            }
            // Wind-down: unhook OUR handle only, and only if we still own it.
            if (epoch.get() == gen) {
                api.UnhookWindowsHookEx(handle)
                if (hookHandle == handle) hookHandle = null
            }
        }, "spw-menu-hook-pump").apply { isDaemon = true }
        thread.start()

        val installed = try {
            ready.await(2, TimeUnit.SECONDS) && ok.get() == 1
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        if (!installed) disarm()
        return installed
    }

    fun disarm() {
        epoch.incrementAndGet()   // invalidate any live callback immediately
        val handle = hookHandle
        hookHandle = null
        if (handle != null) api.UnhookWindowsHookEx(handle)
        val id = pumpThreadId
        pumpThreadId = 0
        if (id != 0) api.PostThreadMessageW(id, WM_QUIT, 0, 0)
        activeCallback = null
    }

    fun close() = disarm()

    companion object {
        private const val WH_MOUSE_LL = 14
        private const val WM_LBUTTONDOWN = 0x0201
        private const val WM_RBUTTONDOWN = 0x0204
        private const val WM_MBUTTONDOWN = 0x0207
        private const val WM_QUIT = 0x0012
    }
}