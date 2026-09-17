// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import kotlin.math.ceil
import kotlin.math.sqrt

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
        private const val EXPANDED_SIDE_SPACE = 32
        private const val MINIMUM_EDGE_PADDING = 20f
        private const val CURVED_EDGE_CLEARANCE = 10f
        private const val LEADING_TEXT_GAP = 12f
        private const val VERTICAL_PADDING = 10f
        private const val INFO_EDGE_OFFSET = 30f
        private const val EXPANDED_EDGE_PADDING = 12f

        fun minimumWidth(expanded: Boolean): Int = if (expanded) {
            PlaybackProgress.FIXED_WIDTH + EXPANDED_SIDE_SPACE * 2 + (EXPANDED_EDGE_PADDING * 2).toInt()
        } else 240

        fun from(mainLineHeight: Float, expansion: Float, showSides: Boolean = true): IslandContentLayout {
            val leadingSize = mainLineHeight.coerceAtLeast(1f)
            val minimumHeight = leadingSize + VERTICAL_PADDING * 2
            val radius = minimumHeight / 2f
            val curveInset = radius - sqrt((radius * radius - (radius - VERTICAL_PADDING) *
                (radius - VERTICAL_PADDING)).coerceAtLeast(0f))
            val outerPadding = maxOf(MINIMUM_EDGE_PADDING, curveInset + CURVED_EDGE_CLEARANCE) +
                EXPANDED_EDGE_PADDING * expansion.coerceIn(0f, 1f)
            val textInset = if (showSides) outerPadding + leadingSize + LEADING_TEXT_GAP else outerPadding
            return IslandContentLayout(
                leadingSize = leadingSize,
                leadingCenterX = outerPadding + leadingSize / 2f,
                textInset = textInset,
                infoInset = outerPadding + INFO_EDGE_OFFSET,
                minimumLyricHeight = ceil(minimumHeight).toInt()
            )
        }
    }
}
