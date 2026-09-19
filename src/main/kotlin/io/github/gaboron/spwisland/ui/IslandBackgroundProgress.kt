// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.core.IslandSettings
import io.github.gaboron.spwisland.core.IslandAnchor
import io.github.gaboron.spwisland.core.BackgroundProgressMode
import io.github.gaboron.spwisland.core.PlaybackSnapshot
import io.github.gaboron.spwisland.core.PlaybackStatus
import io.github.gaboron.spwisland.core.performance
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.LinearGradientPaint
import java.awt.Paint
import java.awt.Shape
import java.awt.geom.Line2D
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import kotlin.math.roundToInt

/** Paints whole-track progress inside the island silhouette without owning playback timing. */
internal object IslandBackgroundProgress {
    fun draw(g: Graphics2D, shape: Shape, width: Int, height: Int, anchor: IslandAnchor,
             snapshot: PlaybackSnapshot, settings: IslandSettings, palette: IslandPalette,
             expansion: Double) {
        if (settings.backgroundProgress == BackgroundProgressMode.OFF ||
            !settings.performance.renderBackgroundProgress) return
        val reveal = expansion.coerceIn(0.0, 1.0)
        val visibility = 1.0 - reveal * reveal * (3.0 - 2.0 * reveal)
        if (visibility <= 0.0) return
        val duration = snapshot.metadata.durationMs
        if (snapshot.track == null || duration <= 0 || snapshot.status == PlaybackStatus.IDLE) return
        val progress = (snapshot.positionMs.toDouble() / duration).coerceIn(0.0, 1.0)
        if (progress <= 0.0) return

        when (settings.backgroundProgress) {
            BackgroundProgressMode.FILL ->
                drawFill(g, shape, width, height, progress, visibility, settings, palette)
            BackgroundProgressMode.TOP_LINE ->
                drawTopLine(g, width, height, anchor, progress, visibility, settings, palette)
            BackgroundProgressMode.OFF -> Unit
        }
    }

    private fun drawFill(g: Graphics2D, shape: Shape, width: Int, height: Int, progress: Double,
                         visibility: Double, settings: IslandSettings, palette: IslandPalette) {
        val clip = g.clip
        val composite = g.composite
        try {
            g.clip(Rectangle2D.Double(0.0, 0.0, width * progress, height.toDouble()))
            // Replace the base fill instead of stacking alpha, so completed areas do not become opaquer.
            g.composite = AlphaComposite.Src
            val alpha = settings.opacity * 255 / 100
            g.color = blend(palette.background, progressColor(palette, alpha), visibility, alpha)
            g.fill(shape)
        } finally {
            g.composite = composite
            g.clip = clip
        }
    }

    private fun drawTopLine(g: Graphics2D, width: Int, height: Int, anchor: IslandAnchor,
                            progress: Double, visibility: Double, settings: IslandSettings,
                            palette: IslandPalette) {
        val edgeInset = IslandGeometry.topEdgeInset(width, height, settings.notch,
            settings.cornerRoundness, anchor)
        val start = edgeInset.coerceAtLeast(2.0)
        val end = (width - edgeInset).coerceAtLeast(start)
        val filledEnd = start + (end - start) * progress
        val stroke = g.stroke
        val paint = g.paint
        try {
            g.stroke = BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            val active = palette.lyric
            val track = Color(active.red, active.green, active.blue, (42 * visibility).roundToInt())
            g.paint = fadedEnds(start, end, track)
            g.draw(Line2D.Double(start, 1.25, end, 1.25))
            val visibleActive = Color(active.red, active.green, active.blue, (255 * visibility).roundToInt())
            g.paint = fadedEnds(start, end, visibleActive)
            g.draw(Line2D.Double(start, 1.25, filledEnd, 1.25))
        } finally {
            g.paint = paint
            g.stroke = stroke
        }
    }

    private fun fadedEnds(start: Double, end: Double, color: Color): Paint = LinearGradientPaint(
        Point2D.Double(start, 0.0), Point2D.Double(end, 0.0),
        floatArrayOf(0f, .06f, .94f, 1f),
        arrayOf(withAlpha(color, 0), color, color, withAlpha(color, 0)))

    private fun withAlpha(color: Color, alpha: Int) =
        Color(color.red, color.green, color.blue, alpha.coerceIn(0, 255))

    private fun blend(from: Color, to: Color, amount: Double, alpha: Int): Color {
        fun channel(a: Int, b: Int) = (a + (b - a) * amount).roundToInt().coerceIn(0, 255)
        return Color(channel(from.red, to.red), channel(from.green, to.green),
            channel(from.blue, to.blue), alpha.coerceIn(0, 255))
    }

    private fun progressColor(palette: IslandPalette, alpha: Int): Color {
        val backgroundHsb = Color.RGBtoHSB(
            palette.background.red, palette.background.green, palette.background.blue, null)
        val lyricHsb = Color.RGBtoHSB(palette.lyric.red, palette.lyric.green, palette.lyric.blue, null)
        val brightness = (backgroundHsb[2] + .10f).coerceAtMost(lyricHsb[2] * .35f)
        val rgb = Color.getHSBColor(backgroundHsb[0], backgroundHsb[1] * .82f, brightness)
        return Color(rgb.red, rgb.green, rgb.blue, alpha)
    }
}
