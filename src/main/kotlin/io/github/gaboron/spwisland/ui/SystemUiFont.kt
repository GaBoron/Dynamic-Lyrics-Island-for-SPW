// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import java.awt.Font
import java.awt.GraphicsEnvironment
import java.util.Locale

/** Creates plugin UI fonts from bundled Noto Sans CJK SC and optional lyric-only custom fonts. */
internal object SystemUiFont {
    private val installedFamilies by lazy {
        GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames
            .associateBy { it.lowercase(Locale.ROOT) }
    }
    private val bundled: Font by lazy {
        val stream = checkNotNull(SystemUiFont::class.java.getResourceAsStream(FONT_RESOURCE)) {
            "内置字体资源缺失：$FONT_RESOURCE"
        }
        stream.use { Font.createFont(Font.TRUETYPE_FONT, it) }
    }

    fun derive(style: Int, size: Float): Font = bundled.deriveFont(style, size)

    fun lyric(family: String, style: Int, size: Float): Font {
        if (family.isBlank()) return derive(style, size)
        val installed = installedFamilies[family.lowercase(Locale.ROOT)] ?: return derive(style, size)
        return Font(installed, style, size.toInt()).deriveFont(style, size)
    }

    private const val FONT_RESOURCE = "/fonts/NotoSansCJKsc-Regular.otf"
}
