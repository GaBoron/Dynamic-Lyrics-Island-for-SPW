// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.core.*
import java.awt.Dimension
import kotlin.math.ceil

/** Measures a variable number of lyric and translation rows as one animated island body. */
class IslandLyricsLayout(snapshot: PlaybackSnapshot, private val settings: IslandSettings) {
    val blocks = ActiveLyrics.select(snapshot, settings.experimentalMultiLine)
        .map { IslandTextBlock(snapshot, settings, it) }
        .ifEmpty { listOf(IslandTextBlock(snapshot, settings)) }
    private val rowGap = if (blocks.size > 1) maxOf(8f, settings.fontSize * .36f) else 0f
    private val contentHeight = blocks.sumOf { it.height.toDouble() }.toFloat() + rowGap * (blocks.size - 1)
    val preferredHeight = maxOf(settings.fontSize + 28, ceil(contentHeight + 28).toInt())

    data class Row(val block: IslandTextBlock, val mainBaseline: Float, val subBaseline: Float)

    fun rows(areaHeight: Float): List<Row> {
        var top = (areaHeight - contentHeight) / 2
        return blocks.map { block ->
            val row = Row(block, top - block.shapedMain.top,
                top + block.shapedMain.height + block.gap - (block.shapedSub?.top ?: 0f))
            top += block.height + rowGap
            row
        }
    }

    fun size(maxWidth: Int, expanded: Boolean): Dimension {
        val motionPad = blocks.maxOf { block ->
            if (settings.karaoke && block.line?.timedWords?.isNotEmpty() == true) settings.fontSize * .32f else 0f
        }
        val needed = ceil(blocks.maxOf { maxOf(it.shapedMain.width + motionPad, it.shapedSub?.width ?: 0f) } +
            IslandTextBlock.INSET * 2).toInt()
        val limit = maxWidth.coerceAtLeast(1)
        val minimum = if (expanded) PlaybackProgress.FIXED_WIDTH + IslandTextBlock.EXPANDED_SIDE_SPACE * 2 else 240
        val natural = maxOf(needed, minimum) + if (expanded) IslandTextBlock.EXPANDED_SIDE_PADDING * 2 else 0
        return Dimension((if (settings.fixedWidth) limit else natural).coerceAtMost(limit),
            preferredHeight + if (expanded) IslandTextBlock.EXPANDED_HEIGHT else 0)
    }
}
