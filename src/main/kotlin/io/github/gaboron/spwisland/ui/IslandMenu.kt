// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.core.SettingsStore
import io.github.gaboron.spwisland.platform.NativePopupMenu
import io.github.gaboron.spwisland.platform.SystemTheme
import java.awt.*
import java.awt.image.BufferedImage
import javax.swing.*

/** Recovery controls remain available in SPW settings even if the tray is unavailable. */
class IslandMenu(private val store: SettingsStore, private val report: (Throwable) -> Unit,
                 private val owner: Window) : AutoCloseable {
    private var tray: TrayIcon? = null
    private val commands = IslandMenuCommands(store, ::about) { ProjectLinks.openSource() }
    private val popup = NativePopupMenu(report)
    private fun action(block: () -> Unit) { try { block() } catch (e: Exception) { report(e) } }
    @Suppress("UNUSED_PARAMETER")
    fun popup(owner: Component, x: Int, y: Int) {
        showPopup()
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
                    if (event.isPopupTrigger) SwingUtilities.invokeLater { this@IslandMenu.showPopup() }
                }
            })
        }
        SystemTray.getSystemTray().add(created)
        tray = created
    }
    private fun showPopup() {
        popup.show(
            commands.entries(),
            dark = !SystemTheme.isLight(),
            lowPerformance = store.read().lowPerformance,
            owner = owner
        ) { command ->
            SwingUtilities.invokeLater { action { commands.execute(command) } }
        }
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
    override fun close() {
        popup.close()
        tray?.let { SystemTray.getSystemTray().remove(it) }; tray = null
    }
}
