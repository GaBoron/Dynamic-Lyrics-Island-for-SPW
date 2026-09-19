// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.core.IslandAnchor
import io.github.gaboron.spwisland.core.VerticalAnchor
import java.awt.geom.AffineTransform
import java.awt.geom.Path2D
import java.awt.Shape

object IslandGeometry {
    private data class SilhouetteKey(val width: Int, val height: Int, val notch: Boolean,
                                     val roundness: Int, val anchor: IslandAnchor)
    private var cachedKey: SilhouetteKey? = null
    private var cachedShape: Shape? = null

    fun contentInset(width: Int, height: Int, notch: Boolean, top: Int, bottom: Int,
                     cornerRoundness: Int = 95, anchor: IslandAnchor = IslandAnchor.TOP_CENTER): Float {
        val shape = silhouette(width, height, notch, cornerRoundness, anchor)
        var inset = 0
        for (y in top.coerceAtLeast(1)..bottom.coerceAtMost(height - 2)) {
            var left = 0; var right = width / 2
            while (left < right) {
                val middle = (left + right) / 2
                if (shape.contains(middle.toDouble(), y.toDouble())) right = middle else left = middle + 1
            }
            inset = maxOf(inset, left)
        }
        return inset + 4f
    }

    /** Width reserved outside the element-safe rectangle for each curved side. */
    fun frameInset(height: Double, notch: Boolean, cornerRoundness: Int,
                   anchor: IslandAnchor = IslandAnchor.TOP_CENTER): Double {
        val h = (height - 1.0).coerceAtLeast(0.0)
        val scale = cornerRoundness.coerceIn(0, 100) / 100.0
        val radius = if (notch && anchor.vertical != VerticalAnchor.CENTER) {
            minOf(h * .45, 32.0) * scale + 10.0
        } else h / 2.0 * scale
        return .5 + radius
    }

    @Synchronized
    fun silhouette(width: Int, height: Int, notch: Boolean, cornerRoundness: Int = 95,
                   anchor: IslandAnchor = IslandAnchor.TOP_CENTER): Shape {
        val key = SilhouetteKey(width, height, notch, cornerRoundness.coerceIn(0, 100), anchor)
        if (key == cachedKey) return cachedShape!!
        val w = (width - 1).coerceAtLeast(0).toDouble()
        val h = (height - 1).coerceAtLeast(0).toDouble()
        val scale = cornerRoundness.coerceIn(0, 100) / 100.0
        val pill = ContinuousCornerPath.roundedRectangle(w, h, cornerRoundness)
        if (!notch || anchor.vertical == VerticalAnchor.CENTER) {
            val centered = AffineTransform.getTranslateInstance(.5, .5).createTransformedShape(pill)
            return cache(key, centered)
        }
        val defaultNotchRadius = (h * 0.45).coerceAtMost(32.0)
        val notchRadiusLimit = minOf((w - 20).coerceAtLeast(0.0) / 2, (h - 14).coerceAtLeast(0.0))
        val r = minOf(defaultNotchRadius, notchRadiusLimit) * scale
        val exponent = ContinuousCornerPath.exponent(cornerRoundness)
        val topNotch = Path2D.Double().apply {
            moveTo(0.0, 0.0); lineTo(w, 0.0)
            curveTo(w - 10, 0.0, w - 10, 8.0, w - 10, 14.0)
            lineTo(w - 10, h - r)
            ContinuousCornerPath.appendBottomRight(this, w - 10, h, r, exponent)
            lineTo(r + 10, h)
            ContinuousCornerPath.appendBottomLeft(this, 10.0, h, r, exponent)
            lineTo(10.0, 14.0); curveTo(10.0, 8.0, 10.0, 0.0, 0.0, 0.0); closePath()
        }
        val oriented = if (anchor.vertical == VerticalAnchor.TOP) topNotch else {
            AffineTransform(1.0, 0.0, 0.0, -1.0, 0.0, h).createTransformedShape(topNotch)
        }
        val shape = AffineTransform.getTranslateInstance(.5, 0.0).createTransformedShape(oriented)
        return cache(key, shape)
    }

    fun topEdgeInset(width: Int, height: Int, notch: Boolean, cornerRoundness: Int,
                     anchor: IslandAnchor): Double =
        if (notch && anchor.vertical != VerticalAnchor.CENTER) 10.5
        else minOf(frameInset(height.toDouble(), false, cornerRoundness, anchor), width / 2.0)

    private fun cache(key: SilhouetteKey, shape: Shape): Shape {
        cachedKey = key
        cachedShape = shape
        return shape
    }
}
