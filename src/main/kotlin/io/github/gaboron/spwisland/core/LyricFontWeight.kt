// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.core

/** Stable storage values for the lyric-only font weight setting. */
enum class LyricFontWeight(val storageName: String) {
    THIN("100"),
    LIGHT("300"),
    DEMI_LIGHT("350"),
    REGULAR("400"),
    MEDIUM("500"),
    BOLD("700"),
    BLACK("900");

    companion object {
        fun fromStorage(value: String): LyricFontWeight =
            entries.firstOrNull { it.storageName == value } ?: REGULAR
    }
}
