// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.platform.SystemTheme
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.Rectangle
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.ActionEvent
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.util.Collections
import java.util.WeakHashMap
import javax.swing.Icon
import javax.swing.JCheckBoxMenuItem
import javax.swing.JComponent
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.JRadioButtonMenuItem
import javax.swing.JSeparator
import javax.swing.MenuSelectionManager
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.border.EmptyBorder
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener
import javax.swing.plaf.basic.BasicMenuUI
import javax.swing.plaf.basic.BasicMenuItemUI
import javax.swing.plaf.basic.BasicPopupMenuUI
import javax.swing.plaf.basic.BasicSeparatorUI

/** Shared Windows 11 inspired presentation for island and tray popup menus. */
internal object Windows11PopupStyle {
    /** Set on a menu item to keep the whole menu open after it is clicked. */
    const val KEEP_OPEN_KEY = "spwIsland.keepOpenMenu"

    /** Leading edge of every menu row; all labels (toggles and steppers) start here. */
    internal const val rowStart = 8

    /** Trailing edge of every menu row. */
    internal const val rowEnd = 10

    /** Gap between a stepper's label and its range hint. */
    internal const val rangeGap = 8

    /** − and + slot width in a stepper row, shared between the stepper and the style so the
     *  middle column's right edge can be computed consistently. */
    internal const val stepperSlotWidth = 26

    /** Gap between the value box and its neighbouring slots. */
    internal const val stepperGap = 3

    /** Size of the trailing state glyph (check mark / radio dot). */
    private const val glyphBox = 16

    /** Gap between an item's text and its trailing state glyph. */
    private const val glyphGap = 6

    /** Palette for one system theme. Windows 11 dark / light context-menu colours. */
    private class Palette(
        val surface: Color,
        val outline: Color,
        val hover: Color,
        val text: Color,
        val muted: Color,
        val accent: Color,
        val accentText: Color
    )

    private val darkPalette = Palette(
        surface = Color(0x20, 0x20, 0x20),
        outline = Color(0x45, 0x45, 0x45),
        hover = Color(0x35, 0x35, 0x35),
        text = Color(0xF5, 0xF5, 0xF5),
        muted = Color(0x9D, 0x9D, 0x9D),
        accent = Color(0x60, 0xCD, 0xFF),
        accentText = Color(0x10, 0x1A, 0x22)
    )

    private val lightPalette = Palette(
        surface = Color(0xF9, 0xF9, 0xF9),
        outline = Color(0xD8, 0xD8, 0xD8),
        hover = Color(0xEB, 0xEB, 0xEB),
        text = Color(0x1B, 0x1B, 0x1B),
        muted = Color(0x5D, 0x5D, 0x5D),
        accent = Color(0x00, 0x67, 0xC0),
        accentText = Color(0xFF, 0xFF, 0xFF)
    )

    /**
     * Follows the system theme; re-read every time a menu is styled, so a Windows theme
     * switch shows up on the next right-click.
     */
    @Volatile private var lightMode = SystemTheme.isLight()
    private val palette: Palette get() = if (lightMode) lightPalette else darkPalette

    internal val surface: Color get() = palette.surface
    /** Outline/border of a menu row or popup. */
    internal val outline: Color get() = palette.outline
    internal val hover: Color get() = palette.hover
    internal val text: Color get() = palette.text
    internal val muted: Color get() = palette.muted
    internal val accent: Color get() = palette.accent

    /** Text drawn on top of [accent], e.g. the selected digits of an inline editor. */
    internal val accentText: Color get() = palette.accentText

    private const val SETTLE_DELAY_MS = 120

    /** How long the pointer may sit outside the menu before open submenus fold away. */
    private const val HOVER_GRACE_MS = 350
    private const val HOVER_POLL_MS = 120

    /** Default row height of a menu row: compact by design, so a long menu fits. */
    internal const val defaultRowHeight = 28

    /** Row height used when a fully expanded menu has to squeeze into a small work area. */
    private const val compactRowHeight = 24

