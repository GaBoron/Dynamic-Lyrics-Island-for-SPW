// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import javax.swing.Icon
import javax.swing.JCheckBoxMenuItem
import javax.swing.JComponent
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.JSeparator
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener
import javax.swing.plaf.basic.BasicMenuItemUI
import javax.swing.plaf.basic.BasicPopupMenuUI
import javax.swing.plaf.basic.BasicSeparatorUI

/** Shared Windows 11 inspired presentation for island and tray popup menus. */
internal object Windows11PopupStyle {
    private val surface = Color(0x20, 0x20, 0x20)
    private val outline = Color(0x45, 0x45, 0x45)
    private val hover = Color(0x35, 0x35, 0x35)
    private val text = Color(0xF5, 0xF5, 0xF5)
    private val muted = Color(0x9D, 0x9D, 0x9D)
    private val accent = Color(0x60, 0xCD, 0xFF)
    private const val popupArc = 12

    val font: Font by lazy {
        val available = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
        val sample = "显示／隐藏词岛项目源代码（GitHub）"
        sequenceOf("Microsoft YaHei UI", "Segoe UI Variable Text", "Segoe UI")
            .filter { it in available }
            .map { Font(it, Font.PLAIN, 13) }
            .firstOrNull { it.canDisplayUpTo(sample) == -1 }
            ?: Font("Dialog", Font.PLAIN, 13)
    }

    fun apply(menu: JPopupMenu) {
        menu.ui = PopupUi()
        menu.isOpaque = false
        menu.background = surface
        menu.foreground = text
        menu.border = EmptyBorder(5, 5, 5, 5)
        menu.components.forEach(::styleComponent)
        menu.addPopupMenuListener(object : PopupMenuListener {
            override fun popupMenuWillBecomeVisible(event: PopupMenuEvent) {
                SwingUtilities.invokeLater { roundPopupWindow(menu) }
            }
            override fun popupMenuWillBecomeInvisible(event: PopupMenuEvent) = Unit
            override fun popupMenuCanceled(event: PopupMenuEvent) = Unit
        })
    }

    private fun styleComponent(component: Component) {
        when (component) {
            is JMenuItem -> {
                component.ui = MenuItemUi()
                component.font = font
                component.foreground = if (component.isEnabled) text else muted
                component.background = surface
                component.isOpaque = false
                component.border = EmptyBorder(0, 8, 0, 10)
                component.icon = MenuGlyph(component is JCheckBoxMenuItem)
                component.disabledIcon = component.icon
                component.iconTextGap = 8
                val preferred = component.preferredSize
                component.preferredSize = Dimension(maxOf(180, preferred.width + 12), 34)
                if (component is JMenu) apply(component.popupMenu)
            }
            is JSeparator -> {
                component.ui = SeparatorUi()
                component.foreground = outline
                component.preferredSize = Dimension(1, 9)
            }
        }
    }

    private fun roundPopupWindow(menu: JPopupMenu) {
        val window = SwingUtilities.getWindowAncestor(menu) ?: return
        if (window.width <= 0 || window.height <= 0) return
        try {
            window.shape = RoundRectangle2D.Double(
                0.0, 0.0, window.width.toDouble(), window.height.toDouble(),
                popupArc.toDouble(), popupArc.toDouble()
            )
        } catch (_: UnsupportedOperationException) {
            // The painted rounded border remains available on unsupported desktops.
        }
    }

    private class PopupUi : BasicPopupMenuUI() {
        override fun paint(graphics: Graphics, component: JComponent) {
            val g = graphics.create() as Graphics2D
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = surface
            g.fillRoundRect(0, 0, component.width - 1, component.height - 1, popupArc, popupArc)
            g.color = outline
            g.drawRoundRect(0, 0, component.width - 1, component.height - 1, popupArc, popupArc)
            g.dispose()
            super.paint(graphics, component)
        }
    }

    private class MenuItemUi : BasicMenuItemUI() {
        override fun installDefaults() {
            super.installDefaults()
            selectionForeground = text
            disabledForeground = muted
            acceleratorForeground = muted
            acceleratorSelectionForeground = text
        }

        override fun paintBackground(graphics: Graphics, item: JMenuItem, background: Color) {
            if (!item.model.isArmed && !(item is JMenu && item.model.isSelected)) return
            val g = graphics.create() as Graphics2D
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = hover
            g.fillRoundRect(3, 2, item.width - 6, item.height - 4, 7, 7)
            g.dispose()
        }
    }

    private class SeparatorUi : BasicSeparatorUI() {
        override fun paint(graphics: Graphics, component: JComponent) {
            graphics.color = outline
            val y = component.height / 2
            graphics.drawLine(32, y, component.width - 8, y)
        }
    }

    private class MenuGlyph(private val selectable: Boolean) : Icon {
        override fun getIconWidth() = 16
        override fun getIconHeight() = 16
        override fun paintIcon(component: Component, graphics: Graphics, x: Int, y: Int) {
            if (!selectable || component !is JCheckBoxMenuItem || !component.isSelected) return
            val g = graphics.create() as Graphics2D
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = accent
            g.stroke = BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.drawLine(x + 3, y + 8, x + 7, y + 12)
            g.drawLine(x + 7, y + 12, x + 14, y + 4)
            g.dispose()
        }
    }
}
