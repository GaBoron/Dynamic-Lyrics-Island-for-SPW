// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.core.IslandSettings
import io.github.gaboron.spwisland.core.LyricLine
import io.github.gaboron.spwisland.core.PlaybackSnapshot
import io.github.gaboron.spwisland.core.PlaybackStatus
import io.github.gaboron.spwisland.core.Track
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.swing.JPanel

/** Paints the real island using the same layout and lyric renderer as playback. */
internal class FontPickerPreviewPanel(private val current: IslandSettings) : JPanel() {
    var family = current.fontFamily
    var weight = current.fontWeight
    var fontSize = current.fontSize
    var primary = "Twinkle, twinkle, little star"
    var translation = "一闪一闪亮晶晶"

    private val island = IslandPanel(object : PlaybackActions {
        override fun previous() = Unit
        override fun toggle() = Unit
        override fun next() = Unit
    })

    init {
        isOpaque = true
        background = Color(0x2A, 0x30, 0x3D)
        island.updateSpectrum(floatArrayOf(.35f, .7f, .5f, .8f), 1.0)
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        if (width <= 0 || height <= 0) return
        val line = LyricLine(0, 10000, primary.ifBlank { " " }, translation.takeIf { it.isNotBlank() }, emptyList())
        island.settings = current.copy(fontFamily = family, fontWeight = weight, fontSize = fontSize)
        island.snapshot = PlaybackSnapshot(
            Track("Twinkle, Twinkle, Little Star", "Traditional", ""),
            line, 3000, true, PlaybackStatus.READY, lyrics = listOf(line)
        )
        val bounds = island.desiredSize((width - 24).coerceAtLeast(1))
        if (bounds.width <= 0 || bounds.height <= 0) return
        island.setSize(bounds)
        island.animatedWidth = bounds.width.toDouble()
        island.animatedHeight = bounds.height.toDouble()
        island.doLayout()

        // Transparent pixels retain the real island's antialiased silhouette in SwingPanel.
        val pixels = BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_ARGB_PRE)
        val islandGraphics = pixels.createGraphics()
        try { island.paint(islandGraphics) } finally { islandGraphics.dispose() }
        val g = graphics.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.drawImage(pixels, (width - bounds.width) / 2, (height - bounds.height) / 2, null)
        } finally { g.dispose() }
    }
}
