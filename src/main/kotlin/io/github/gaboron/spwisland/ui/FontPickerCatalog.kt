// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.core.LyricFontWeight
import com.sun.jna.Platform
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

internal data class PickerFace(val name: String, val weight: LyricFontWeight, val italic: Boolean,
                               val displayName: String = "") {
    val label: String get() {
        val upright = when (weight.value) {
            in 100..199 -> "极细"
            in 200..299 -> "特细"
            in 300..349 -> "细体"
            in 350..399 -> "半细"
            in 400..449 -> "正体"
            in 450..549 -> "中等"
            in 550..649 -> "半粗"
            in 650..749 -> "粗体"
            in 750..849 -> "特粗"
            else -> "黑体"
        }
        return if (italic) if (weight == LyricFontWeight.REGULAR) "斜体"
            else upright.removeSuffix("体") + "斜体" else upright
    }
}

internal data class PickerFamily(val name: String, val localizedName: String, val faces: List<PickerFace>,
                                 val source: String = "", val variable: Boolean = false) {
    val category: PickerFontCategory get() {
        if (name.isEmpty()) return PickerFontCategory.SIMPLIFIED_CHINESE
        val text = "$name $localizedName".lowercase(Locale.ROOT)
        if (simplifiedMarkers.containsMatchIn(text)) return PickerFontCategory.SIMPLIFIED_CHINESE
        if (otherMarkers.containsMatchIn(text) || localizedName.any { it.code > 127 })
            return PickerFontCategory.OTHER
        return PickerFontCategory.ENGLISH
    }
}

internal enum class PickerFontCategory { SIMPLIFIED_CHINESE, ENGLISH, OTHER }

private val simplifiedMarkers = Regex(
    "(?i)(?:\\bsc\\b|simplified chinese|yahei|simsun|simhei|simkai|fangsong|dengxian|kaiti|\\bhei\\b|" +
        "微软雅黑|黑体|宋体|楷体|仿宋|等线|简体)"
)
private val otherMarkers = Regex(
    "(?i)(?:\\btc\\b|\\bjp\\b|\\bkr\\b|traditional chinese|jhenghei|mingliu|gothic|" +
        "mincho|hiragino|malgun|batang|gulim|emoji|symbol|dingbat|arabic|hebrew|thai|devanagari)"
)

/** AWT names are also used by the existing lyric font resolver. */
internal object FontPickerCatalog {
    fun load(): List<PickerFamily> {
        if (Platform.isWindows()) return windowsFonts()
        val locale = Locale.getDefault()
        val installed = GraphicsEnvironment.getLocalGraphicsEnvironment().allFonts
        val families = installed.groupBy { it.getFamily(Locale.ROOT) }
        return listOf(PickerFamily("", "内置 MiSans", bundledFaces, "插件内置字体", true)) +
            families.map { (name, fonts) ->
            PickerFamily(name, fonts.first().getFamily(locale), fonts.map { font ->
                PickerFace(font.getFontName(locale), weightOf(font), font.isItalic)
            }.distinctBy { it.name })
        }.sortedBy { it.name.lowercase(locale) }
    }

    private fun windowsFonts(): List<PickerFamily> {
        val entries = listOf(
            WinReg.HKEY_LOCAL_MACHINE to "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Fonts",
            WinReg.HKEY_CURRENT_USER to "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Fonts"
        ).flatMap { (root, key) ->
            runCatching { Advapi32Util.registryGetValues(root, key).entries.toList() }
                .getOrDefault(emptyList())
        }
        val grouped = linkedMapOf<String, MutableList<PickerFace>>()
        val sources = mutableMapOf<String, String>()
        for ((rawName, file) in entries) {
            val fileName = file as? String ?: continue
            val names = rawName.replace(Regex("\\s*\\([^)]*\\)\\s*$"), "").split(" & ")
            for (name in names) {
                val fullName = name.trim()
                if (fullName.isEmpty()) continue
                val family = baseFamily(fullName)
                val face = PickerFace(fullName, weightOfName(fullName),
                    fullName.contains("Italic", true) || fullName.contains("Oblique", true),
                    fullName.removePrefix(family).trim().ifEmpty { "Regular" })
                grouped.getOrPut(family) { mutableListOf() }.add(face)
                sources.putIfAbsent(family, fileName)
            }
        }
        val bundled = PickerFamily("", "内置 MiSans", bundledFaces, "插件内置字体", true)
        return listOf(bundled) + grouped.map { (name, faces) ->
            PickerFamily(name, name, faces.distinctBy { it.name }, sources[name].orEmpty(),
                sources[name]?.contains("VF", true) == true || name.contains("Variable", true))
        }.sortedBy { it.name.lowercase(Locale.getDefault()) }
    }

