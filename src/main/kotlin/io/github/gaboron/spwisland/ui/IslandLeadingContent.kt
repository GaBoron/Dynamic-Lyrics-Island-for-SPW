// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.core.LeadingContent
import io.github.gaboron.spwisland.core.CoverArtwork
import java.awt.*
import java.awt.geom.AffineTransform
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage

/** Paints the user-selected leading visual without coupling window layout to artwork decoding. */
object IslandLeadingContent {
    private var cachedArtwork: CoverArtwork? = null
    private var cachedImage: BufferedImage? = null

    fun draw(g: Graphics2D, mode: LeadingContent, cover: CoverArtwork?, bands: FloatArray,
             centerX: Float, centerY: Float, accent: Color) {
        when (mode) {
            LeadingContent.SPECTRUM -> spectrum(g, bands, centerX, centerY, accent)
            LeadingContent.COVER -> cover(g, cover, centerX, centerY, accent)
        }
    }

    private fun spectrum(g: Graphics2D, bands: FloatArray, centerX: Float, centerY: Float, accent: Color) {
        g.color = accent
        for (i in 0 until 4) {
            val barHeight = 2f + bands.getOrElse(i) { 0f }.coerceIn(0f, 1f) * 24f
            g.fill(RoundRectangle2D.Float(
                centerX - 11f + i * 6f, centerY - barHeight / 2f, 3f, barHeight, 3f, 3f
            ))
        }
    }

    private fun cover(g: Graphics2D, artwork: CoverArtwork?, centerX: Float, centerY: Float, accent: Color) {
        val size = 32f
        val x = centerX - size / 2f
        val y = centerY - size / 2f
        val shape = RoundRectangle2D.Float(x, y, size, size, 9f, 9f)
        if (artwork == null) {
            g.color = Color(accent.red, accent.green, accent.blue, 55)
            g.fill(shape)
            g.color = Color(accent.red, accent.green, accent.blue, 180)
            g.draw(java.awt.geom.Ellipse2D.Float(centerX - 7f, centerY - 7f, 14f, 14f))
            g.fill(java.awt.geom.Ellipse2D.Float(centerX - 2f, centerY - 2f, 4f, 4f))
            return
        }
        val image = image(artwork)
        val copy = g.create() as Graphics2D
        try {
            copy.clip(shape)
            copy.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            val source = minOf(image.width, image.height)
            val sx = (image.width - source) / 2f
            val sy = (image.height - source) / 2f
            val scale = size / source
            val transform = AffineTransform.getTranslateInstance(
                (x - sx * scale).toDouble(), (y - sy * scale).toDouble()
            ).apply { scale(scale.toDouble(), scale.toDouble()) }
            copy.drawImage(image, transform, null)
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
