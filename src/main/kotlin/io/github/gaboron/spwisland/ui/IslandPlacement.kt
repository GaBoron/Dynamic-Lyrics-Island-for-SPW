// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.core.VerticalAnchor
import java.awt.Rectangle
import kotlin.math.abs

/** Pure placement policy for free, top-snapped and bottom-snapped island windows. */
object IslandPlacement {
    private const val SNAP_DISTANCE = 12

    fun snap(screen: Rectangle, proposedTop: Int, collapsedHeight: Int, notch: Boolean): VerticalAnchor {
        if (notch) return VerticalAnchor.TOP
        val topDistance = abs(proposedTop.toLong() - screen.y)
        val bottomDistance = abs(proposedTop.toLong() + collapsedHeight - screen.maxY.toLong())
        return when {
            topDistance <= SNAP_DISTANCE && topDistance <= bottomDistance -> VerticalAnchor.TOP
            bottomDistance <= SNAP_DISTANCE -> VerticalAnchor.BOTTOM
            else -> VerticalAnchor.FREE
        }
    }

    fun bounds(screen: Rectangle, centerX: Int, proposedTop: Int, width: Int, height: Int,
               anchor: VerticalAnchor): Rectangle {
        val w = width.coerceIn(1, screen.width.coerceAtLeast(1))
        val h = height.coerceIn(1, screen.height.coerceAtLeast(1))
        val x = (centerX.toLong() - w / 2).coerceIn(screen.x.toLong(), screen.maxX.toLong() - w)
        val anchoredTop = when (anchor) {
            VerticalAnchor.TOP -> screen.y.toLong()
            VerticalAnchor.BOTTOM -> screen.maxY.toLong() - h
            VerticalAnchor.FREE -> proposedTop.toLong()
        }
        val y = anchoredTop.coerceIn(screen.y.toLong(), screen.maxY.toLong() - h)
        return Rectangle(x.toInt(), y.toInt(), w, h)
    }
}
