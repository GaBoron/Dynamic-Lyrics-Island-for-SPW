// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.core.IslandAnchor
import io.github.gaboron.spwisland.core.VerticalAnchor
import java.awt.geom.AffineTransform
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.awt.Shape

object IslandGeometry {
    fun contentInset(width: Int, height: Int, notch: Boolean, top: Int, bottom: Int,
                     cornerRoundness: Int = 60, anchor: IslandAnchor = IslandAnchor.TOP_CENTER): Float {
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
    fun silhouette(width: Int, height: Int, notch: Boolean, cornerRoundness: Int = 60,
                   anchor: IslandAnchor = IslandAnchor.TOP_CENTER): Shape {
        val w = width.toDouble(); val h = height.toDouble()
        val scale = cornerRoundness.coerceIn(0, 100) / 100.0
        val pillRadius = minOf(w, h) / 2 * scale
        val pill = RoundRectangle2D.Double(0.0, 0.0, w - 1, h - 1, pillRadius * 2, pillRadius * 2)
        if (!notch || anchor.vertical == VerticalAnchor.CENTER) return pill
        val defaultNotchRadius = (h * 0.45).coerceAtMost(32.0)
        val notchRadiusLimit = minOf((w - 20).coerceAtLeast(0.0) / 2, (h - 14).coerceAtLeast(0.0))
        val r = minOf(defaultNotchRadius, notchRadiusLimit) * scale
        val topNotch = Path2D.Double().apply {
            moveTo(0.0, 0.0); lineTo(w, 0.0)
            curveTo(w - 10, 0.0, w - 10, 8.0, w - 10, 14.0)
            lineTo(w - 10, h - r)
            quadTo(w - 10, h, w - r - 10, h)
            lineTo(r + 10, h); quadTo(10.0, h, 10.0, h - r)
            lineTo(10.0, 14.0); curveTo(10.0, 8.0, 10.0, 0.0, 0.0, 0.0); closePath()
        }
        return if (anchor.vertical == VerticalAnchor.TOP) topNotch else {
            AffineTransform(1.0, 0.0, 0.0, -1.0, 0.0, h).createTransformedShape(topNotch)
        }
    }
}
