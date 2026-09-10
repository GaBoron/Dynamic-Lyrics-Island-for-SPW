// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.core.*
import java.awt.Dimension
import java.awt.Font
import kotlin.math.ceil

/** One layout specification owns text selection, insets, measured width and vertical centering. */
class IslandTextBlock(snapshot: PlaybackSnapshot, private val settings: IslandSettings,
                      selectedLine: LyricLine? = snapshot.line) {
    val line = selectedLine
    val main = line?.text?.takeIf { it.isNotBlank() } ?: snapshot.track?.title?.takeIf { it.isNotBlank() } ?: "SPW"
    val mainFont = Font(settings.fontFamily, Font.PLAIN, settings.fontSize)
    val sub = line?.translation?.takeIf { settings.translation && it.isNotBlank() }
    val subFont = mainFont.deriveFont(settings.fontSize * .7f)
    val shapedMain = LyricTypography.shape(main, mainFont)
    val shapedSub = sub?.let { LyricTypography.shape(it, subFont) }
    val gap = if (sub == null) 0f else 8f
    val height = shapedMain.height + gap + (shapedSub?.height ?: 0f)
    val preferredHeight = maxOf(settings.fontSize + 28, ceil(height + 28).toInt())

    fun size(maxWidth: Int, expanded: Boolean): Dimension {
        val motionPad = if (settings.karaoke && line?.timedWords?.isNotEmpty() == true) settings.fontSize * .32f else 0f
        val needed = ceil(maxOf(shapedMain.width + motionPad, shapedSub?.width ?: 0f) + INSET * 2).toInt()
        val limit = maxWidth.coerceAtLeast(1)
        val minimum = if (expanded) PlaybackProgress.FIXED_WIDTH + EXPANDED_SIDE_SPACE * 2 else 240
        val natural = maxOf(needed, minimum) + if (expanded) EXPANDED_SIDE_PADDING * 2 else 0
        val width = if (settings.fixedWidth) limit else natural
        return Dimension(width.coerceAtMost(limit),
            preferredHeight + if (expanded) EXPANDED_HEIGHT else 0)
    }
    fun mainBaseline(lyricAreaHeight: Float): Float = (lyricAreaHeight - height) / 2 - shapedMain.top
    fun subBaseline(lyricAreaHeight: Float): Float = (lyricAreaHeight - height) / 2 + shapedMain.height + gap - (shapedSub?.top ?: 0f)

    companion object {
        const val INSET = 54f
        const val EXPANDED_HEIGHT = 98
        const val EXPANDED_SIDE_SPACE = 32
        const val EXPANDED_SIDE_PADDING = 12
    }
}
