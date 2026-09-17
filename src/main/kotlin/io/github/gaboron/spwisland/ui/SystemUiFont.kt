// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import com.sun.jna.Platform
import java.awt.Font
import javax.swing.UIManager

/** Keeps the Windows visual default while using the desktop UI font everywhere else. */
internal object SystemUiFont {
    fun derive(style: Int, size: Float): Font {
        val base = if (Platform.isWindows()) Font("Microsoft YaHei UI", style, size.toInt())
            else UIManager.getFont("Label.font") ?: Font(Font.DIALOG, style, size.toInt())
        return base.deriveFont(style, size)
    }
}