    /** Popup border (5 + 5) and separator height, both needed to size an expanded menu. */
    private const val menuPadding = 10
    private const val separatorHeight = 9

    /** Popup windows already watched for post-show moves (kept weakly). */
    private val watchedWindows = Collections.newSetFromMap(WeakHashMap<Window, Boolean>())

    private val submenuWatcher = Timer(HOVER_POLL_MS) { collapseAbandonedSubmenus() }
    private var outsideSince = 0L

    val font: Font by lazy {
        val available = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
        val sample = "显示／隐藏词岛项目源代码（GitHub）"
        sequenceOf("Microsoft YaHei UI", "Segoe UI Variable Text", "Segoe UI")
            .filter { it in available }
            .map { Font(it, Font.PLAIN, 13) }
            .firstOrNull { it.canDisplayUpTo(sample) == -1 }
            ?: Font("Dialog", Font.PLAIN, 13)
    }

    /** Font for a given row height: compact rows use a smaller face so text still fits. */
    internal fun fontFor(rowHeight: Int): Font = when {
        rowHeight >= 32 -> font
        rowHeight >= 28 -> font.deriveFont(12f)
        else -> font.deriveFont(11f)
    }

    /**
     * Row height that lets [rowCount] rows plus [separators] separators fit [availableHeight],
     * so a fully expanded menu adapts to the screen instead of being clipped. Rows stay
     * readable: the result never goes below [compactRowHeight] nor above [defaultRowHeight].
     */
    internal fun rowHeightFor(rowCount: Int, separators: Int, availableHeight: Int = workAreaHeight()): Int {
        val usable = availableHeight - menuPadding - separators * separatorHeight
        return (usable / rowCount.coerceAtLeast(1)).coerceIn(compactRowHeight, defaultRowHeight)
    }

    /** Height of the desktop work area (taskbar excluded) of the screen under the pointer. */
    private fun workAreaHeight(): Int {
        val device = MouseInfo.getPointerInfo()?.device
            ?: GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
        val bounds = device.defaultConfiguration.bounds
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(device.defaultConfiguration)
        return bounds.height - insets.top - insets.bottom
    }

    /**
     * Styles [menu]. Row height is chosen so that the whole menu fits the work area
     * (tray menus can carry every setting inline); pass an explicit height to match a
     * parent menu, e.g. for its submenus.
     */
    fun apply(menu: JPopupMenu, rowHeight: Int = 0) {
        lightMode = SystemTheme.isLight()
        val height = if (rowHeight > 0) rowHeight else adaptiveRowHeight(menu)
        menu.ui = PopupUi()
        menu.isOpaque = false
        menu.background = surface
        menu.foreground = text
        menu.border = EmptyBorder(5, 5, 5, 5)
        menu.components.forEach { styleComponent(it, height) }
        menu.addPopupMenuListener(object : PopupMenuListener {
            override fun popupMenuWillBecomeVisible(event: PopupMenuEvent) {
                SwingUtilities.invokeLater { settlePopup(menu) }
                // The toolkit can still resize or move the freshly shown popup (and opening
                // a submenu can shift its parent), so settle once more a beat later.
                Timer(SETTLE_DELAY_MS) { settlePopup(menu) }.apply { isRepeats = false }.start()
                startSubmenuWatcher()
            }
            override fun popupMenuWillBecomeInvisible(event: PopupMenuEvent) = stopWatcherWhenIdle()
            override fun popupMenuCanceled(event: PopupMenuEvent) = stopWatcherWhenIdle()
        })
    }

    /** Row height that fits this menu's own rows into the work area. */
    private fun adaptiveRowHeight(menu: JPopupMenu): Int {
        val separators = menu.components.count { it is JSeparator }
        val rows = (menu.componentCount - separators).coerceAtLeast(1)
        return rowHeightFor(rows, separators)
    }

    private fun settlePopup(menu: JPopupMenu) {
        fitPopupToWorkArea(menu)
        paintPopupBackground(menu)
        watchPopupWindow(menu)
    }

