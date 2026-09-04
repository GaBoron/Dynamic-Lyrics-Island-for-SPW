// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland

import io.github.gaboron.spwisland.core.*
import io.github.gaboron.spwisland.ui.*
import org.junit.Assert.*
import org.junit.Test
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.SwingUtilities

class PresentationTest {
    @Test fun placementHandlesNegativeMonitorsAndOutOfRangeSavedPositions() {
        val screen = Rectangle(-1920, -200, 1920, 1080)
        val normal = IslandGeometry.clamp(screen, -960, -180, 600, 90)
        assertEquals(Rectangle(-1260, -180, 600, 90), normal)
        val moved = IslandGeometry.clamp(screen, Int.MAX_VALUE, Int.MIN_VALUE, 600, 90)
        assertTrue(screen.contains(moved))
        val huge = IslandGeometry.clamp(screen, 0, 0, 3000, 2000)
        assertEquals(screen, huge)
    }
    @Test fun rendersCjkTranslationAndHasTransparentCorners() {
        SwingUtilities.invokeAndWait {
            val panel = samplePanel()
            val size = panel.desiredSize(800); panel.setSize(size); panel.doLayout()
            val image = BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB)
            image.createGraphics().let { panel.paint(it); it.dispose() }
            assertEquals(0, image.getRGB(0, 0) ushr 24)
            assertTrue(image.getRGB(size.width / 2, size.height / 2) ushr 24 > 200)
            File("build/preview").mkdirs()
            ImageIO.write(image, "png", File("build/preview/island.png"))
            panel.expanded = true; panel.settings = panel.settings.copy(notch = true)
            val expanded = panel.desiredSize(800); panel.setSize(expanded); panel.doLayout()
            assertTrue(expanded.height > size.height)
            val second = BufferedImage(expanded.width, expanded.height, BufferedImage.TYPE_INT_ARGB)
            second.createGraphics().let { panel.paint(it); it.dispose() }
            ImageIO.write(second, "png", File("build/preview/island-expanded.png"))
        }
    }
    @Test fun smallScreenAndLongUnicodeLinesRemainInsideViewport() {
        SwingUtilities.invokeAndWait {
            val panel = samplePanel()
            panel.snapshot = panel.snapshot.copy(line = LyricLine(0, 10000, "你好 🌙 e\u0301 مرحبا ".repeat(30), null, emptyList()))
            panel.expanded = true
            val size = panel.desiredSize(240); assertEquals(240, size.width)
            panel.setSize(size); panel.doLayout()
            val image = BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB)
            image.createGraphics().let { panel.paint(it); it.dispose() }
        }
    }
}

internal fun samplePanel() = IslandPanel(object : PlaybackActions {
    override fun previous() {} ; override fun toggle() {} ; override fun next() {}
}).apply {
    snapshot = PlaybackSnapshot(Track("夜航", "示例歌手", "sample.flac"),
        LyricLine(0, 5000, "让音乐照亮此刻", "Let the music light up this moment",
            listOf(Word(0, 600, "让"), Word(600, 1600, "音乐"), Word(1600, 3000, "照亮"), Word(3000, 5000, "此刻"))),
        2250, true, PlaybackStatus.READY)
}