    /** Add local names after the fast registry list is already visible. */
    fun localize(families: List<PickerFamily>): List<PickerFamily> {
        if (!Platform.isWindows()) return families
        val locale = Locale.getDefault()
        return families.map { family ->
            if (family.name.isEmpty() || !localizedCandidates.containsMatchIn(family.name)) return@map family
            val path = fontPath(family.source) ?: return@map family
            val localized = runCatching {
                Font.createFonts(path.toFile()).firstOrNull {
                    it.getFamily(Locale.ROOT).equals(family.name, true)
                }?.getFamily(locale)
            }.getOrNull()
            if (localized.isNullOrBlank() || localized == family.name) family
            else family.copy(localizedName = localized)
        }
    }

    private fun fontPath(file: String): Path? {
        val direct = runCatching { Path.of(file) }.getOrNull()
        if (direct?.isAbsolute == true && Files.isRegularFile(direct)) return direct
        val candidates = listOfNotNull(
            runCatching { Path.of(System.getenv("WINDIR") ?: "C:\\Windows", "Fonts", file) }.getOrNull(),
            System.getenv("LOCALAPPDATA")?.let { Path.of(it, "Microsoft", "Windows", "Fonts", file) }
        )
        return candidates.firstOrNull(Files::isRegularFile)
    }

    private val localizedCandidates = Regex("(?i)(?:SC|TC|YaHei|Kai|Sim|Song|Hei|Fang|Ping|Hiragino|Yu|Noto)")

    private val bundledFaces = listOf(
        PickerFace("", LyricFontWeight.LIGHT, false),
        PickerFace("", LyricFontWeight.REGULAR, false),
        PickerFace("", LyricFontWeight.MEDIUM, false),
        PickerFace("", LyricFontWeight.BOLD, false)
    )

    private fun baseFamily(name: String): String {
        var result = name
        val suffix = Regex("(?i)(?:\\s+(?:Regular|Normal|Thin|ExtraLight|UltraLight|Light|SemiLight|DemiLight|Medium|SemiBold|DemiBold|Bold|ExtraBold|UltraBold|Black|Heavy|Italic|Oblique))+$")
        result = result.replace(suffix, "")
        return result.ifEmpty { name }
    }

    private fun weightOfName(name: String): LyricFontWeight {
        val text = name.lowercase(Locale.ROOT)
        return when {
            "thin" in text || "hairline" in text -> LyricFontWeight.THIN
            "extra light" in text || "extralight" in text || "ultralight" in text -> LyricFontWeight(200)
            "semi light" in text || "semilight" in text || "demilight" in text -> LyricFontWeight.DEMI_LIGHT
            "light" in text -> LyricFontWeight.LIGHT
            "semi bold" in text || "semibold" in text || "demibold" in text -> LyricFontWeight(600)
            "medium" in text -> LyricFontWeight.MEDIUM
            "extra bold" in text || "extrabold" in text || "ultrabold" in text -> LyricFontWeight(800)
            "black" in text || "heavy" in text -> LyricFontWeight.BLACK
            "bold" in text -> LyricFontWeight.BOLD
            else -> LyricFontWeight.REGULAR
        }
    }

    private fun weightOf(font: Font): LyricFontWeight {
        val name = font.getFontName(Locale.ROOT).lowercase(Locale.ROOT)
        return when {
            "thin" in name || "hairline" in name -> LyricFontWeight.THIN
            "extra light" in name || "extralight" in name || "ultralight" in name -> LyricFontWeight(200)
            "semi light" in name || "semilight" in name || "demilight" in name -> LyricFontWeight.DEMI_LIGHT
            "light" in name -> LyricFontWeight.LIGHT
            "semi bold" in name || "semibold" in name || "demibold" in name -> LyricFontWeight(600)
            "medium" in name -> LyricFontWeight.MEDIUM
            "extra bold" in name || "extrabold" in name || "ultrabold" in name -> LyricFontWeight(800)
            "black" in name || "heavy" in name -> LyricFontWeight.BLACK
            "bold" in name -> LyricFontWeight.BOLD
            else -> LyricFontWeight.REGULAR
        }
    }
}
