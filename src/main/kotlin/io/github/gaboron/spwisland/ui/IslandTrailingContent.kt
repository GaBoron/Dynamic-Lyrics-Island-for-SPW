// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.core.PlaybackSnapshot
import io.github.gaboron.spwisland.core.SideContent
import java.awt.Color
import java.awt.Graphics2D
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D
import kotlin.math.sin

/** Paints the configured trailing spectrum or the legacy playback status. */
object IslandTrailingContent {
    fun draw(g: Graphics2D, mode: SideContent, snapshot: PlaybackSnapshot, bands: FloatArray,
             centerX: Float, centerY: Float, size: Float, accent: Color, animate: Boolean) {
        when (mode) {
            SideContent.COVER_SPECTRUM ->
                IslandLeadingContent.drawSpectrum(g, bands, centerX, centerY, size, accent)
            SideContent.SPECTRUM, SideContent.COVER -> drawStatus(g, snapshot, centerX, centerY, animate)
            SideContent.NONE -> Unit
        }
    }

    private fun drawStatus(g: Graphics2D, snapshot: PlaybackSnapshot, centerX: Float, centerY: Float,
                           animate: Boolean) {
        if (snapshot.line == null && snapshot.lyrics.isEmpty() && snapshot.playing) {
            for (i in 0..2) {
                val alpha = if (animate) (150 + 90 * sin(snapshot.positionMs / 350.0 - i)).toInt() else 150
                g.color = Color(190, 204, 221, alpha)
                g.fill(Ellipse2D.Float(centerX - 8f + i * 7f, centerY - 2f, 4f, 4f))
            }
        } else {
            g.color = Color(115, 131, 149)
            if (snapshot.playing) {
                g.fill(Ellipse2D.Float(centerX - 3f, centerY - 3f, 6f, 6f))
            } else {
                g.fill(RoundRectangle2D.Float(centerX - 6f, centerY - 5f, 3f, 10f, 2f, 2f))
                g.fill(RoundRectangle2D.Float(centerX, centerY - 5f, 3f, 10f, 2f, 2f))
            }
        }
    }
}