    /**
     * A heavyweight popup window keeps the look-and-feel's own content-pane colour in the
     * strip and corners that [PopupUi.paint] leaves unpainted — that is the light seam
     * around an otherwise dark menu. Paint the window in the menu colour.
     */
    private fun paintPopupBackground(menu: JPopupMenu) {
        val window = SwingUtilities.getWindowAncestor(menu) ?: return
        window.background = surface
        val content = (window as? RootPaneContainer)?.contentPane ?: return
        content.background = surface
        (content as? JComponent)?.isOpaque = true
    }

    /**
     * Swing keeps a submenu open until another *menu item* is entered, and `mouseExited`
     * does nothing; rows that are not menu items (the inline steppers) never touch the
     * selection at all, so the 字体 / 垂直锚点 submenu used to hang around. Whenever the
     * pointer is outside the whole menu tree for a moment, fold the submenus back into
     * the top-level menu — like a native Windows menu.
     */
    private fun startSubmenuWatcher() {
        outsideSince = 0L
        if (!submenuWatcher.isRunning) submenuWatcher.start()
    }

    private fun stopWatcherWhenIdle() {
        if (Window.getWindows().none { it.isShowing && isMenuPopup(it) }) {
            submenuWatcher.stop()
            outsideSince = 0L
        }
    }

    private fun collapseAbandonedSubmenus() {
        val manager = MenuSelectionManager.defaultManager()
        val path = manager.selectedPath
        if (path.size <= 1) {
            outsideSince = 0L
            return
        }
        if (pointerInsideMenuTree()) {
            outsideSince = 0L
            return
        }
        val now = System.currentTimeMillis()
        if (outsideSince == 0L) {
            outsideSince = now
            return
        }
        if (now - outsideSince >= HOVER_GRACE_MS) {
            outsideSince = 0L
            manager.setSelectedPath(arrayOf(path[0]))
        }
    }

    private fun pointerInsideMenuTree(): Boolean {
        val pointer = MouseInfo.getPointerInfo()?.location ?: return true
        return Window.getWindows().any { it.isShowing && isMenuPopup(it) && it.bounds.contains(pointer) }
    }

    /** True for the popup windows this style created, i.e. windows holding a JPopupMenu. */
    private fun isMenuPopup(window: Window): Boolean {
        val content = (window as? RootPaneContainer)?.contentPane ?: return false
        return containsPopup(content)
    }

    private fun containsPopup(container: Container): Boolean {
        if (container is JPopupMenu) return true
        for (child in container.components) if (child is Container && containsPopup(child)) return true
        return false
    }

