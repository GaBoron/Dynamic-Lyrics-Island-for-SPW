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
    private val contentLayout = IslandContentLayout.from(blocks.first().mainLineHeight, 0f)
    val preferredHeight = maxOf(settings.fontSize + 28, ceil(contentHeight + 28).toInt(),
        contentLayout.minimumLyricHeight)

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
            if (settings.karaoke && block.timedWords.isNotEmpty()) settings.fontSize * .32f else 0f
        }
        val horizontal = IslandContentLayout.from(blocks.first().mainLineHeight, if (expanded) 1f else 0f)
        val needed = ceil(blocks.maxOf { maxOf(it.shapedMain.width + motionPad, it.shapedSub?.width ?: 0f) } +
            horizontal.textInset * 2).toInt()
        val limit = maxWidth.coerceAtLeast(1)
        val natural = maxOf(needed, IslandContentLayout.minimumWidth(expanded))
        return Dimension((if (settings.fixedWidth) limit else natural).coerceAtMost(limit),
            preferredHeight + if (expanded) IslandTextBlock.EXPANDED_HEIGHT else 0)
    }
}
