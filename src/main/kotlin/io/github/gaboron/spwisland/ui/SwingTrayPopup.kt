// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import java.awt.Font
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

    fun show(location: Point) {
        check(SwingUtilities.isEventDispatchThread())
        val anchor = owner ?: JWindow().apply {
            type = Window.Type.POPUP
            isAlwaysOnTop = true
            setSize(1, 1)
        }.also { owner = it }
        anchor.location = location
        anchor.isVisible = true
        createMenu().apply {
            isLightWeightPopupEnabled = false
            applyFont(this, displayFont())
            addPopupMenuListener(object : PopupMenuListener {
                override fun popupMenuWillBecomeVisible(event: PopupMenuEvent) = Unit
                override fun popupMenuWillBecomeInvisible(event: PopupMenuEvent) { anchor.isVisible = false }
                override fun popupMenuCanceled(event: PopupMenuEvent) { anchor.isVisible = false }
            })
            show(anchor.contentPane, 0, 0)
        }
    }

    internal fun displayFont(): Font = Windows11PopupStyle.font

    private fun applyFont(component: java.awt.Component, font: Font) {
        component.font = font
        if (component is java.awt.Container) component.components.forEach { applyFont(it, font) }
    }

    override fun close() {
        val dispose = { owner?.dispose(); owner = null }
        if (SwingUtilities.isEventDispatchThread()) dispose() else SwingUtilities.invokeLater(dispose)
    }
}