    /** Keeps the popup inside the work area even if something moves it afterwards. */
    private fun watchPopupWindow(menu: JPopupMenu) {
        val window = SwingUtilities.getWindowAncestor(menu) ?: return
        if (!watchedWindows.add(window)) return
        window.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(event: ComponentEvent) = fitPopupToWorkArea(menu)
            override fun componentMoved(event: ComponentEvent) = fitPopupToWorkArea(menu)
        })
    }

    private fun styleComponent(component: Component, rowHeight: Int) {
        when (component) {
            is StepperItem -> component.useRowHeight(rowHeight)
            is JMenu -> {
                // JMenu must keep BasicMenuUI: it owns popup expansion (hover/click).
                component.ui = MenuUi()
                styleItem(component, rowHeight)
                // Submenus keep the parent's row height when that already fits, but may
                // compress further on their own: the settings submenu carries far more
                // rows than the menu it hangs off.
                val own = adaptiveRowHeight(component.popupMenu)
                apply(component.popupMenu, minOf(rowHeight, own))
            }
            is JMenuItem -> {
                component.ui = MenuItemUi(component.getClientProperty(KEEP_OPEN_KEY) == true)
                styleItem(component, rowHeight)
            }
            is JSeparator -> {
                component.ui = SeparatorUi()
                component.foreground = outline
                component.preferredSize = Dimension(1, separatorHeight)
            }
        }
    }

    private fun styleItem(component: JMenuItem, rowHeight: Int) {
        component.font = fontFor(rowHeight)
        component.foreground = if (component.isEnabled) text else muted
        component.background = surface
        component.isOpaque = false
        // No leading icon column: the state glyph lives at the trailing edge, so the
        // label starts on the same x as every stepper row in the menu.
        component.icon = null
        component.disabledIcon = null
        component.iconTextGap = 0
        // JMenuItem adds its own 2 px margin on top of the border, which pushed checkbox /
        // submenu labels 2 px right of the stepper rows. Zero it so every label really
        // starts at `rowStart`.
        component.margin = Insets(0, 0, 0, 0)
        component.border = EmptyBorder(
            0, rowStart, 0,
            if (component.isStateItem) rowEnd + glyphBox + glyphGap else rowEnd
        )
        val preferred = component.preferredSize
        component.preferredSize = Dimension(maxOf(180, preferred.width + 12), rowHeight)
    }

    /** True for the items that carry a check mark or radio dot. */
    private val JMenuItem.isStateItem: Boolean
        get() = this is JCheckBoxMenuItem || this is JRadioButtonMenuItem

    /** Draws the item's state glyph centred in the middle column between the label and the
     *  − slot — the range/check column of the three-column grid. */
    internal fun paintStateGlyph(graphics: Graphics, item: JMenuItem) {
        if (!item.isStateItem) return
        val labelEnd = rowStart + item.getFontMetrics(item.font).stringWidth(item.text)
        val left = labelEnd + rangeGap
        val right = item.width - (rangeGap + stepperSlotWidth + stepperGap + stepperSlotWidth + rowEnd)
        val x = left + (right - left - glyphBox) / 2
        val y = (item.height - glyphBox) / 2
        MenuGlyph(selectable = true).paintIcon(item, graphics, x, y)
    }

    /**
     * Swing clamps a popup to the screen bounds, not to the desktop work area, so a menu
     * opened from the tray (anchor near the bottom edge) ends up with its last rows hidden
     * behind the taskbar — the settings submenu is taller than one row, so a whole item
     * disappears. Nudge the popup back inside the work area.
     */
    private fun fitPopupToWorkArea(menu: JPopupMenu) {
        val window = SwingUtilities.getWindowAncestor(menu) ?: return
        if (window.width <= 0 || window.height <= 0) return
        val gc = window.graphicsConfiguration ?: return
        val screen = gc.bounds
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(gc)
        val left = screen.x + insets.left
        val top = screen.y + insets.top
        val right = screen.x + screen.width - insets.right
        val bottom = screen.y + screen.height - insets.bottom
        var x = window.x
        var y = window.y
        if (window.height <= bottom - top) {
            if (y + window.height > bottom) y = bottom - window.height
            if (y < top) y = top
        } else {
            // A menu taller than the work area cannot be fitted; keeping its top visible at
            // least exposes the first rows instead of hiding them behind the taskbar.
            y = top
        }
        if (window.width <= right - left) {
            if (x + window.width > right) x = right - window.width
            if (x < left) x = left
        }
        if (x != window.x || y != window.y) window.setLocation(x, y)
    }

    private class PopupUi : BasicPopupMenuUI() {
        override fun paint(graphics: Graphics, component: JComponent) {
            val g = graphics.create() as Graphics2D
            // Square corners on purpose. A rounded popup has to be clipped by the window
            // shape, and whatever the clip cuts away shows the desktop behind it — over a
            // dark wallpaper that read as a black rim in light mode and as a mismatched
            // black in dark mode. Filling the whole rectangle leaves nothing transparent.
            g.color = surface
            g.fillRect(0, 0, component.width, component.height)
            g.color = outline
            g.drawRect(0, 0, component.width - 1, component.height - 1)
            g.dispose()
            super.paint(graphics, component)
        }
    }

    private class MenuUi : BasicMenuUI() {
        override fun installDefaults() {
            super.installDefaults()
            checkIcon = null
            arrowIcon = null
            selectionForeground = text
            disabledForeground = muted
            acceleratorForeground = muted
            acceleratorSelectionForeground = text
        }

        /** The submenu arrow goes in the middle column (next to the range hint / check), not
         *  tucked against the right border — so override paint and draw it ourselves. */
        override fun paint(graphics: Graphics, component: JComponent) {
            val g = graphics.create() as Graphics2D
            super.paint(graphics, component)
            val item = menuItem
            val labelEnd = rowStart + item.getFontMetrics(item.font).stringWidth(item.text)
            val left = labelEnd + rangeGap
            val right = component.width - (rangeGap + stepperSlotWidth + stepperGap + stepperSlotWidth + rowEnd)
            val arrowX = left + (right - left - glyphBox) / 2
            val arrowY = (component.height - glyphBox) / 2
            MenuGlyph(selectable = true).paintIcon(item, g, arrowX, arrowY)
            g.dispose()
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

    private class MenuItemUi(private val keepOpen: Boolean) : BasicMenuItemUI() {
        override fun installDefaults() {
            super.installDefaults()
            checkIcon = null
            selectionForeground = text
            disabledForeground = muted
            acceleratorForeground = muted
            acceleratorSelectionForeground = text
        }

        override fun paint(graphics: Graphics, component: JComponent) {
            super.paint(graphics, component)
            paintStateGlyph(graphics, menuItem)
        }

        override fun paintBackground(graphics: Graphics, item: JMenuItem, background: Color) {
            if (!item.model.isArmed && !(item is JMenu && item.model.isSelected)) return
            val g = graphics.create() as Graphics2D
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = hover
            g.fillRoundRect(3, 2, item.width - 6, item.height - 4, 7, 7)
            g.dispose()
        }

        override fun doClick(msm: MenuSelectionManager) {
            if (menuItem.isEnabled) {
                if (keepOpen) {
                    // Settings submenu items must not dismiss the menu, so the user
                    // can keep toggling / stepping values in one sitting.
                    //
                    // AbstractButton.doClick(0) cannot be used here: ToggleButtonModel
                    // flips checkbox/radio state when setPressed(false) runs while
                    // armed — and doClick() always arms first — so any manual flip
                    // before it would be double-flipped. Flip the state ourselves and
                    // fire the action listeners directly instead.
                    menuItem.model.isArmed = false
                    menuItem.model.isPressed = false
                    when (menuItem) {
                        is JCheckBoxMenuItem -> menuItem.isSelected = !menuItem.isSelected
                        is JRadioButtonMenuItem -> menuItem.isSelected = true
                    }
                    val event = ActionEvent(menuItem, ActionEvent.ACTION_PERFORMED, menuItem.actionCommand)
                    for (listener in menuItem.actionListeners) listener.actionPerformed(event)
                } else {
                    super.doClick(msm)
                }
            } else {
                msm.clearSelectedPath()
            }
        }
    }

    private class SeparatorUi : BasicSeparatorUI() {
        override fun paint(graphics: Graphics, component: JComponent) {
            graphics.color = outline
            val y = component.height / 2
            graphics.drawLine(rowStart, y, component.width - rowEnd, y)
        }
    }

    private class MenuGlyph(private val selectable: Boolean) : Icon {
        override fun getIconWidth() = glyphBox
        override fun getIconHeight() = glyphBox
        override fun paintIcon(component: Component, graphics: Graphics, x: Int, y: Int) {
            if (!selectable) return
            val g = graphics.create() as Graphics2D
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = accent
            when (component) {
                // Submenu marker for JMenu rows (字体 / 垂直锚点), drawn in the middle column.
                is JMenu -> {
                    g.font = g.font.deriveFont(Font.BOLD, 12f)
                    g.drawString("\u203A", x + 5, y + 13)
                }
                is JCheckBoxMenuItem -> if (component.isSelected) {
                    g.stroke = BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                    g.drawLine(x + 3, y + 8, x + 7, y + 12)
                    g.drawLine(x + 7, y + 12, x + 14, y + 4)
                }
                is JRadioButtonMenuItem -> if (component.isSelected) {
                    g.fillOval(x + 4, y + 4, 8, 8)
                }
            }
            g.dispose()
        }
    }
}
