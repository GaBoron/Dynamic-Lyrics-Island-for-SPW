// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import androidx.compose.ui.awt.ComposeWindow
import io.github.gaboron.spwisland.core.IslandSettings
import java.awt.Dimension
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.nio.file.Path
import javax.swing.JFrame
import javax.swing.SwingUtilities

/** Loads the plugin's Skiko DLL rather than the different one bundled with SPW. */
internal class ComposeFontPickerWindow(
    private val apply: (FontPickerSelection) -> Unit,
    private val report: (Throwable) -> Unit
) : AutoCloseable {
    private var window: ComposeWindow? = null

    fun show(current: IslandSettings) {
        SwingUtilities.invokeLater {
            window?.let { it.isVisible = true; it.toFront(); return@invokeLater }
            try {
                val nativeDirectory = nativeDirectory()
                val previous = System.getProperty("skiko.library.path")
                try {
                    System.setProperty("skiko.library.path", nativeDirectory.toString())
                    org.jetbrains.skiko.Library.load()
                    val frame = ComposeWindow()
                    frame.title = "选择字体 · 动态歌词岛"
                    frame.iconImage = ApplicationIdentity.icon
                    frame.minimumSize = Dimension(960, 680)
                    frame.setSize(1160, 740)
                    frame.setLocationRelativeTo(null)
                    frame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
                    frame.setContent {
                        FontPickerContent(current, onCancel = { frame.dispose() }, onApply = {
                            apply(it)
                            frame.dispose()
                        })
                    }
                    frame.addWindowListener(object : WindowAdapter() {
                        override fun windowClosed(event: WindowEvent?) {
                            if (window === frame) window = null
                        }
                    })
                    window = frame
                    frame.isVisible = true
                } finally {
                    if (previous == null) System.clearProperty("skiko.library.path")
                    else System.setProperty("skiko.library.path", previous)
                }
            } catch (error: Throwable) {
                window?.dispose()
                window = null
                report(IllegalStateException("字体窗口启动失败", error))
            }
        }
    }

    private fun nativeDirectory(): Path {
        val resource = checkNotNull(javaClass.getResource("/native/compose/skiko-windows-x64.dll")) {
            "字体窗口原生渲染资源缺失"
        }
        check(resource.protocol == "file") { "字体窗口原生渲染资源没有展开为文件" }
        return Path.of(resource.toURI()).parent
    }

    override fun close() { SwingUtilities.invokeLater { window?.dispose(); window = null } }
}
