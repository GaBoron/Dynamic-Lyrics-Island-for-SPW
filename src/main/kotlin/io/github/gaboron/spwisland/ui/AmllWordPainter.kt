// SPDX-License-Identifier: GPL-3.0-only
// Links to the AGPL-3.0-only AMLL motion port under GPLv3 section 13; see NOTICE.
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.core.Word
import java.awt.*
import java.text.BreakIterator
import java.util.Locale

/** Transforms shaped graphemes, preserving fallback fonts and whole-line shaping. */
object AmllWordPainter {
    fun draw(g: Graphics2D, shaped: ShapedText, text: String, words: List<Word>, time: Long,
             origin: Float, baseline: Float, fontSize: Float, motion: Boolean) {
        val breaks = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(text) }
        var wordStart = 0
        for ((wordIndex, word) in words.withIndex()) {
            val wordEnd = (wordStart + word.text.length).coerceAtMost(text.length)
            val wordBounds = shaped.layout.getLogicalHighlightShape(wordStart, wordEnd).bounds2D
            val clusters = mutableListOf<Pair<Int, Int>>()
            var start = wordStart
            while (start < wordEnd) {
                val next = breaks.following(start).takeUnless { it == BreakIterator.DONE } ?: wordEnd
                val end = minOf(next, wordEnd); clusters += start to end; start = end
            }
            for ((index, range) in clusters.withIndex()) {
                val bounds = shaped.layout.getLogicalHighlightShape(range.first, range.second).bounds2D
                val area = shaped.glyph(range.first, range.second)
                val pose = if (motion) AmllMotion.word(word, time, index, clusters.size, wordIndex == words.lastIndex) else AmllMotion.Pose()
                val copy = g.create() as Graphics2D
                try {
                    copy.translate(origin.toDouble(), baseline.toDouble())
                    copy.translate(pose.xEm * fontSize + bounds.centerX, pose.yEm * fontSize + bounds.centerY)
                    copy.scale(pose.scale, pose.scale); copy.translate(-bounds.centerX, -bounds.centerY)
                    if (pose.glow > .001) {
                        for (radius in 3 downTo 1) {
                            copy.color = Color(255, 255, 255, (pose.glow * 55 / radius).toInt().coerceIn(0, 255))
                            copy.stroke = BasicStroke(fontSize * .035f * radius, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                            copy.draw(area)
                        }
                    }
                    val progress = word.progress(time)
                    val dim = Color(126, 129, 138)
                    val boundary = if (shaped.layout.isLeftToRight) wordBounds.x + wordBounds.width * progress
                        else wordBounds.maxX - wordBounds.width * progress
                    val feather = minOf(fontSize * .45, wordBounds.width * .6).toFloat().coerceAtLeast(.01f)
                    copy.paint = when {
                        progress <= 0 -> dim
                        progress >= 1 -> Color.WHITE
                        else -> GradientPaint(boundary.toFloat() - feather / 2, 0f,
                            if (shaped.layout.isLeftToRight) Color.WHITE else dim,
                            boundary.toFloat() + feather / 2, 0f, if (shaped.layout.isLeftToRight) dim else Color.WHITE)
                    }
                    copy.fill(area)
                } finally { copy.dispose() }
            }
            wordStart = wordEnd
        }
    }
}
