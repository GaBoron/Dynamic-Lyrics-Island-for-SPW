// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.core.HorizontalAnchor
import io.github.gaboron.spwisland.core.IslandAnchor
import io.github.gaboron.spwisland.core.VerticalAnchor
import java.awt.GraphicsConfiguration
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit

/** Pure nine-grid anchor policy for island windows on a desktop work area. */
object IslandPlacement {
    fun workArea(configuration: GraphicsConfiguration): Rectangle {
        val screen = configuration.bounds
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration)
        return Rectangle(screen.x + insets.left, screen.y + insets.top,
            (screen.width - insets.left - insets.right).coerceAtLeast(1),
            (screen.height - insets.top - insets.bottom).coerceAtLeast(1))
    }

    /** Selects one of nine anchors from the island centre's screen third. */
    fun automaticAnchor(screen: Rectangle, bounds: Rectangle): IslandAnchor {
        val centerX = bounds.x.toLong() + bounds.width / 2
        val centerY = bounds.y.toLong() + bounds.height / 2
        val horizontal = when (third(centerX - screen.x, screen.width)) {
            0 -> HorizontalAnchor.LEFT
            2 -> HorizontalAnchor.RIGHT
            else -> HorizontalAnchor.CENTER
        }
        val vertical = when (third(centerY - screen.y, screen.height)) {
            0 -> VerticalAnchor.TOP
            2 -> VerticalAnchor.BOTTOM
            else -> VerticalAnchor.CENTER
        }
        return IslandAnchor(horizontal, vertical)
    }

    /** Converts current bounds to the fixed point used by its selected anchor. */
    fun anchorPoint(bounds: Rectangle, anchor: IslandAnchor): Point {
        val x = bounds.x + when (anchor.horizontal) {
            HorizontalAnchor.LEFT -> 0
            HorizontalAnchor.CENTER -> bounds.width / 2
            HorizontalAnchor.RIGHT -> bounds.width
        }
        val y = bounds.y + when (anchor.vertical) {
            VerticalAnchor.TOP -> 0
            VerticalAnchor.CENTER -> bounds.height / 2
            VerticalAnchor.BOTTOM -> bounds.height
        }
        return Point(x, y)
    }

    /** Places a resized island around a stable nine-grid anchor point. */
    fun bounds(screen: Rectangle, point: Point, width: Int, height: Int, anchor: IslandAnchor): Rectangle {
        val w = width.coerceIn(1, screen.width.coerceAtLeast(1))
        val h = height.coerceIn(1, screen.height.coerceAtLeast(1))
        val proposedX = point.x.toLong() - when (anchor.horizontal) {
            HorizontalAnchor.LEFT -> 0
            HorizontalAnchor.CENTER -> w / 2
            HorizontalAnchor.RIGHT -> w
        }
        val proposedY = point.y.toLong() - when (anchor.vertical) {
            VerticalAnchor.TOP -> 0
            VerticalAnchor.CENTER -> h / 2
            VerticalAnchor.BOTTOM -> h
        }
        val x = proposedX.coerceIn(screen.x.toLong(), screen.maxX.toLong() - w)
        val y = proposedY.coerceIn(screen.y.toLong(), screen.maxY.toLong() - h)
        return Rectangle(x.toInt(), y.toInt(), w, h)
    }

    /** Keeps the translucent backing window still while it already contains the island. */
    fun stableCanvasBounds(screen: Rectangle, island: Rectangle, preferred: Rectangle,
                           current: Rectangle): Rectangle {
        if (current.width != preferred.width || current.height != preferred.height) return preferred
        val minX = maxOf(screen.x, island.x + island.width - current.width)
        val maxX = minOf(screen.x + screen.width - current.width, island.x)
        val minY = maxOf(screen.y, island.y + island.height - current.height)
        val maxY = minOf(screen.y + screen.height - current.height, island.y)
        if (minX > maxX || minY > maxY) return preferred
        return Rectangle(current.x.coerceIn(minX, maxX), current.y.coerceIn(minY, maxY),
            current.width, current.height)
    }

    private fun third(offset: Long, length: Int): Int =
        ((offset.coerceIn(0, length.toLong()) * 3) / length.coerceAtLeast(1)).toInt().coerceAtMost(2)
}
