// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.platform

import io.github.gaboron.spwisland.core.IslandSettings
import io.github.gaboron.spwisland.core.LyricFontWeight
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Launches the self-contained WinUI font picker without blocking SPW's UI thread. */
internal class WindowsFontPicker(
    private val apply: (String, LyricFontWeight) -> Unit,
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
                val executable = executablePath()
                val launched = ProcessBuilder(
                    executable.toString(),
                    "--family", current.fontFamily,
                    "--weight", current.fontWeight.storageName
                ).directory(executable.parent.toFile())
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start()
                process = launched
                val result = launched.inputStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                    lines.mapNotNull(::decode).firstOrNull()
                }
                launched.waitFor()
                if (!closed.get() && result != null) apply(result.first, result.second)
            } catch (error: Exception) {
                if (!closed.get()) report(IllegalStateException("字体选择器启动失败", error))
            } finally {
                process = null
                running.set(false)
            }
        }
    }

    private fun executablePath(): Path {
        val resource = checkNotNull(javaClass.getResource("/native/font-picker/IslandFontPicker.exe")) {
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
        fun decode(line: String): Pair<String, LyricFontWeight>? {
            val parts = line.split(':', limit = 3)
            if (parts.size != 3 || parts[0] != "APPLY") return null
            val weight = LyricFontWeight.fromStorage(parts[1])
            val family = runCatching {
                if (parts[2] == "-") "" else {
                    String(Base64.getDecoder().decode(parts[2]), StandardCharsets.UTF_8).take(100).trim()
                }
            }.getOrNull() ?: return null
            return family to weight
        }
    }
}
