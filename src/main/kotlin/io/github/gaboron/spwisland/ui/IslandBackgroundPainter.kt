// SPDX-License-Identifier: GPL-3.0-only
// Mesh-style album-color motion inspired by AMLL's documented dynamic background; see NOTICE.
package io.github.gaboron.spwisland.ui

import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RadialGradientPaint
import java.awt.Shape
import java.awt.geom.Point2D
import kotlin.math.cos
import kotlin.math.sin

/** Draws the static backing and optional album-color flow without owning animation state. */
object IslandBackgroundPainter {
    fun draw(graphics: Graphics2D, shape: Shape, background: Color, coverRgb: Int?, opacity: Int,
             flowing: Boolean, positionMs: Long, reducedMotion: Boolean) {
        val alpha = opacity.coerceIn(0, 100) * 255 / 100
        graphics.color = Color(background.red, background.green, background.blue, alpha)
        graphics.fill(shape)
        if (!flowing || shape.bounds2D.width <= 0 || shape.bounds2D.height <= 0) return

        val bounds = shape.bounds2D
        val seed = coverRgb?.let(::Color) ?: Color(132, 216, 188)
        val hsb = Color.RGBtoHSB(seed.red, seed.green, seed.blue, null)
        val saturation = hsb[1].coerceIn(.38f, .72f)
        val first = Color.getHSBColor(hsb[0], saturation, .58f)
        val second = Color.getHSBColor((hsb[0] + .12f) % 1f, saturation.coerceAtMost(.62f), .46f)
        val phase = if (reducedMotion) 0.0 else positionMs / 7_500.0
        val width = bounds.width.toFloat()
        val height = bounds.height.toFloat()
        val radius = (width * .62f).coerceAtLeast(height * 2.2f)

        val copy = graphics.create() as Graphics2D
        try {
            copy.clip(shape)
            // SrcAtop keeps the configured window opacity while the colored lights blend inside it.
            copy.composite = AlphaComposite.SrcAtop
            glow(copy, bounds.x.toFloat() + width * (.25f + .16f * sin(phase).toFloat()),
                bounds.y.toFloat() + height * (.35f + .18f * cos(phase * .73).toFloat()), radius,
                withAlpha(first, 150), shape)
            glow(copy, bounds.x.toFloat() + width * (.76f + .12f * cos(phase * .83).toFloat()),
                bounds.y.toFloat() + height * (.65f + .16f * sin(phase * .61).toFloat()), radius * .82f,
                withAlpha(second, 125), shape)
        } finally { copy.dispose() }
    }

    private fun glow(g: Graphics2D, x: Float, y: Float, radius: Float, color: Color, shape: Shape) {
        g.paint = RadialGradientPaint(Point2D.Float(x, y), radius,
            floatArrayOf(0f, .52f, 1f), arrayOf(color, withAlpha(color, color.alpha / 3), withAlpha(color, 0)))
        g.fill(shape)
    }

    private fun withAlpha(color: Color, alpha: Int) =
        Color(color.red, color.green, color.blue, alpha.coerceIn(0, 255))
}
