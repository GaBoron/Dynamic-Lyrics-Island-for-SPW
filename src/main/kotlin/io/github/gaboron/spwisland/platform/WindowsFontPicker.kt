// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.platform

import io.github.gaboron.spwisland.core.IslandSettings
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.awt.Desktop
import java.net.URI
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

/** Launches the WinUI font picker without blocking SPW's UI thread. */
internal data class FontChoice(
    val family: String, val weight: Int, val size: Int, val style: String, val stretch: Int
)

internal class WindowsFontPicker(
    private val apply: (FontChoice) -> Unit,
    private val report: (Throwable) -> Unit
) : AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "SPW Island font picker").apply { isDaemon = true }
    }
    private val running = AtomicBoolean()
    private val closed = AtomicBoolean()
    @Volatile private var process: Process? = null

    fun show(current: IslandSettings) {
        if (closed.get() || !running.compareAndSet(false, true)) return
        executor.execute {
            try {
                if (!WindowsNativeRuntime.isReady()) {
                    showRuntimePrompt()
                    return@execute
                }
                val executable = executablePath()
                val launched = ProcessBuilder(
                    executable.toString(),
                    "--font-picker",
                    "--family", current.fontFamily,
                    "--weight", current.fontWeight.toString(),
                    "--size", current.fontSize.toString(),
                    "--style", current.fontStyle,
                    "--stretch", current.fontStretch.toString()
                ).directory(executable.parent.toFile())
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start()
                process = launched
                val result = launched.inputStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                    lines.mapNotNull(::decode).firstOrNull()
                }
                val exitCode = launched.waitFor()
                if (exitCode != 0 && result == null && !closed.get()) {
                    error("Windows 运行组件启动失败，请检查安装后重试")
                }
                if (!closed.get() && result != null) apply(result)
            } catch (error: Exception) {
                if (!closed.get()) report(IllegalStateException("字体选择器启动失败", error))
            } finally {
                process = null
                running.set(false)
            }
        }
    }

    private fun showRuntimePrompt() {
        SwingUtilities.invokeLater {
            if (closed.get()) return@invokeLater
            val choice = JOptionPane.showOptionDialog(
                null,
                "Windows 运行组件尚未就绪。安装或修复后，再次打开字体选择器即可继续。",
                "Windows 运行组件",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                arrayOf("查看安装说明", "稍后"),
                "查看安装说明"
            )
            if (choice == 0) runCatching {
                Desktop.getDesktop().browse(URI(
                    "https://github.com/GaBoron/SPW-island/blob/main/docs/installation.md#windows-运行组件"
                ))
            }.onFailure(report)
        }
    }

    private fun executablePath(): Path {
        val resource = checkNotNull(javaClass.getResource("/native/island-host/IslandHost.exe")) {
            "字体选择器资源缺失"
        }
        check(resource.protocol == "file") { "字体选择器没有展开为可执行文件" }
        return Path.of(resource.toURI())
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        process?.destroy()
        executor.shutdownNow()
    }

    private companion object {
        fun decode(line: String): FontChoice? {
            val parts = line.split(':', limit = 6)
            if (parts.size != 6 || parts[0] != "APPLY") return null
            return runCatching {
                val family = if (parts[1] == "-") "" else {
                    String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8).take(100).trim()
                }
                val weight = parts[2].toInt().takeIf { it in 100..900 } ?: return null
                val size = parts[3].toInt().takeIf { it in 14..42 } ?: return null
                val style = parts[4].takeIf { it in setOf("normal", "italic", "oblique") } ?: return null
                val stretch = parts[5].toInt().takeIf { it in 1..9 } ?: return null
                FontChoice(family, weight, size, style, stretch)
            }.getOrNull()
        }
    }
}
