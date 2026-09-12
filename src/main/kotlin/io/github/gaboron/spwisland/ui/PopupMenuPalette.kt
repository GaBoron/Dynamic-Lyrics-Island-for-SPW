// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.platform.SystemTheme
import java.awt.Color

/** Windows light/dark colours used by the plugin's existing popup menus. */
internal data class PopupMenuPalette(
    val surface: Color,
    val outline: Color,
    val hover: Color,
    val text: Color,
    val muted: Color,
    val accent: Color
) {
    companion object {
        fun current(): PopupMenuPalette = if (SystemTheme.isLight()) LIGHT else DARK

        private val DARK = PopupMenuPalette(
            Color(0x20, 0x20, 0x20), Color(0x45, 0x45, 0x45), Color(0x35, 0x35, 0x35),
            Color(0xF5, 0xF5, 0xF5), Color(0x9D, 0x9D, 0x9D), Color(0x60, 0xCD, 0xFF)
        )
        private val LIGHT = PopupMenuPalette(
            Color(0xF9, 0xF9, 0xF9), Color(0xD8, 0xD8, 0xD8), Color(0xEB, 0xEB, 0xEB),
            Color(0x1B, 0x1B, 0x1B), Color(0x5D, 0x5D, 0x5D), Color(0x00, 0x67, 0xC0)
        )
    }
}
