// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland

import io.github.gaboron.spwisland.core.*
import io.github.gaboron.spwisland.platform.LocalTrackMetadata
import io.github.gaboron.spwisland.ui.*
import org.junit.Assert.*
import org.junit.Test
import java.awt.Color
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.swing.SwingUtilities

class IslandFeaturesTest {
    @Test fun readsRealWaveDurationAndSidecarWithoutChangingAudio() {
        val directory = java.nio.file.Path.of("build/test-metadata")
        java.nio.file.Files.createDirectories(directory)
        val file = directory.resolve("tone.wav").toFile()
        val format = javax.sound.sampled.AudioFormat(8000f, 16, 1, true, false)
        javax.sound.sampled.AudioInputStream(java.io.ByteArrayInputStream(ByteArray(32000)), format, 16000).use {
            javax.sound.sampled.AudioSystem.write(it, javax.sound.sampled.AudioFileFormat.Type.WAVE, file)
        }
        val before = file.readBytes()
        val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().let { it.color = Color.GREEN; it.fillRect(0, 0, 16, 16); it.dispose() }
        javax.imageio.ImageIO.write(image, "png", directory.resolve("cover.png").toFile())
        val result = LocalTrackMetadata.read(file.path)
        assertEquals(2000L, result.durationMs)
        assertTrue(Color(result.coverRgb!!).green > 200)
        assertArrayEquals(before, file.readBytes())
    }
    @Test fun lateMetadataCannotLeakAcrossTrackChangesOrStop() {
        val timeline = PlaybackTimeline()
        val a = Track("A", "", "a.mp3"); val b = Track("B", "", "b.mp3")
        val old = timeline.trackChanged(a)
        timeline.trackChanged(b)
        val current = timeline.trackChanged(a)
        timeline.metadataLoaded(old, TrackMetadata(1000, Color.RED.rgb))
        assertEquals(TrackMetadata(), timeline.snapshot().metadata)
        timeline.metadataLoaded(current, TrackMetadata(5000, Color.BLUE.rgb))
        assertEquals(5000L, timeline.snapshot().metadata.durationMs)
        timeline.stateChanged(PlaybackStatus.IDLE)
        timeline.metadataLoaded(current, TrackMetadata(5000, Color.BLUE.rgb))
        assertEquals(TrackMetadata(), timeline.snapshot().metadata)
    }

    @Test fun independentPaletteTogglesAndMissingCoverFallback() {
        val defaults = IslandPalette.from(IslandSettings(), null)
        val all = IslandSettings(lyricCoverColor = true, backgroundCoverColor = true, spectrumCoverColor = true)
        assertEquals(defaults, IslandPalette.from(all, null))
        val lyric = IslandPalette.from(IslandSettings(lyricCoverColor = true), Color.RED.rgb)
        assertNotEquals(defaults.lyric, lyric.lyric)
        assertEquals(defaults.background, lyric.background); assertEquals(defaults.spectrum, lyric.spectrum)
        val colored = IslandPalette.from(all, Color.RED.rgb)
        assertTrue(colored.background.red > colored.background.green)
        assertEquals(colored.lyric, colored.spectrum)
        assertTrue(colored.lyric.red > 200 && colored.background.red < 60)
        val cover = BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB)
        assertNull(LocalTrackMetadata.dominantColor(cover))
        for (y in 0..9) for (x in 0..9) cover.setRGB(x, y, if (x < 8) Color.BLUE.rgb else Color.RED.rgb)
        val dominant = Color(LocalTrackMetadata.dominantColor(cover)!!)
        assertTrue(dominant.blue > dominant.red)
        assertEquals(TrackMetadata(), LocalTrackMetadata.read("build/missing-track.mp3"))
    }

    @Test fun seekCommitsOnceClampsAndCancelsOnTrackChange() = SwingUtilities.invokeAndWait {
        val seeks = mutableListOf<Long>()
        val progress = PlaybackProgress { seeks += it }.apply { setSize(296, 28) }
        val snap = PlaybackSnapshot(Track("A", "", "a"), null, 1000, true, PlaybackStatus.READY, TrackMetadata(10000))
        progress.update(snap)
        fun mouse(id: Int, x: Int, button: Int = MouseEvent.BUTTON1) {
            progress.dispatchEvent(MouseEvent(progress, id, 0, 0, x, 14, 1, false, button))
        }
        mouse(MouseEvent.MOUSE_PRESSED, 48)
        mouse(MouseEvent.MOUSE_DRAGGED, 148, MouseEvent.NOBUTTON)
        assertTrue(seeks.isEmpty()); assertTrue(progress.dragging)
        progress.update(snap.copy(positionMs = 2000))
        mouse(MouseEvent.MOUSE_RELEASED, 148)
        assertEquals(listOf(5000L), seeks)
        mouse(MouseEvent.MOUSE_PRESSED, 48); mouse(MouseEvent.MOUSE_RELEASED, 999)
        assertEquals(10000L, seeks.last())
        mouse(MouseEvent.MOUSE_PRESSED, 48)
        progress.update(snap.copy(track = Track("B", "", "b")))
        mouse(MouseEvent.MOUSE_RELEASED, 148)
        assertEquals(2, seeks.size)
        progress.update(snap.copy(metadata = TrackMetadata()))
        mouse(MouseEvent.MOUSE_PRESSED, 148); mouse(MouseEvent.MOUSE_RELEASED, 148)
        assertEquals(2, seeks.size); assertFalse(progress.isEnabled)
    }

    @Test fun stableSurfaceClearsOldSilhouettesDuringAlternatingResize() = SwingUtilities.invokeAndWait {
        val panel = samplePanel()
        val surface = IslandSurface(panel).apply { setSize(1200, 330) }
        val image = BufferedImage(1200, 330, BufferedImage.TYPE_INT_ARGB)
        for (notch in listOf(false, true)) for (i in 0..30) {
            panel.settings = panel.settings.copy(notch = notch)
            val width = if (i % 2 == 0) 1100 else 280
            val height = if (i % 3 == 0) 200 else 60
            panel.setBounds((1200 - width) / 2, 0, width, height)
            panel.expanded = false; panel.doLayout()
            image.createGraphics().let { surface.paint(it); it.dispose() }
            assertEquals(0, image.getRGB(80, 250) ushr 24)
            assertEquals(0, image.getRGB(600, height + 2) ushr 24)
            assertEquals(0, image.getRGB(panel.x - 2, height / 2) ushr 24)
            assertTrue(surface.inputRegion().contains(600.0, height / 2.0))
            assertFalse(surface.inputRegion().contains(0.0, 250.0))
        }
    }
}
