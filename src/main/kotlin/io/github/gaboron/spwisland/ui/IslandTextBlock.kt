// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.core.*
import java.awt.Dimension
import java.awt.Font
import kotlin.math.ceil

/** One layout specification owns text selection, insets, measured width and vertical centering. */
class IslandTextBlock(snapshot: PlaybackSnapshot, settings: IslandSettings) {
    val line = snapshot.line
    val main = line?.text?.takeIf { it.isNotBlank() } ?: when {
        snapshot.status == PlaybackStatus.BUFFERING -> "正在缓冲"
        snapshot.playing -> "···"
        snapshot.track != null -> "已暂停"
        else -> "等待播放"
    }
    val mainFont = Font(settings.fontFamily, Font.PLAIN, settings.fontSize)
    val sub = line?.translation?.takeIf { settings.translation && it.isNotBlank() }
    val subFont = mainFont.deriveFont(settings.fontSize * .7f)
    val shapedMain = LyricTypography.shape(main, mainFont)
    val shapedSub = sub?.let { LyricTypography.shape(it, subFont) }
    val gap = if (sub == null) 0f else 8f
    val height = shapedMain.height + gap + (shapedSub?.height ?: 0f)
    val preferredHeight = maxOf(settings.fontSize + 28, ceil(height + 28).toInt())

    fun size(maxWidth: Int, expanded: Boolean): Dimension {
        val needed = ceil(maxOf(shapedMain.width, shapedSub?.width ?: 0f) + INSET * 2).toInt()
        val limit = maxWidth.coerceAtLeast(1)
        return Dimension(needed.coerceIn(minOf(if (expanded) 340 else 240, limit), limit),
            preferredHeight + if (expanded) EXPANDED_HEIGHT else 0)
    }
    fun mainBaseline(lyricAreaHeight: Float): Float = (lyricAreaHeight - height) / 2 - shapedMain.top
    fun subBaseline(lyricAreaHeight: Float): Float = (lyricAreaHeight - height) / 2 + shapedMain.height + gap - (shapedSub?.top ?: 0f)

    companion object {
        const val INSET = 54f
        const val EXPANDED_HEIGHT = 65
    }
}
