// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.core.SideContent
import io.github.gaboron.spwisland.core.CoverArtwork
import java.awt.*
import java.awt.geom.AffineTransform
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage

/** Paints the user-selected leading visual without coupling window layout to artwork decoding. */
object IslandLeadingContent {
    private var cachedArtwork: CoverArtwork? = null
    private var cachedImage: BufferedImage? = null

    fun draw(g: Graphics2D, mode: SideContent, cover: CoverArtwork?, bands: FloatArray,
             centerX: Float, centerY: Float, size: Float, accent: Color) {
        when (mode) {
            SideContent.COVER_SPECTRUM, SideContent.COVER -> cover(g, cover, centerX, centerY, size, accent)
            SideContent.SPECTRUM -> drawSpectrum(g, bands, centerX, centerY, size, accent)
            SideContent.NONE -> Unit
        }
    }

    internal fun drawSpectrum(g: Graphics2D, bands: FloatArray, centerX: Float, centerY: Float,
                              size: Float, accent: Color) {
        val scale = size / BASE_SIZE
        val barWidth = 3f * scale
        val corner = 3f * scale
        g.color = accent
        for (i in 0 until 4) {
            val barHeight = 2f * scale + bands.getOrElse(i) { 0f }.coerceIn(0f, 1f) * (size - 2f * scale)
            g.fill(RoundRectangle2D.Float(
                centerX - 11f * scale + i * 6f * scale, centerY - barHeight / 2f,
                barWidth, barHeight, corner, corner
            ))
        }
    }

    private fun cover(g: Graphics2D, artwork: CoverArtwork?, centerX: Float, centerY: Float,
                      size: Float, accent: Color) {
        val scale = size / BASE_SIZE
        val x = centerX - size / 2f
        val y = centerY - size / 2f
        val shape = RoundRectangle2D.Float(x, y, size, size, 9f * scale, 9f * scale)
        if (artwork == null) {
            g.color = Color(accent.red, accent.green, accent.blue, 55)
            g.fill(shape)
            g.color = Color(accent.red, accent.green, accent.blue, 180)
            g.draw(java.awt.geom.Ellipse2D.Float(
                centerX - 7f * scale, centerY - 7f * scale, 14f * scale, 14f * scale
            ))
            g.fill(java.awt.geom.Ellipse2D.Float(
                centerX - 2f * scale, centerY - 2f * scale, 4f * scale, 4f * scale
            ))
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

    private const val BASE_SIZE = 32f
}
