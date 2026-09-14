// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.core.SettingsStore
import io.github.gaboron.spwisland.platform.SystemTheme
import java.awt.*
import java.awt.image.BufferedImage
import javax.swing.*

/** Recovery controls remain available in SPW settings even if the tray is unavailable. */
class IslandMenu(private val store: SettingsStore, private val report: (Throwable) -> Unit,
                 private val owner: Window) : AutoCloseable {
    private var tray: TrayIcon? = null
    private val commands = IslandMenuCommands(store, ::about) { ProjectLinks.openSource() }
    private val popup = LightweightPopupMenu(owner, report)
    private val aboutDialog = AboutDialog(owner, report)
    private fun action(block: () -> Unit) { try { block() } catch (e: Exception) { report(e) } }
    fun popup(owner: Component, x: Int, y: Int) {
        val origin = owner.locationOnScreen
        showPopup(Point(origin.x + x, origin.y + y))
    }
    fun installTray() {
        popup.prewarm(commands.entries(), !SystemTheme.isLight())
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
                    if (!event.isPopupTrigger) return
                    val anchor = Point(event.xOnScreen, event.yOnScreen)
                    if (SwingUtilities.isEventDispatchThread()) this@IslandMenu.showPopup(anchor)
                    else SwingUtilities.invokeLater { this@IslandMenu.showPopup(anchor) }
                }
            })
        }
        SystemTray.getSystemTray().add(created)
        tray = created
    }
    private fun showPopup(anchor: Point) {
        popup.show(
            commands.entries(),
            useDark = !SystemTheme.isLight(),
            anchor = anchor
        ) { command ->
            action { commands.execute(command) }
        }
    }
    fun about() = aboutDialog.show()
    override fun close() {
        popup.close()
        aboutDialog.close()
        tray?.let { SystemTray.getSystemTray().remove(it) }; tray = null
    }
}
