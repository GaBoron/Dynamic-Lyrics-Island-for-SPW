// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.platform.GlobalMenuDismisser
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Window
import javax.swing.JPopupMenu
import javax.swing.JWindow
import javax.swing.SwingUtilities
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener

/** Uses Swing's composite fonts instead of AWT's native tray menu, which can render CJK as boxes. */
class SwingTrayPopup(private val createMenu: () -> JPopupMenu) : AutoCloseable {
    private var owner: JWindow? = null
    private var visibleMenu: JPopupMenu? = null

    /** Dismisses the menu on an outside click without swallowing the click, so it
     * still reaches whatever window sits underneath (native tray-menu behaviour). */
    private val dismisser = GlobalMenuDismisser { hideMenu() }

    fun show(location: Point) {
        check(SwingUtilities.isEventDispatchThread())
        hideMenu()
        // Use the real LOGICAL cursor position. The TrayIcon event coords passed
        // by callers can be un-scaled physical pixels on hi-DPI displays, which
        // would push the menu beyond the logical screen and clamp it to the
        // bottom-right corner.
        val info = MouseInfo.getPointerInfo()
        val cursor = info?.location ?: location
        val device = info?.device
            ?: owner?.graphicsConfiguration?.device
            ?: GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
        val anchor = owner ?: JWindow().apply {
            type = Window.Type.POPUP
            isAlwaysOnTop = true
            setSize(1, 1)
        }.also { owner = it }
        val menu = createMenu().apply {
            isLightWeightPopupEnabled = false
            applyFont(this, displayFont())
            addPopupMenuListener(object : PopupMenuListener {
                override fun popupMenuWillBecomeVisible(event: PopupMenuEvent) = Unit
                override fun popupMenuWillBecomeInvisible(event: PopupMenuEvent) { owner?.isVisible = false; dismisser.disarm() }
                override fun popupMenuCanceled(event: PopupMenuEvent) { owner?.isVisible = false; dismisser.disarm() }
            })
        }
        visibleMenu = menu

        // Place the menu on the device under the cursor, its bottom-left corner
        // sitting on the cursor (with clamping + flip when too close to an edge).
        val size = menu.preferredSize
        val screen = device.defaultConfiguration.bounds
        var px = cursor.x
        var py = cursor.y - size.height
        if (px + size.width > screen.x + screen.width) px = screen.x + screen.width - size.width
        if (px < screen.x) px = screen.x
        if (py < screen.y) py = cursor.y
        anchor.location = Point(px, py)

        anchor.isVisible = true
        menu.show(anchor.contentPane, 0, 0)
        // Arm the hook now that the heavyweight menu window exists.
        SwingUtilities.getWindowAncestor(menu)?.let { dismisser.arm(it) }
    }

    private fun hideMenu() {
        visibleMenu?.isVisible = false
        visibleMenu = null
        owner?.isVisible = false
        dismisser.disarm()
    }

    internal fun displayFont(): Font = Windows11PopupStyle.font

    private fun applyFont(component: java.awt.Component, font: Font) {
        component.font = font
        if (component is java.awt.Container) component.components.forEach { applyFont(it, font) }
    }

    override fun close() {
        val dispose = {
            hideMenu()
            owner?.dispose(); owner = null
        }
        if (SwingUtilities.isEventDispatchThread()) dispose() else SwingUtilities.invokeLater(dispose)
    }
}
