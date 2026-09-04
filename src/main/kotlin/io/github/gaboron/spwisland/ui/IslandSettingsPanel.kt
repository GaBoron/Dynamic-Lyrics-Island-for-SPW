// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.core.*
import java.awt.*
import javax.swing.*

/** The controls and persistence use one store; no host Compose state or float slider is involved. */
class IslandSettingsPanel(private val store: SettingsStore) : JPanel() {
    private val status = JLabel("更改立即保存；滑块松开后保存，可用方向键逐个整数调整。")
    private val bindings = mutableListOf<(IslandSettings) -> Unit>()
    private var refreshing = false

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = BorderFactory.createEmptyBorder(16, 20, 16, 20)
        toggle("显示词岛", "enabled") { it.enabled }
        toggle("显示翻译", "translation") { it.translation }
        toggle("逐字高亮与长音强调", "karaoke") { it.karaoke }
        toggle("鼠标穿透", "click_through") { it.clickThrough }
        toggle("全屏时隐藏", "hide_fullscreen") { it.hideFullscreen }
        toggle("暂停时隐藏", "hide_paused") { it.hidePaused }
        toggle("减少动画", "reduced_motion") { it.reducedMotion }
        val shape = JComboBox(arrayOf("胶囊", "顶部刘海")).apply { name = "shape" }
        shape.addActionListener { write("shape", if (shape.selectedIndex == 1) "notch" else "pill") }
        bindings += { shape.selectedIndex = if (it.notch) 1 else 0 }
        row("外观", shape)
        val font = JComboBox(GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames).apply {
            name = "font_family"; isEditable = true; maximumRowCount = 12
        }
        font.addActionListener { write("font_family", font.selectedItem?.toString()?.trim().orEmpty()) }
        bindings += { font.selectedItem = it.fontFamily }
        row("字体", font)
        integer("字号", "font_size", 14, 42) { it.fontSize }
        integer("最大宽度", "max_width", 280, 1200) { it.maxWidth }
        integer("背景不透明度 %", "opacity", 35, 100) { it.opacity }
        integer("歌词偏移 ms", "offset_ms", -2000, 2000) { it.offsetMs }
        add(JLabel("正偏移提前逐字高亮；普通逐行歌词的切换由 SPW 决定。").apply { alignmentX = LEFT_ALIGNMENT })
        add(Box.createVerticalStrut(12))
        add(JButton("找回词岛：显示、解锁并回到顶部").apply {
            alignmentX = LEFT_ALIGNMENT
            addActionListener { save {
                store.set("enabled", true); store.set("click_through", false); store.resetPosition(); refresh()
            } }
        })
        add(Box.createVerticalStrut(12)); add(status.apply { alignmentX = LEFT_ALIGNMENT })
        refresh()
    }
    private fun toggle(label: String, key: String, get: (IslandSettings) -> Boolean) {
        val control = JCheckBox(label).apply { name = key; alignmentX = LEFT_ALIGNMENT }
        control.addActionListener { write(key, control.isSelected) }
        bindings += { control.isSelected = get(it) }
        add(control)
    }
    private fun integer(label: String, key: String, min: Int, max: Int, get: (IslandSettings) -> Int) {
        val slider = JSlider(min, max).apply { name = key; minorTickSpacing = 1; snapToTicks = true }
        val value = JSpinner(SpinnerNumberModel(get(store.read()), min, max, 1)).apply {
            name = "$key.value"; editor = JSpinner.NumberEditor(this, "0")
        }
        var syncing = false
        slider.addChangeListener {
            if (!syncing) {
                syncing = true; value.value = slider.value; syncing = false
                if (!slider.valueIsAdjusting) write(key, slider.value)
            }
        }
        value.addChangeListener {
            if (!syncing) {
                syncing = true; slider.value = (value.value as Number).toInt(); syncing = false
                write(key, slider.value)
            }
        }
        bindings += { syncing = true; slider.value = get(it); value.value = get(it); syncing = false }
        row(label, JPanel(BorderLayout(8, 0)).apply { add(slider); add(value, BorderLayout.EAST) })
    }
    private fun row(label: String, control: JComponent) {
        add(JPanel(BorderLayout(12, 0)).apply {
            alignmentX = LEFT_ALIGNMENT; maximumSize = Dimension(Int.MAX_VALUE, 38)
            border = BorderFactory.createEmptyBorder(4, 0, 4, 0)
            add(JLabel(label).apply { preferredSize = Dimension(125, 28) }, BorderLayout.WEST)
            add(control)
        })
    }
    private fun write(key: String, value: Any) {
        if (!refreshing) save { store.set(key, value) }
    }
    private fun save(action: () -> Unit) {
        try { action(); status.foreground = Color(30, 120, 65); status.text = "已保存并应用" }
        catch (error: Exception) {
            status.foreground = Color(175, 35, 35); status.text = error.message ?: "保存失败"
            refresh()
        }
    }
    fun refresh() {
        refreshing = true
        try { val settings = store.read(); bindings.forEach { it(settings) } }
        finally { refreshing = false }
    }
}
