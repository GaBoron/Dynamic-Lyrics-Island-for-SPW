// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.core.LeadingContent
import io.github.gaboron.spwisland.core.CoverArtwork
import java.awt.*
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage

/** Paints the user-selected leading visual without coupling window layout to artwork decoding. */
object IslandLeadingContent {
    private var cachedArtwork: CoverArtwork? = null
    private var cachedImage: BufferedImage? = null

    fun draw(g: Graphics2D, mode: LeadingContent, cover: CoverArtwork?, bands: FloatArray,
             centerX: Int, centerY: Int, accent: Color) {
        when (mode) {
            LeadingContent.SPECTRUM -> spectrum(g, bands, centerX, centerY, accent)
            LeadingContent.COVER -> cover(g, cover, centerX, centerY, accent)
        }
    }

    private fun spectrum(g: Graphics2D, bands: FloatArray, centerX: Int, centerY: Int, accent: Color) {
        g.color = accent
        for (i in 0 until 4) {
            val barHeight = 2 + (bands.getOrElse(i) { 0f }.coerceIn(0f, 1f) * 24).toInt()
            g.fillRoundRect(centerX - 11 + i * 6, centerY - barHeight / 2, 3, barHeight, 3, 3)
        }
    }

    private fun cover(g: Graphics2D, artwork: CoverArtwork?, centerX: Int, centerY: Int, accent: Color) {
        val size = 32
        val x = centerX - size / 2
        val y = centerY - size / 2
        val shape = RoundRectangle2D.Float(x.toFloat(), y.toFloat(), size.toFloat(), size.toFloat(), 9f, 9f)
        if (artwork == null) {
            g.color = Color(accent.red, accent.green, accent.blue, 55)
            g.fill(shape)
            g.color = Color(accent.red, accent.green, accent.blue, 180)
            g.drawOval(centerX - 7, centerY - 7, 14, 14)
            g.fillOval(centerX - 2, centerY - 2, 4, 4)
            return
        }
        val image = image(artwork)
        val copy = g.create() as Graphics2D
        try {
            copy.clip(shape)
            copy.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            val source = minOf(image.width, image.height)
            val sx = (image.width - source) / 2
            val sy = (image.height - source) / 2
            copy.drawImage(image, x, y, x + size, y + size, sx, sy, sx + source, sy + source, null)
        } finally { copy.dispose() }
        g.color = Color(255, 255, 255, 35)
        g.draw(shape)
    }

    private fun image(artwork: CoverArtwork): BufferedImage {
        if (cachedArtwork !== artwork) {
            cachedArtwork = artwork
            cachedImage = BufferedImage(artwork.width, artwork.height, BufferedImage.TYPE_INT_ARGB).apply {
                setRGB(0, 0, width, height, artwork.argb, 0, width)
            }
        }
        return cachedImage!!
    }
}
