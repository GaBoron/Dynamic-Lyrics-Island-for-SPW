// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland

import io.github.gaboron.spwisland.core.*
import io.github.gaboron.spwisland.ui.*
import org.junit.Assert.*
import org.junit.Test
import java.awt.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.SwingUtilities

class TypographyRegressionTest {
    @Test fun defaultPositionAndNearTopDragSnapToScreenEdge() {
        val screen = Rectangle(-1920, -100, 1920, 1080)
        assertEquals(-100, IslandGeometry.top(screen, false, null, null))
        assertEquals(-100, IslandGeometry.top(screen, false, -92, null))
        assertEquals(-100, IslandGeometry.top(screen, true, 300, null))
        assertEquals(40, IslandGeometry.top(screen, false, 40, null))
    }
    @Test fun wordWidthAndVerticalCenterUseTheSameShapedText() {
        for (translation in listOf<String?>(null, "音楽が今を照らす · 음악이 지금을 비춰요")) {
            val snap = samplePanel().snapshot.copy(line = LyricLine(0, 5000,
                "夜空に響くメロディー · 밤하늘의 멜로디", translation, emptyList()))
            val block = IslandTextBlock(snap, IslandSettings(maxWidth = 1200))
            val size = block.size(1200, false)
            assertTrue(size.width - IslandTextBlock.INSET * 2 >= block.shapedMain.width)
            block.shapedSub?.let { assertTrue(size.width - IslandTextBlock.INSET * 2 >= it.width) }
            val inkTop = block.mainBaseline(size.height.toFloat()) + block.shapedMain.top
            val inkBottom = block.shapedSub?.let { block.subBaseline(size.height.toFloat()) + it.bottom }
                ?: (block.mainBaseline(size.height.toFloat()) + block.shapedMain.bottom)
            assertEquals(inkTop, size.height - inkBottom, .01f)
        }
    }
    @Test fun japaneseAndKoreanDoNotRenderAsMissingGlyphs() {
        SwingUtilities.invokeAndWait {
            val font = Font("Microsoft YaHei UI", Font.PLAIN, 32)
            val korean = LyricTypography.shape("음악", font)
            // The configured Chinese font lacks Hangul: resolved layout must differ from its tofu outline.
            val unfallbacked = java.awt.font.TextLayout("음악", font, LyricTypography.context)
            if (font.canDisplayUpTo("음악") >= 0) {
                assertNotEquals(unfallbacked.getOutline(null).bounds2D, korean.layout.getOutline(null).bounds2D)
            }
            val panel = samplePanel()
            panel.settings = IslandSettings(fontSize = 28, maxWidth = 1200)
            panel.snapshot = panel.snapshot.copy(line = LyricLine(0, 5000,
                "夜空に響くメロディー · 밤하늘의 멜로디", "日本語も 한국어도 · 都能显示", emptyList()))
            val size = panel.desiredSize(1200); panel.setSize(size); panel.doLayout()
            val image = BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB)
            image.createGraphics().let { panel.paint(it); it.dispose() }
            File("build/preview").mkdirs()
            ImageIO.write(image, "png", File("build/preview/japanese-korean.png"))
        }
    }
    @Test fun instrumentalGapCannotUseTrackTitleAsLyric() {
        val timeline = PlaybackTimeline { 0 }
        timeline.trackChanged(Track("SONG TITLE MUST NOT APPEAR", "Artist", "song"))
        timeline.stateChanged(PlaybackStatus.READY); timeline.playingChanged(true)
        timeline.lineChanged(LyricLine(0, 1000, "Lyrics", null, emptyList()))
        timeline.positionChanged(1500)
        assertEquals("···", IslandTextBlock(timeline.snapshot(), IslandSettings()).main)
        timeline.lineChanged(null)
        assertEquals("···", IslandTextBlock(timeline.snapshot(), IslandSettings()).main)
    }
    @Test fun leftIconDoesNotInventAudioAmplitude() {
        SwingUtilities.invokeAndWait {
            fun render(time: Long): BufferedImage {
                val panel = samplePanel(); panel.snapshot = panel.snapshot.copy(positionMs = time)
                val size = panel.desiredSize(800); panel.setSize(size); panel.doLayout()
                return BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB).also { image ->
                    image.createGraphics().let { panel.paint(it); it.dispose() }
                }
            }
            val a = render(1000); val b = render(1900)
            for (x in 17..42) for (y in 0 until a.height) assertEquals(a.getRGB(x, y), b.getRGB(x, y))
        }
    }
}
