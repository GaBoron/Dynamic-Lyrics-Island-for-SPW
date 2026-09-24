// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.core.SettingsStore
import java.awt.Window

/** Recovery controls remain available in SPW settings even if the tray is unavailable. */
class IslandMenu(private val store: SettingsStore, private val report: (Throwable) -> Unit,
                 owner: Window) : AutoCloseable {
    private var linuxTray: GtkTray? = null
    private val commands = IslandMenuCommands(store, ::about) { ProjectLinks.openSource() }
    private val aboutDialog = AboutDialog(owner, report)
    fun installTray() {
        if (linuxTray == null) linuxTray = GtkTray(commands::entries,
            { command -> runCatching { commands.execute(command) }.onFailure(report) }, report)
    }
    fun about() = aboutDialog.show()
    override fun close() {
        linuxTray?.close(); linuxTray = null
        aboutDialog.close()
    }
}
