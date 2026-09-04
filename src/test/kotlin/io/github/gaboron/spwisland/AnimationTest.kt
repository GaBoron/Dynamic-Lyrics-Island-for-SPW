// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland

import io.github.gaboron.spwisland.core.*
import io.github.gaboron.spwisland.ui.*
import org.junit.Assert.*
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.SwingUtilities

class AnimationTest {
    @Test fun longWordsEmphasizeAndSettleWhileReducedMotionKeepsKaraoke() {
        val word = Word(1000, 4000, "光芒")
        val peak = AmllMotion.word(word, 2300, 0, 2, true)
        assertTrue(peak.scale > 1.01 && peak.glow > .01 && peak.yEm < 0)
        val end = AmllMotion.word(word, 10000, 0, 2, true)
        assertEquals(1.0, end.scale, .0001); assertEquals(0.0, end.glow, .0001)
        assertEquals(0.0, AmllMotion.line(0.0), .0001)
        assertEquals(1.0, AmllMotion.line(1.0), .001)
        SwingUtilities.invokeAndWait {
            val panel = samplePanel()
            panel.settings = panel.settings.copy(fontSize = 36, maxWidth = 900)
            panel.snapshot = panel.snapshot.copy(line = LyricLine(1000, 4000, "光芒", "Long-note emphasis", listOf(word)))
            val size = panel.desiredSize(900); panel.setSize(size); panel.doLayout()
            val sheet = BufferedImage(size.width, size.height * 4, BufferedImage.TYPE_INT_ARGB)
            for ((i, time) in listOf(1000L, 2000L, 3000L, 5000L).withIndex()) {
                panel.snapshot = panel.snapshot.copy(positionMs = time)
                sheet.createGraphics().let { g -> g.translate(0, i * size.height); panel.paint(g); g.dispose() }
            }
            File("build/preview").mkdirs(); ImageIO.write(sheet, "png", File("build/preview/long-note.png"))
        }
    }
    @Test fun measuredSpectrumChangesBarsAndZeroInputDecaysToSilence() {
        SwingUtilities.invokeAndWait {
            val panel = samplePanel(); panel.setSize(panel.desiredSize(800)); panel.doLayout()
            fun render(): BufferedImage = BufferedImage(panel.width, panel.height, BufferedImage.TYPE_INT_ARGB).also {
                val g = it.createGraphics(); panel.paint(g); g.dispose()
            }
            val silent = render()
            panel.updateSpectrum(floatArrayOf(1f, .7f, .4f, .2f), 1.0)
            val sound = render()
            assertTrue((17..42).any { x -> (0 until sound.height).any { y -> sound.getRGB(x, y) != silent.getRGB(x, y) } })
            panel.updateSpectrum(FloatArray(4), 3.0)
            val quiet = render()
            for (x in 17..42) for (y in 0 until quiet.height) assertEquals(silent.getRGB(x, y), quiet.getRGB(x, y))
        }
    }
}
