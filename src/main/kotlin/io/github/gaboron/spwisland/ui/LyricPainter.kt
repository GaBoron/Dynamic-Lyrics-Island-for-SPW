// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.core.*
import java.awt.*
import java.awt.geom.Rectangle2D

/** Shapes the whole line together, so combining marks and bidirectional scripts stay intact. */
object LyricPainter {
    fun draw(g: Graphics2D, text: String, words: List<Word>, position: Long, x: Float, baseline: Float,
             available: Float, font: Font, karaoke: Boolean, color: Color = Color.WHITE, motion: Boolean = true) {
        if (text.isEmpty() || available <= 0) return
        val shaped = LyricTypography.shape(text, font)
        val layout = shaped.layout
        val geometry = if (karaoke && words.isNotEmpty()) WordGeometry.ready(shaped, text, words.map { it.text }) else null
        var character = 0
        var activeX = 0.0
        for ((index, word) in words.withIndex()) {
            val end = (character + word.text.length).coerceAtMost(text.length)
            if (end > character && word.progress(position) > 0) {
                val bounds = geometry?.get(index)?.bounds ?: layout.getLogicalHighlightShape(character, end).bounds2D
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
            copy.clip(Rectangle2D.Float(x - font.size2D * .16f, baseline - layout.ascent - font.size2D * .3f,
                available + font.size2D * .32f, layout.ascent + layout.descent + font.size2D * .6f))
            if (karaoke && words.isNotEmpty()) {
                AmllWordPainter.draw(copy, shaped, text, words, position, origin, baseline, font.size2D, motion, color)
                return
            }
            copy.color = color
            layout.draw(copy, origin, baseline)
        } finally { copy.dispose() }
    }
}
