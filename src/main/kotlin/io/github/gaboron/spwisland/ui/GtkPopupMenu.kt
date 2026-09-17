// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.platform.LinuxHelper
import java.awt.Point
import javax.swing.SwingUtilities

/** System GTK theme, check marks, keyboard navigation, and compositor-managed dismissal. */
internal class GtkPopupMenu(private val report: (Throwable) -> Unit) : AutoCloseable {
    private var process: Process? = null
    private var closed = false
    fun show(entries: List<PopupMenuEntry>, anchor: Point, selected: (Int) -> Unit) {
        check(SwingUtilities.isEventDispatchThread())
        if (closed) return
        process?.destroy()
        val child = try { LinuxHelper.start("menu", anchor.x.toString(), anchor.y.toString()) }
        catch (error: Exception) { report(error); return }
        process = child
        Thread({
            try {
                child.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                    entries.forEach { entry ->
                        val label = entry.label.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ')
                        writer.write("${entry.kind}\t${entry.id}\t${if (entry.selected) 1 else 0}\t$label\n")
                    }
                }
                val command = child.inputStream.bufferedReader(Charsets.UTF_8).use { it.readLine()?.toIntOrNull() }
                val exit = child.waitFor()
                SwingUtilities.invokeLater {
                    if (!closed && process === child) {
                        process = null
                        if (exit != 0) report(IllegalStateException("GTK 系统菜单启动失败（$exit）"))
                        else if (command != null && entries.any { it.id == command &&
                                it.kind in setOf(PopupMenuKind.ACTION, PopupMenuKind.TOGGLE) }) selected(command)
                    }
                }
            } catch (error: Exception) {
                child.destroy()
                SwingUtilities.invokeLater { if (!closed && process === child) { process = null; report(error) } }
            }
        }, "SPW GTK menu").apply { isDaemon = true }.start()
    }
    override fun close() { closed = true; process?.destroy(); process = null }
}
