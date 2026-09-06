// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.core.SettingsStore
import java.awt.*
import java.awt.image.BufferedImage
import javax.swing.*

/** Recovery controls remain available in SPW settings even if the tray is unavailable. */
class IslandMenu(private val store: SettingsStore, private val report: (Throwable) -> Unit) : AutoCloseable {
    private var tray: TrayIcon? = null
    private val trayPopup = SwingTrayPopup(::trayMenu)
    private fun action(block: () -> Unit) { try { block() } catch (e: Exception) { report(e) } }
    fun popup(owner: Component, x: Int, y: Int) {
        val menu = JPopupMenu()
        menu.add(JMenuItem("完整设置请在 SPW 插件配置中调整").apply { isEnabled = false })
        menu.addSeparator()
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
        val created = TrayIcon(icon, "Dynamic Lyrics Island for SPW").apply {
            isImageAutoSize = true
            addActionListener { SwingUtilities.invokeLater { action { store.set("enabled", !store.read().enabled) } } }
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mousePressed(event: java.awt.event.MouseEvent) = showPopup(event)
                override fun mouseReleased(event: java.awt.event.MouseEvent) = showPopup(event)
                private fun showPopup(event: java.awt.event.MouseEvent) {
                    if (event.isPopupTrigger) SwingUtilities.invokeLater { trayPopup.show(event.locationOnScreen) }
                }
            })
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
            "AMLL 歌词动画：AMLL contributors / Steve-xmh\n" +
            "https://github.com/amll-dev/applemusic-like-lyrics\n" +
            "动画移植模块：AGPL-3.0-only；其他程序：GPL-3.0-only。\n" +
            "按两者第 13 条组合分发；SPW API：Apache-2.0。\n" +
            "在适用法律允许范围内不提供担保，可按对应许可证再分发。\n" +
            "完整许可、第三方声明及对应源码随插件 ZIP 提供。",
            "关于与许可", JOptionPane.INFORMATION_MESSAGE)
    }
    private fun trayMenu() = JPopupMenu().apply {
        fun item(label: String, block: () -> Unit) { add(JMenuItem(label).apply { addActionListener { action(block) } }) }
        item("显示／隐藏词岛") { store.set("enabled", !store.read().enabled) }
        item("解除鼠标穿透并显示") { store.set("click_through", false); store.set("enabled", true) }
        item("重置位置") { store.resetPosition() }
        addSeparator()
        item("关于与许可") { about() }
        item("项目源代码（GitHub）") { ProjectLinks.openSource() }
    }
    override fun close() {
        trayPopup.close()
        tray?.let { SystemTray.getSystemTray().remove(it) }; tray = null
    }
}
