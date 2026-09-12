// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.core.SettingsStore
import javax.swing.JCheckBoxMenuItem
import javax.swing.JMenuItem
import javax.swing.JPopupMenu

/** Reusable settings sections; the complete configuration remains owned by SPW. */
internal object SettingsMenuSections {
    fun addCommon(menu: JPopupMenu, store: SettingsStore, report: (Throwable) -> Unit) = with(menu) {
        add(sectionTitle("常用设置"))
        val settings = store.read()

        toggle("低性能模式", "reduced_motion", settings.lowPerformance, store, report)
        stringToggle("顶部刘海", "shape", settings.notch, "notch", "pill", store, report)
        addSeparator()
        toggle("显示翻译", "translation", settings.translation, store, report)
        toggle("逐字高亮", "karaoke", settings.karaoke, store, report)
        addSeparator()
        toggle("鼠标穿透", "click_through", settings.clickThrough, store, report)
        toggle("悬停自动隐藏", "auto_hide_on_hover", settings.autoHideOnHover, store, report)
    }

    fun addDisplay(menu: JPopupMenu, store: SettingsStore, report: (Throwable) -> Unit) = with(menu) {
        add(sectionTitle("显示设置"))
        val settings = store.read()
        toggle("显示词岛", "enabled", settings.enabled, store, report)
        toggle("实验性多行歌词", "experimental_multi_line", settings.experimentalMultiLine, store, report)
        addSeparator()
        toggle("全屏时隐藏", "hide_fullscreen", settings.hideFullscreen, store, report)
        toggle("暂停时隐藏", "hide_paused", settings.hidePaused, store, report)
    }

    private fun JPopupMenu.toggle(
        label: String,
        key: String,
        selected: Boolean,
        store: SettingsStore,
        report: (Throwable) -> Unit
    ) {
        add(JCheckBoxMenuItem(label, selected).apply {
            addActionListener { runCatching { store.set(key, isSelected) }.onFailure(report) }
        })
    }

    private fun JPopupMenu.stringToggle(
        label: String,
        key: String,
        selected: Boolean,
        selectedValue: String,
        unselectedValue: String,
        store: SettingsStore,
        report: (Throwable) -> Unit
    ) {
        add(JCheckBoxMenuItem(label, selected).apply {
            addActionListener {
                runCatching { store.set(key, if (isSelected) selectedValue else unselectedValue) }.onFailure(report)
            }
        })
    }

    private fun sectionTitle(label: String) = JMenuItem(label).apply { isEnabled = false }
}
