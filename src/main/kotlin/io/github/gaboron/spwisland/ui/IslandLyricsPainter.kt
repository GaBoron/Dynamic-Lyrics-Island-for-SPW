// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.core.*
import java.awt.*
import java.awt.geom.Rectangle2D

object IslandLyricsPainter {
    fun draw(g: Graphics2D, current: PlaybackSnapshot, previous: PlaybackSnapshot?, settings: IslandSettings,
             width: Int, height: Float, transition: Double, inset: Float = IslandTextBlock.INSET) {
        val progress = if (transition >= 1) 1.0 else AmllMotion.line(transition * .65)
        val outgoingAlpha = (1 - transition * 3).coerceIn(0.0, 1.0).toFloat()
        if (previous != null && outgoingAlpha > 0) {
            block(g, previous, settings, width, height, -settings.fontSize * progress,
                outgoingAlpha, 1 - .04 * progress, inset)
        }
        block(g, current, settings, width, height, settings.fontSize * (1 - progress),
            (transition * 3).coerceIn(0.0, 1.0).toFloat(), .96 + .04 * progress, inset)
    }
    private fun block(g: Graphics2D, snapshot: PlaybackSnapshot, settings: IslandSettings, width: Int,
                      height: Float, offset: Double, alpha: Float, scale: Double, inset: Float) {
        val block = IslandTextBlock(snapshot, settings)
        val line = block.line
        val time = snapshot.positionMs + settings.offsetMs
        val copy = g.create() as Graphics2D
        try {
            copy.clip(Rectangle2D.Float(inset - 8, 2f, width - inset * 2 + 16, height - 4))
            copy.composite = AlphaComposite.SrcOver.derive(alpha)
            copy.translate(width / 2.0, height / 2.0 + offset)
            copy.scale(scale, scale); copy.translate(-width / 2.0, -height / 2.0)
            val available = width - inset * 2
            LyricPainter.draw(copy, block.main, line?.timedWords.orEmpty(),
                if (line?.timedWords?.isNotEmpty() == true) time else (time - (line?.startMs ?: 0)).coerceAtLeast(0),
                inset, block.mainBaseline(height), available, block.mainFont, settings.karaoke,
                color = IslandPalette.from(settings, snapshot.metadata.coverRgb).lyric, motion = !settings.reducedMotion)
            block.sub?.let { LyricPainter.draw(copy, it, emptyList(), (time - (line?.startMs ?: 0)).coerceAtLeast(0),
                inset, block.subBaseline(height), available, block.subFont, false, Color(177, 182, 195)) }
        } finally { copy.dispose() }
    }
}
