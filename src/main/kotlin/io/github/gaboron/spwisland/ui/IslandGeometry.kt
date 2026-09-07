// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import java.awt.Rectangle
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.awt.Shape

object IslandGeometry {
    fun contentInset(width: Int, height: Int, notch: Boolean, top: Int, bottom: Int,
                     cornerRoundness: Int = 100): Float {
        val shape = silhouette(width, height, notch, cornerRoundness)
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
    fun top(screen: Rectangle, notch: Boolean, dragged: Int?, saved: Int?): Int {
        val proposed = dragged ?: saved ?: screen.y
        return if (notch || kotlin.math.abs(proposed.toLong() - screen.y) <= 12) screen.y else proposed
    }
    fun clamp(screen: Rectangle, centerX: Int, top: Int, width: Int, height: Int): Rectangle {
        val w = width.coerceIn(1, screen.width.coerceAtLeast(1))
        val h = height.coerceIn(1, screen.height.coerceAtLeast(1))
        val x = (centerX.toLong() - w / 2).coerceIn(screen.x.toLong(), screen.x.toLong() + screen.width - w)
        val y = top.toLong().coerceIn(screen.y.toLong(), screen.y.toLong() + screen.height - h)
        return Rectangle(x.toInt(), y.toInt(), w, h)
    }
    fun silhouette(width: Int, height: Int, notch: Boolean, cornerRoundness: Int = 100): Shape {
        val w = width.toDouble(); val h = height.toDouble()
        val scale = cornerRoundness.coerceIn(0, 100) / 100.0
        val pillRadius = minOf(w, h) / 2 * scale
        if (!notch) return RoundRectangle2D.Double(0.0, 0.0, w - 1, h - 1,
            pillRadius * 2, pillRadius * 2)
        val defaultNotchRadius = (h * 0.45).coerceAtMost(32.0)
        val notchRadiusLimit = minOf((w - 20).coerceAtLeast(0.0) / 2, (h - 14).coerceAtLeast(0.0))
        val r = minOf(defaultNotchRadius, notchRadiusLimit) * scale
        return Path2D.Double().apply {
            moveTo(0.0, 0.0); lineTo(w, 0.0)
            curveTo(w - 10, 0.0, w - 10, 8.0, w - 10, 14.0)
            lineTo(w - 10, h - r)
            quadTo(w - 10, h, w - r - 10, h)
            lineTo(r + 10, h); quadTo(10.0, h, 10.0, h - r)
            lineTo(10.0, 14.0); curveTo(10.0, 8.0, 10.0, 0.0, 0.0, 0.0); closePath()
        }
    }
}
