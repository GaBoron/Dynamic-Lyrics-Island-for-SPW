// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import kotlin.math.ceil

/** Keeps the leading visual, lyrics and trailing status on one shared inset model. */
data class IslandContentLayout private constructor(
    val leadingSize: Float,
    val leadingCenterX: Float,
    val textInset: Float,
    val infoInset: Float,
    val minimumLyricHeight: Int
) {
    fun statusCenterX(width: Float): Float = width - leadingCenterX

    companion object {
        private const val LEADING_TEXT_GAP = 12f
        private const val VERTICAL_PADDING = 10f
        private const val INFO_EDGE_OFFSET = 30f

        fun minimumWidth(expanded: Boolean): Int = if (expanded) {
            PlaybackProgress.FIXED_WIDTH
        } else 240

        fun from(mainLineHeight: Float, showSides: Boolean = true): IslandContentLayout {
            val leadingSize = mainLineHeight.coerceAtLeast(1f)
            val minimumHeight = leadingSize + VERTICAL_PADDING * 2
            val textInset = if (showSides) leadingSize + LEADING_TEXT_GAP else 0f
            return IslandContentLayout(
                leadingSize = leadingSize,
                leadingCenterX = leadingSize / 2f,
                textInset = textInset,
                infoInset = INFO_EDGE_OFFSET,
                minimumLyricHeight = ceil(minimumHeight).toInt()
            )
        }
    }
}
