// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.core

/** Numeric lyric weight; named values remain for bundled static font faces. */
data class LyricFontWeight(val value: Int) : java.io.Serializable {
    init { require(value in 100..900) }
    val storageName: String get() = value.toString()

    companion object {
        val THIN = LyricFontWeight(100)
        val LIGHT = LyricFontWeight(300)
        val DEMI_LIGHT = LyricFontWeight(350)
        val REGULAR = LyricFontWeight(400)
        val MEDIUM = LyricFontWeight(500)
        val BOLD = LyricFontWeight(700)
        val BLACK = LyricFontWeight(900)
        val entries = listOf(THIN, LIGHT, DEMI_LIGHT, REGULAR, MEDIUM, BOLD, BLACK)

        fun fromStorage(value: String): LyricFontWeight =
            value.toIntOrNull()?.takeIf { it in 100..900 }?.let(::LyricFontWeight) ?: REGULAR
    }
}
