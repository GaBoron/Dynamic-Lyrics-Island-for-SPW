// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.core.IslandSettings
import java.awt.Color

data class IslandPalette(val lyric: Color, val background: Color, val spectrum: Color) {
    companion object {
        fun from(settings: IslandSettings, coverRgb: Int?): IslandPalette {
            val cover = coverRgb?.let { Color(it) }
            val hsb = cover?.let { Color.RGBtoHSB(it.red, it.green, it.blue, null) }
            val bright = hsb?.let { Color.getHSBColor(it[0], it[1].coerceAtMost(.55f), 1f) }
            val dark = hsb?.let { Color.getHSBColor(it[0], it[1].coerceAtMost(.65f), .16f) }
            return IslandPalette(
                if (settings.lyricCoverColor) bright ?: Color.WHITE else Color.WHITE,
                if (settings.backgroundCoverColor) dark ?: Color(7, 8, 12) else Color(7, 8, 12),
                if (settings.spectrumCoverColor) bright ?: Color(132, 216, 188) else Color(132, 216, 188))
        }
    }
}
