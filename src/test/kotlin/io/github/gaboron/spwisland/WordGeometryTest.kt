// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland

import io.github.gaboron.spwisland.ui.*
import org.junit.Assert.*
import org.junit.Test
import java.awt.Font
import java.awt.image.BufferedImage
import io.github.gaboron.spwisland.core.Word
import javax.swing.SwingUtilities

class WordGeometryTest {
    @Test fun pendingGeometryKeepsUnsungWordsDimAndSungWordsWhite() {
        val shaped = LyricTypography.shape("MM", Font("Dialog", Font.PLAIN, 40))
        val words = listOf(Word(0, 1000, "M"), Word(1000, 2000, "M"))
        val image = BufferedImage(120, 70, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try { AmllWordPainter.drawTimed(g, shaped, words, 1000, 10f, 50f, 40f) }
        finally { g.dispose() }
        val split = 10 + shaped.layout.getLogicalHighlightShape(0, 1).bounds2D.maxX.toInt()
        fun opaqueColors(from: Int, to: Int) = (from until to).flatMap { x ->
            (0 until image.height).map { y -> image.getRGB(x, y) }
        }.filter { (it ushr 24) == 255 }.map { it and 0xffffff }.toSet()
        assertTrue(opaqueColors(10, split).contains(0xffffff))
        val unsung = opaqueColors(split + 1, 100)
        assertTrue(unsung.contains(0x7e818a))
        assertFalse(unsung.contains(0xffffff))
    }

    @Test fun roundedSurfaceRetainsAlphaCoverageAndClearsPreviousPixels() {
        SwingUtilities.invokeAndWait {
            val panel = samplePanel()
            panel.settings = panel.settings.copy(opacity = 100, notch = false)
            for (scale in listOf(1.0, 1.5, 2.0)) {
                panel.setSize(480, 70)
                val image = BufferedImage((480 * scale).toInt(), (70 * scale).toInt(), BufferedImage.TYPE_INT_ARGB)
                val g = image.createGraphics()
                try {
                    g.color = java.awt.Color.WHITE; g.fillRect(0, 0, image.width, image.height)
                    g.scale(scale, scale); panel.paint(g)
                } finally { g.dispose() }
                assertEquals(0, image.getRGB(0, 0) ushr 24)
                val cornerAlphas = (0 until (30 * scale).toInt()).flatMap { x ->
                    (0 until (15 * scale).toInt()).map { y -> image.getRGB(x, y) ushr 24 }
                }
                assertTrue(cornerAlphas.any { it in 1..254 })
            }
        }
    }
    @Test fun preparedClustersPreserveCombiningCharactersAndWordBounds() {
        val text = "e\u0301光芒"
        val shaped = LyricTypography.shape(text, Font("Dialog", Font.PLAIN, 32))
        val cells = WordGeometry.prepare(shaped, text, listOf("e\u0301", "光芒"))
        assertEquals(1, cells[0].clusters.size)
        assertEquals(2, cells[1].clusters.size)
        assertEquals(shaped.layout.getLogicalHighlightShape(2, 4).bounds2D, cells[1].bounds)
        assertTrue(cells.all { cell -> cell.clusters.all { !it.shape.bounds2D.isEmpty } })
    }
    @Test fun capsuleCornersStayOutsideAcrossLargeWidthChanges() {
        for (width in 240..1000 step 19) {
            val shape = IslandGeometry.silhouette(width, 70, false)
            assertFalse(shape.contains(1.0, 1.0))
            assertFalse(shape.contains(width - 2.0, 1.0))
            assertTrue(shape.contains(width / 2.0, 35.0))
        }
    }
}
