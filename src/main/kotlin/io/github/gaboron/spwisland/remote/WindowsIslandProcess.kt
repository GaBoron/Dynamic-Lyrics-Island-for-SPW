// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.remote

import io.github.gaboron.spwisland.core.PlaybackSource
import io.github.gaboron.spwisland.core.SettingsStore
import io.github.gaboron.spwisland.core.IslandAnchor
import io.github.gaboron.spwisland.core.SpectrumMode
import io.github.gaboron.spwisland.core.performance
import io.github.gaboron.spwisland.platform.WindowsNativeRuntime
import io.github.gaboron.spwisland.ui.PlaybackActions
import java.awt.GraphicsEnvironment
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

/** Keeps the native process alive while AWT remains the active Windows renderer. */
internal class WindowsIslandProcess(
    private val timeline: PlaybackSource,
    private val settings: SettingsStore,
    private val actions: PlaybackActions,
    private val spectrumLevels: () -> FloatArray
) : AutoCloseable {
    private val closed = AtomicBoolean()
    @Volatile private var process: Process? = null
    private val supervisor = Thread(::supervise, "SPW Island native host").apply {
        isDaemon = true
        start()
    }

    private fun supervise() {
        var failures = 0
        var notified = false
        while (!closed.get()) {
            try {
                if (!WindowsNativeRuntime.isReady()) {
                    Thread.sleep(30_000)
                    continue
                }
                val sessionStarted = System.nanoTime()
                try {
                    runSession()
                } finally {
                    if (System.nanoTime() - sessionStarted >= 30_000_000_000L) {
                        failures = 0
                        notified = false
                    }
                }
                if (!closed.get()) error("IslandHost 已退出")
            } catch (error: InterruptedException) {
                if (closed.get()) return
            } catch (error: Exception) {
                if (!closed.get() && !notified) {
                    System.err.println("[SPW Island] IslandHost 暂时不可用，将自动重试：" + error.message)
                    notified = true
                }
            }
            failures = (failures + 1).coerceAtMost(5)
            if (!closed.get()) try {
                Thread.sleep((1000L shl (failures - 1)).coerceAtMost(30_000L))
            } catch (_: InterruptedException) { if (closed.get()) return }
        }
    }

    private fun runSession() {
        val resource = checkNotNull(javaClass.getResource("/native/island-host/IslandHost.exe")) {
            "IslandHost 资源缺失"
        }
        check(resource.protocol == "file") { "请使用 SPW 插件 ZIP 安装 IslandHost" }
        val executable = Path.of(resource.toURI())
        val pipeName = "spw-island-" + UUID.randomUUID().toString()
        val child = ProcessBuilder(executable.toString(),
            "--parent-pid", ProcessHandle.current().pid().toString(),
            "--spectrum-pipe", pipeName)
            .directory(executable.parent.toFile())
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()
        process = child
        val spectrum = WindowsSpectrumPipe(pipeName)
        try {
            val input = child.inputStream.bufferedReader(StandardCharsets.UTF_8)
            val output = child.outputStream.bufferedWriter(StandardCharsets.UTF_8)
            val wire = WindowsIslandWire()
            val handshake = AtomicReference("")
            val ready = CountDownLatch(1)
            val readerOpen = AtomicBoolean(true)
            Thread({
                try {
                    input.useLines { lines ->
                        var first = true
                        lines.forEach { line ->
                            if (first) {
                                handshake.set(line)
                                ready.countDown()
                                first = false
                            } else wire.command(line)?.let(::dispatch)
                        }
                    }
                } catch (error: Exception) {
                    if (!closed.get()) System.err.println("[SPW Island] IslandHost 控制通道已断开：" + error.message)
                } finally {
                    readerOpen.set(false)
                    ready.countDown()
                }
            }, "SPW Island native controls").apply { isDaemon = true; start() }
            check(ready.await(5, TimeUnit.SECONDS) && wire.ready(handshake.get())) {
                "IslandHost 协议握手失败"
            }
            while (!closed.get() && child.isAlive && readerOpen.get()) {
                val current = settings.read()
                val message = wire.state(timeline.snapshot(), current)
                if (message != null) {
                    output.write(message)
                    output.newLine()
                    output.flush()
                }
                if (current.performance.spectrumMode == SpectrumMode.LIVE &&
                    current.sideContent.showsSpectrum) {
                    spectrum.send(spectrumLevels())
                }
                Thread.sleep(current.performance.nativeSyncDelayMs.toLong())
            }
        } finally {
            spectrum.close()
            process = null
            child.destroy()
        }
    }

    private fun dispatch(command: IslandCommand) {
        when (command.action) {
        "previous" -> actions.previous()
        "toggle" -> actions.toggle()
        "next" -> actions.next()
        "seek" -> command.arguments.singleOrNull()?.toLongOrNull()?.let(actions::seek)
        "setting" -> if (command.arguments.size == 2) {
            settings.set(command.arguments[0], command.arguments[1].toBooleanStrict())
        }
        "position" -> if (command.arguments.size == 6) {
            val args = command.arguments
            val anchor = IslandAnchor.fromStorage(args[3])
            val nativeIndex = Regex("DISPLAY(\\d+)$", RegexOption.IGNORE_CASE)
                .find(args[0])?.groupValues?.get(1)?.toIntOrNull()
            val device = nativeIndex?.let { index ->
                GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
                    .firstOrNull { it.iDstring.endsWith("Display${index - 1}", ignoreCase = true) }
            }
            if (anchor != null && device != null) {
                val config = device.defaultConfiguration
                val bounds = config.bounds
                val scale = config.defaultTransform
                val x = bounds.x + ((args[1].toInt() - args[4].toInt()) / scale.scaleX).roundToInt()
                val y = bounds.y + ((args[2].toInt() - args[5].toInt()) / scale.scaleY).roundToInt()
                settings.savePosition(device.iDstring, x, y, anchor)
            }
        }
        "resetPosition" -> settings.resetPosition()
        else -> Unit
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        process?.destroy()
        supervisor.interrupt()
    }
}
