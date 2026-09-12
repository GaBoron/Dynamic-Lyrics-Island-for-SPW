// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.core.IslandSettings
import io.github.gaboron.spwisland.core.LeadingContent
import io.github.gaboron.spwisland.core.SettingsStore
import java.awt.GraphicsEnvironment
import javax.swing.ButtonGroup
import javax.swing.JCheckBoxMenuItem
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JRadioButtonMenuItem

private fun <T : JMenuItem> T.keepOpen(): T {
    putClientProperty(Windows11PopupStyle.KEEP_OPEN_KEY, true)
    return this
}

/**
 * Builds the "调整词岛设置" submenu that mirrors every control shown on the SPW
 * config panel. The menu reads a fresh snapshot each time it is built and
 * writes through the same store, so it stays in sync with the SPW settings page.
 * Numeric options render as inline `− □ +` steppers for quick successive tweaks.
 */
fun settingsMenu(store: SettingsStore, report: (Throwable) -> Unit): JMenu = JMenu("调整词岛设置").apply {
    val config = store.read()

    fun toggle(label: String, key: String, value: Boolean) {
        add(JCheckBoxMenuItem(label, value).keepOpen().apply {
            addActionListener { runCatching { store.set(key, isSelected) }.onFailure(report) }
        })
    }

    /** Two-valued options presented as a switch: checked = checkedValue. */
    fun switch(label: String, key: String, checked: Boolean, checkedValue: String, uncheckedValue: String) {
        add(JCheckBoxMenuItem(label, checked).keepOpen().apply {
            addActionListener {
                runCatching { store.set(key, if (isSelected) checkedValue else uncheckedValue) }.onFailure(report)
            }
        })
    }

    fun stepper(key: String, label: String, get: (IslandSettings) -> Int, min: Int, max: Int, step: Int, unit: String) {
        add(StepperItem(
            label = label,
            rangeText = if (unit.isEmpty()) "$min–$max" else "$min–$max$unit",
            min = min, max = max, step = step,
            getValue = { get(store.read()) },
            commit = { next -> runCatching { store.set(key, next) }.onFailure(report) }
        ))
    }

    fun radio(key: String, label: String, options: List<Pair<String, String>>, current: String) {
        val menu = JMenu(label)
        val group = ButtonGroup()
        for ((value, text) in options) {
            menu.add(JRadioButtonMenuItem(text, value == current).keepOpen().apply {
                group.add(this)
                addActionListener { runCatching { store.set(key, value) }.onFailure(report) }
            })
        }
        add(menu)
    }

    // 排列顺序对齐 SPW 配置面板（island.json 键序；未落盘的默认项补在同类位置）。
    toggle("减弱动画", "reduced_motion", config.reducedMotion)
    switch("顶部刘海形状", "shape", config.notch, "notch", "pill")
    add(fontMenu(store, config.fontFamily, report))
    stepper("font_size", "字号", { it.fontSize }, 14, 42, 1, "")
    stepper("opacity", "透明度", { it.opacity }, 35, 100, 1, "%")
    stepper("max_width", "最大宽度", { it.maxWidth }, 280, 1200, 1, "px")
    toggle("固定宽度", "fixed_width", config.fixedWidth)
    toggle("暂停时隐藏", "hide_paused", config.hidePaused)
    toggle("显示翻译", "translation", config.translation)
    toggle("逐字高亮", "karaoke", config.karaoke)
    toggle("多行歌词", "experimental_multi_line", config.experimentalMultiLine)
    toggle("显示词岛", "enabled", config.enabled)
    toggle("鼠标穿透", "click_through", config.clickThrough)
    toggle("悬停自动隐藏", "auto_hide_on_hover", config.autoHideOnHover)
    stepper("offset_ms", "歌词偏移", { it.offsetMs }, -2000, 2000, 1, "ms")
    radio("vertical_anchor", "垂直锚点", listOf("free" to "自由", "top" to "顶部", "bottom" to "底部"), config.verticalAnchor.name.lowercase())
    stepper("corner_roundness", "圆角", { it.cornerRoundness }, 0, 100, 1, "%")
    toggle("歌词取封面色", "lyric_cover_color", config.lyricCoverColor)
    toggle("背景取封面色", "background_cover_color", config.backgroundCoverColor)
    toggle("频谱取封面色", "spectrum_cover_color", config.spectrumCoverColor)
    toggle("全屏时隐藏", "hide_fullscreen", config.hideFullscreen)
    switch("前导内容显示封面", "leading_content", config.leadingContent == LeadingContent.COVER, "cover", "spectrum")
}

private fun fontMenu(store: SettingsStore, current: String, report: (Throwable) -> Unit): JMenu = JMenu("字体").apply {
    val available = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
    val common = listOf(
        "微软雅黑", "微软雅黑 UI", "等线", "黑体", "宋体", "楷体",
        "Arial", "Segoe UI", "Consolas", "Dialog",
        "HarmonyOS Sans SC", "MiSans", "Source Han Sans SC", "Noto Sans CJK SC"
    )
    val candidates = (listOf(current) + common).distinct()
        .filter { it in available || it == current || it == "Dialog" }
        .take(16)
    val group = ButtonGroup()
    for (name in candidates) {
        add(JRadioButtonMenuItem(name, name == current).keepOpen().apply {
            group.add(this)
            addActionListener { runCatching { store.set("font_family", name) }.onFailure(report) }
        })
    }
}