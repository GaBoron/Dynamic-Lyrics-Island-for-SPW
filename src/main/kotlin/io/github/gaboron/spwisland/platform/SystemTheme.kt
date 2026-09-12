// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.platform

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg

/** Reads the Windows app theme, so the Swing menus can follow light/dark mode. */
object SystemTheme {
    private const val PERSONALIZE = "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize"

    /**
     * True when Windows is in light mode (`AppsUseLightTheme` = 1), false for dark mode or
     * when the value cannot be read. `-Dspwisland.menuTheme=light|dark` overrides the
     * registry, which is also how the light palette is exercised in tests.
     */
    fun isLight(): Boolean {
        when (System.getProperty("spwisland.menuTheme")?.lowercase()) {
            "light" -> return true
            "dark" -> return false
        }
        return runCatching {
            Advapi32Util.registryGetIntValue(WinReg.HKEY_CURRENT_USER, PERSONALIZE, "AppsUseLightTheme") != 0
        }.getOrDefault(false)
    }
}
