// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.core.LyricFontWeight
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.font.TextAttribute
import java.util.Locale

/** Creates plugin UI fonts from bundled Noto Sans SC weights and optional lyric-only custom fonts. */
internal object SystemUiFont {
    private val installedFamilies by lazy {
        GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames
            .associateBy { it.lowercase(Locale.ROOT) }
    }
    private val bundled by lazy {
        LyricFontWeight.entries.associateWith { weight ->
            val resource = fontResources.getValue(weight)
            val stream = checkNotNull(SystemUiFont::class.java.getResourceAsStream(resource)) {
                "内置字体资源缺失：$resource"
            }
            stream.use { Font.createFont(Font.TRUETYPE_FONT, it) }
        }
    }

    fun derive(style: Int, size: Float): Font = bundled.getValue(LyricFontWeight.REGULAR).deriveFont(style, size)

    fun lyric(family: String, weight: LyricFontWeight, size: Float): Font {
        val installed = installedFamilies[family.lowercase(Locale.ROOT)]
            ?: return lyricFallback(weight, size)
        return weighted(Font(installed, Font.PLAIN, size.toInt()), weight.attributeValue, size)
    }

    fun lyricFallback(weight: LyricFontWeight, size: Float): Font = bundled.getValue(weight).deriveFont(size)

    private fun weighted(font: Font, weight: Any, size: Float): Font = font.deriveFont(
        mapOf(TextAttribute.SIZE to size, TextAttribute.WEIGHT to weight)
    )

    private val LyricFontWeight.attributeValue: Float
        get() = when (this) {
            LyricFontWeight.THIN -> TextAttribute.WEIGHT_EXTRA_LIGHT
            LyricFontWeight.LIGHT -> TextAttribute.WEIGHT_LIGHT
            LyricFontWeight.DEMI_LIGHT -> TextAttribute.WEIGHT_DEMILIGHT
            LyricFontWeight.REGULAR -> TextAttribute.WEIGHT_REGULAR
            LyricFontWeight.MEDIUM -> TextAttribute.WEIGHT_MEDIUM
            LyricFontWeight.BOLD -> TextAttribute.WEIGHT_BOLD
            LyricFontWeight.BLACK -> TextAttribute.WEIGHT_ULTRABOLD
        }

    private val fontResources = mapOf(
        LyricFontWeight.THIN to "/fonts/NotoSansSC-Thin.otf",
        LyricFontWeight.LIGHT to "/fonts/NotoSansSC-Light.otf",
        LyricFontWeight.DEMI_LIGHT to "/fonts/NotoSansSC-DemiLight.otf",
        LyricFontWeight.REGULAR to "/fonts/NotoSansSC-Regular.otf",
        LyricFontWeight.MEDIUM to "/fonts/NotoSansSC-Medium.otf",
        LyricFontWeight.BOLD to "/fonts/NotoSansSC-Bold.otf",
        LyricFontWeight.BLACK to "/fonts/NotoSansSC-Black.otf"
    )
}
