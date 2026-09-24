// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.core.LyricFontWeight
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/** Creates plugin UI fonts from bundled Noto Sans SC weights and optional lyric-only custom fonts. */
internal object SystemUiFont {
    private data class InstalledFace(val font: Font, val weight: Int)

    private val installedFonts by lazy {
        GraphicsEnvironment.getLocalGraphicsEnvironment().allFonts.toList()
    }
    private val installedFamilies = ConcurrentHashMap<String, List<InstalledFace>>()
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
        val requested = normalizeName(family)
        if (requested.isEmpty()) return lyricFallback(weight, size)
        val direct = facesFor(requested)
        if (direct.isNotEmpty()) return selectFace(direct, weight, size)
        exactFace(requested)?.let { return it.deriveFont(size) }
        val fallbackFaces = strippedCandidates(requested).firstNotNullOfOrNull { candidate ->
            facesFor(candidate).takeIf { it.isNotEmpty() }
        }
        return fallbackFaces?.let { selectFace(it, weight, size) } ?: lyricFallback(weight, size)
    }

    private fun selectFace(faces: List<InstalledFace>, weight: LyricFontWeight, size: Float): Font {
        val target = weight.storageName.toInt()
        val face = faces.minWith(compareBy<InstalledFace>(
            { abs(it.weight - target) },
            { if (target >= 500) -it.weight else it.weight }
        ))
        return face.font.deriveFont(size)
    }

    private fun facesFor(requested: String): List<InstalledFace> =
        installedFamilies.computeIfAbsent(requested) { installedFacesFor(requested) }

    private fun installedFacesFor(requested: String): List<InstalledFace> =
        installedFonts.asSequence()
            .filter { belongsToFamily(it, requested) && !it.isItalicFace }
            .map { InstalledFace(it, it.inferredWeight) }
            .distinctBy { it.font.psName }
            .toList()

    /** Progressively drops trailing style and weight words when matching installed families. */
    private fun strippedCandidates(requested: String): Sequence<String> = sequence {
        var current = requested
        while (true) {
            val trimmed = stripTrailingToken(current) ?: break
            if (trimmed == current) break
            current = trimmed
            yield(current)
        }
    }

    private fun stripTrailingToken(name: String): String? {
        val index = name.lastIndexOf(' ')
        if (index <= 0) return null
        return name.substring(0, index).takeIf { name.substring(index + 1) in removableTokens }
    }

    /** Full face names such as "Arial Bold Italic" resolve exactly, including italic faces. */
    private fun exactFace(requested: String): Font? = exactFaces[requested]

    private val exactFaces by lazy {
        buildMap {
            installedFonts.forEach { font ->
                for (locale in arrayOf(Locale.ROOT, Locale.getDefault())) {
                    val name = normalizeName(font.getFontName(locale))
                    if (name !in this) put(name, font)
                }
                val postScript = normalizeName(font.psName)
                if (postScript !in this) put(postScript, font)
            }
        }
    }

    fun lyricFallback(weight: LyricFontWeight, size: Float): Font = bundled.getValue(weight).deriveFont(size)

    fun glyphFallback(reference: Font): Font {
        val style = if (reference.inferredWeight >= 600) Font.BOLD else Font.PLAIN
        return Font(Font.SANS_SERIF, style, reference.size.coerceAtLeast(1)).deriveFont(reference.size2D)
    }

    private fun belongsToFamily(font: Font, requested: String): Boolean {
        return sequenceOf(Locale.ROOT, Locale.getDefault()).any { locale ->
            val fontFamily = normalizeName(font.getFamily(locale))
            val fontName = normalizeName(font.getFontName(locale))
            if (fontFamily == requested || fontName == requested) return@any true
            val suffix = fontFamily.removePrefix("$requested ")
            suffix != fontFamily && suffix in weightFamilySuffixes
        }
    }

    private val Font.isItalicFace: Boolean
        get() {
            val name = normalizeName("${getFontName(Locale.ROOT)} $psName")
            return name.containsWord("italic") || name.containsWord("oblique")
        }

    private val Font.inferredWeight: Int
        get() {
            val name = normalizeName("${getFamily(Locale.ROOT)} ${getFontName(Locale.ROOT)} $psName")
            return weightNames.firstOrNull { (marker, _) -> name.containsWord(marker) }?.second ?: 400
        }

    private fun String.containsWord(marker: String): Boolean =
        this == marker || startsWith("$marker ") || endsWith(" $marker") || contains(" $marker ")

    private fun normalizeName(name: String): String = name
        .trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("[-_]+"), " ")
        .replace(Regex("\\s+"), " ")

    private val weightNames = listOf(
        "extra black" to 950, "ultra black" to 950,
        "extra bold" to 800, "ultra bold" to 800, "extrabold" to 800, "ultrabold" to 800,
        "semi bold" to 600, "demi bold" to 600, "semibold" to 600, "demibold" to 600,
        "extra light" to 200, "ultra light" to 200, "extralight" to 200, "ultralight" to 200,
        "semi light" to 350, "demi light" to 350, "semilight" to 350, "demilight" to 350,
        "hairline" to 100, "thin" to 100,
        "light" to 300,
        "medium" to 500,
        "black" to 900, "heavy" to 900,
        "bold" to 700,
        "regular" to 400, "normal" to 400, "book" to 400, "roman" to 400
    )

    private val weightFamilySuffixes = weightNames.mapTo(mutableSetOf()) { it.first }

    /** Trailing tokens stripped when matching faces under a base family name. */
    private val removableTokens = weightFamilySuffixes + "ui"

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
