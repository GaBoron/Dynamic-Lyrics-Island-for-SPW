// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import java.awt.*
import javax.swing.Icon

/** Matching filled transport symbols, independent of the selected font. */
enum class PlaybackIcon : Icon {
    PREVIOUS, PLAY, PAUSE, NEXT;
    override fun getIconWidth() = 18
    override fun getIconHeight() = 18
    override fun paintIcon(c: Component, graphics: Graphics, x: Int, y: Int) {
        val g = graphics.create() as Graphics2D
        try {
            g.translate(x, y)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = c.foreground
            when (this) {
                PAUSE -> { g.fillRoundRect(4, 3, 4, 12, 1, 1); g.fillRoundRect(11, 3, 4, 12, 1, 1) }
                PLAY -> g.fillPolygon(intArrayOf(5, 5, 15), intArrayOf(3, 15, 9), 3)
                PREVIOUS -> { g.fillRect(3, 3, 2, 12); g.fillPolygon(intArrayOf(15, 15, 5), intArrayOf(3, 15, 9), 3) }
                NEXT -> { g.fillRect(13, 3, 2, 12); g.fillPolygon(intArrayOf(3, 3, 13), intArrayOf(3, 15, 9), 3) }
            }
        } finally { g.dispose() }
    }
}
