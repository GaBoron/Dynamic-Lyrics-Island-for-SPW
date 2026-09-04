// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.core.*
import java.awt.*
import java.awt.geom.Area
import java.awt.geom.Rectangle2D

/** Shapes the whole line together, so combining marks and bidirectional scripts stay intact. */
object LyricPainter {
    fun draw(g: Graphics2D, text: String, words: List<Word>, position: Long, x: Float, baseline: Float,
             available: Float, font: Font, karaoke: Boolean, color: Color = Color.WHITE) {
        if (text.isEmpty() || available <= 0) return
        val shaped = LyricTypography.shape(text, font)
        val layout = shaped.layout
        var character = 0
        var activeX = 0.0
        for (word in words) {
            val end = (character + word.text.length).coerceAtMost(text.length)
            if (end > character && word.progress(position) > 0) {
                val bounds = layout.getLogicalHighlightShape(character, end).bounds2D
                activeX = bounds.x + bounds.width * word.progress(position)
            }
            character = end
        }
        // Keep the sung word in view; untimed long lines make one smooth pass over their lifetime.
        val overflow = (shaped.width - available).coerceAtLeast(0f)
        val scroll = if (words.isNotEmpty()) (activeX - available * 0.65).coerceIn(0.0, overflow.toDouble()).toFloat()
            else if (overflow > 0) ((position.coerceAtLeast(0) / 45.0).coerceAtMost(overflow.toDouble())).toFloat() else 0f
        val origin = (if (overflow == 0f) x + (available - shaped.width) / 2 else x - scroll) - shaped.left
        val copy = g.create() as Graphics2D
        try {
            copy.clip(Rectangle2D.Float(x, baseline - layout.ascent - 3, available, layout.ascent + layout.descent + 6))
            copy.color = if (karaoke && words.isNotEmpty()) Color(126, 129, 138) else color
            layout.draw(copy, origin, baseline)
            if (karaoke && words.isNotEmpty()) {
                character = 0
                for (word in words) {
                    val end = (character + word.text.length).coerceAtMost(text.length)
                    val progress = word.progress(position)
                    if (end > character && progress > 0) {
                        val area = Area(layout.getLogicalHighlightShape(character, end))
                        val bounds = area.bounds2D
                        val left = if (layout.isLeftToRight) bounds.x else bounds.maxX - bounds.width * progress
                        area.intersect(Area(Rectangle2D.Double(left, bounds.y, bounds.width * progress, bounds.height)))
                        val highlight = copy.create() as Graphics2D
                        try {
                            highlight.translate(origin.toDouble(), baseline.toDouble())
                            highlight.clip(area)
                            highlight.color = color
                            layout.draw(highlight, 0f, 0f)
                        } finally { highlight.dispose() }
                    }
                    character = end
                }
            }
        } finally { copy.dispose() }
    }
}
