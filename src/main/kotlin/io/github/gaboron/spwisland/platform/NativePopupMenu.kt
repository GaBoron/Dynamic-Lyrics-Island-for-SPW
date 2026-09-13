// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.platform

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.POINT
import java.awt.Window
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.Base64
import java.util.concurrent.Executors

/** Process bridge for the WPF popup. The native window owns rendering and light-dismiss behavior. */
internal class NativePopupMenu(private val report: (Throwable) -> Unit) : AutoCloseable {
    enum class Kind { TITLE, NOTE, SEPARATOR, TOGGLE, ACTION }
    data class Entry(val kind: Kind, val id: Int = 0, val selected: Boolean = false, val label: String = "")

    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "SPW Island native menu").apply { isDaemon = true }
    }
    @Volatile private var generation = 0
    @Volatile private var process: Process? = null
    @Volatile private var closed = false

    @Synchronized
    fun show(entries: List<Entry>, dark: Boolean, lowPerformance: Boolean, owner: Window, selected: (Int) -> Unit) {
        if (closed) return
        // Snapshot placement before asynchronous startup; cursor movement while WPF
        // loads must not change where this menu opens.
        val cursor = POINT()
        if (!User32.INSTANCE.GetCursorPos(cursor)) {
            report(IllegalStateException("无法获取右键菜单打开位置"))
            return
        }
        val cursorX = cursor.x
        val cursorY = cursor.y
        val ownerHandle = Pointer.nativeValue(Native.getWindowPointer(owner))
        generation++
        val request = generation
        process?.destroy()
        worker.execute { launch(request, entries, dark, lowPerformance, ownerHandle, cursorX, cursorY, selected) }
    }

    private fun launch(
        request: Int,
        entries: List<Entry>,
        dark: Boolean,
        lowPerformance: Boolean,
        ownerHandle: Long,
        cursorX: Int,
        cursorY: Int,
        selected: (Int) -> Unit
    ) {
        try {
            if (closed || request != generation) return
            val resource = NativePopupMenu::class.java.getResource("/native/spw-menu.exe")
                ?: error("插件中缺少 native/spw-menu.exe")
            check(resource.protocol == "file") { "请使用 SPW 插件 ZIP 安装原生菜单程序" }
            val helper = ProcessBuilder(
                Path.of(resource.toURI()).toString(),
                if (dark) "dark" else "light",
                if (lowPerformance) "solid" else "acrylic",
                ownerHandle.toString(),
                cursorX.toString(),
                cursorY.toString()
            ).start()
            process = helper
            helper.outputStream.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
                for (entry in entries) {
                    val label = Base64.getEncoder().encodeToString(entry.label.toByteArray(StandardCharsets.UTF_8))
                    writer.append(entry.kind.name.lowercase()).append('\t')
                        .append(entry.id.toString()).append('\t')
                        .append(if (entry.selected) "1" else "0").append('\t')
                        .append(label).append('\n')
                }
            }
            val command = helper.inputStream.bufferedReader(StandardCharsets.UTF_8).readLine()?.toIntOrNull()
            val errorText = helper.errorStream.bufferedReader(StandardCharsets.UTF_8).readText().trim()
            val exit = helper.waitFor()
            if (!closed && request == generation) {
                if (exit != 0) error(errorText.ifBlank { "原生菜单程序异常退出：$exit" })
                command?.let(selected)
            }
        } catch (error: Exception) {
            if (!closed && request == generation) report(error)
        } finally {
            if (request == generation) {
                process = null
            }
        }
    }

    override fun close() {
        closed = true
        generation++
        process?.destroy()
        process = null
        worker.shutdownNow()
    }
}
