// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.core.SettingsStore
import java.awt.*
import java.awt.image.BufferedImage
import javax.swing.*

/** Recovery controls remain available in SPW settings even if the tray is unavailable. */
class IslandMenu(private val store: SettingsStore, private val report: (Throwable) -> Unit) : AutoCloseable {
    private var tray: TrayIcon? = null
    private fun action(block: () -> Unit) { try { block() } catch (e: Exception) { report(e) } }
    fun popup(owner: Component, x: Int, y: Int) {
        val menu = JPopupMenu()
        fun toggle(label: String, key: String, value: Boolean) {
            menu.add(JCheckBoxMenuItem(label, value).apply { addActionListener { action { store.set(key, isSelected) } } })
        }
        val config = store.read()
        toggle("显示词岛", "enabled", config.enabled)
        toggle("鼠标穿透（从 SPW 设置解锁）", "click_through", config.clickThrough)
        toggle("显示翻译", "translation", config.translation)
        toggle("逐字高亮", "karaoke", config.karaoke)
        toggle("全屏时隐藏", "hide_fullscreen", config.hideFullscreen)
        toggle("暂停时隐藏", "hide_paused", config.hidePaused)
        toggle("减少动画", "reduced_motion", config.reducedMotion)
        menu.add(JMenuItem(if (config.notch) "切换为胶囊" else "切换为顶部刘海").apply {
            addActionListener { action { store.set("shape", if (config.notch) "pill" else "notch") } }
        })
        menu.addSeparator()
        menu.add(JMenuItem("重置位置").apply { addActionListener { action { store.resetPosition() } } })
        menu.add(JMenuItem("关于与许可").apply { addActionListener { about() } })
        menu.add(JMenuItem("项目源代码（GitHub）").apply { addActionListener { action { ProjectLinks.openSource() } } })
        menu.show(owner, x, y)
    }
    fun installTray() {
        if (!SystemTray.isSupported() || tray != null) return
        val icon = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)
        icon.createGraphics().let { g ->
            g.color = Color(12, 15, 21); g.fillRoundRect(1, 5, 30, 22, 18, 18)
            g.color = Color(132, 216, 188)
            for (i in 0..3) g.fillRoundRect(7 + i * 5, 10 + (i % 2) * 3, 3, 12 - (i % 2) * 6, 2, 2)
            g.dispose()
        }
        val menu = PopupMenu()
        fun item(label: String, block: () -> Unit) { menu.add(MenuItem(label).apply {
            addActionListener { SwingUtilities.invokeLater { action(block) } }
        }) }
        item("显示／隐藏词岛") { store.set("enabled", !store.read().enabled) }
        item("解除鼠标穿透并显示") { store.set("click_through", false); store.set("enabled", true) }
        item("重置位置") { store.resetPosition() }
        menu.addSeparator()
        item("关于与许可") { about() }
        item("项目源代码（GitHub）") { ProjectLinks.openSource() }
        val created = TrayIcon(icon, "Dynamic Lyrics Island for SPW", menu).apply {
            isImageAutoSize = true
            addActionListener { SwingUtilities.invokeLater { action { store.set("enabled", !store.read().enabled) } } }
        }
        SystemTray.getSystemTray().add(created)
        tray = created
    }
    fun about() {
        JOptionPane.showMessageDialog(null,
            "Dynamic Lyrics Island for SPW\n\n" +
            "项目源代码：${ProjectLinks.source}\n\n" +
            "灵动词岛原创：Lyricify / WXRIW（XY Wang）\n" +
            "https://github.com/WXRIW/Lyricify-App\n" +
            "创意及视觉改编：CC BY-SA 4.0\n" +
            "https://creativecommons.org/licenses/by-sa/4.0/\n\n" +
            "本项目为独立 SPW 插件，重新实现渲染、交互和时序；非官方 Lyricify 产品。\n" +
            "新增程序代码：GPL-3.0-only；SPW API：Apache-2.0。\n" +
            "完整许可、第三方声明及对应源码随插件 ZIP 提供。",
            "关于与许可", JOptionPane.INFORMATION_MESSAGE)
    }
    override fun close() { tray?.let { SystemTray.getSystemTray().remove(it) }; tray = null }
}
