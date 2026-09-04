// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.platform

import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Starts a private process-loopback reader; only four FFT band levels cross the pipe. */
class ProcessSpectrum : AutoCloseable {
    @Volatile private var stopped = false
    @Volatile private var process: Process? = null
    @Volatile private var sample = FloatArray(4)
    @Volatile private var receivedAt = 0L
    @Volatile var status = "正在连接 SPW 音频"
        private set
    private val worker = thread(name = "SPW Island audio", isDaemon = true) {
        try {
            val resource = ProcessSpectrum::class.java.getResource("/native/spw-spectrum.exe")
                ?: error("插件中缺少 native/spw-spectrum.exe")
            check(resource.protocol == "file") { "请使用 SPW 插件 ZIP 安装频谱程序" }
            val helper = ProcessBuilder(Path.of(resource.toURI()).toString(), ProcessHandle.current().pid().toString())
                .redirectErrorStream(true).start()
            process = helper
            if (stopped) helper.destroy()
            helper.inputStream.bufferedReader().useLines { lines -> lines.forEach { line ->
                if (line == "READY") status = "SPW 进程音频频谱"
                else {
                    val values = line.split(',').mapNotNull { it.toFloatOrNull()?.takeIf(Float::isFinite) }
                    if (values.size == 4) {
                        sample = values.map { it.coerceIn(0f, 1f) }.toFloatArray()
                        receivedAt = System.nanoTime()
                    } else if (line.isNotBlank()) {
                        status = "频谱不可用：$line"
                        System.err.println("[SPW Island] $status")
                    }
                }
            } }
            if (!stopped && helper.waitFor() != 0) status = "频谱不可用（需要 Windows 20348+ 与共享音频输出）：$status"
        } catch (error: Exception) {
            if (!stopped) {
                status = "频谱不可用：${error.message}"
                System.err.println("[SPW Island] $status")
            }
        } finally { sample = FloatArray(4) }
    }
    fun levels(): FloatArray = if (System.nanoTime() - receivedAt < 350_000_000) sample else FloatArray(4)
    override fun close() {
        stopped = true
        process?.let { helper ->
            runCatching { helper.outputStream.bufferedWriter().apply { write("stop\n"); flush() } }
            if (!helper.waitFor(800, TimeUnit.MILLISECONDS)) helper.destroy()
            if (!helper.waitFor(200, TimeUnit.MILLISECONDS)) helper.destroyForcibly()
        }
        worker.join(200)
    }
}
