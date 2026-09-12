// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import java.awt.*
import java.awt.geom.AffineTransform
import java.awt.geom.Area
import io.github.gaboron.spwisland.core.HorizontalAnchor
import io.github.gaboron.spwisland.core.IslandAnchor
import io.github.gaboron.spwisland.core.VerticalAnchor
import javax.swing.JPanel

/** Stable translucent backing surface; animation changes the child silhouette, not the native bitmap. */
class IslandSurface(val island: IslandPanel) : JPanel(null) {
    var revealScale = 1.0
    var revealAnchor = IslandAnchor.TOP_CENTER
    init { isOpaque = false; isDoubleBuffered = false; island.isDoubleBuffered = false; add(island) }
    override fun paintChildren(graphics: Graphics) {
        if (revealScale <= 0.0) return
        val g = graphics.create() as Graphics2D
        try {
            val pivotX = island.x + when (revealAnchor.horizontal) {
                HorizontalAnchor.LEFT -> 0.0
                HorizontalAnchor.CENTER -> island.width / 2.0
                HorizontalAnchor.RIGHT -> island.width.toDouble()
            }
            val anchoredPivotY = island.y + when (revealAnchor.vertical) {
                VerticalAnchor.TOP -> 0.0
                VerticalAnchor.CENTER -> island.height / 2.0
                VerticalAnchor.BOTTOM -> island.height.toDouble()
            }
            g.translate(pivotX, anchoredPivotY)
            g.scale(revealScale, revealScale)
            g.translate(-pivotX, -anchoredPivotY)
            super.paintChildren(g)
        } finally { g.dispose() }
    }
    override fun paintComponent(graphics: Graphics) {
        val g = graphics.create() as Graphics2D
        try {
            g.composite = AlphaComposite.Clear
            g.fillRect(0, 0, width, height)
        } finally { g.dispose() }
    }
    fun inputRegion(): Shape {
        val shape = IslandGeometry.silhouette(island.width, island.height, island.settings.notch,
            island.settings.cornerRoundness, island.anchor)
        // The region includes the antialiased outer edge instead of clipping it to a hard pixel boundary.
        val padded = Area(shape).apply { add(Area(BasicStroke(2f).createStrokedShape(shape))) }
        return AffineTransform.getTranslateInstance(island.x.toDouble(), island.y.toDouble()).createTransformedShape(padded)
    }
}
