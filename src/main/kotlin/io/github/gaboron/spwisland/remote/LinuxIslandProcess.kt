// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.remote

import io.github.gaboron.spwisland.core.*
import io.github.gaboron.spwisland.platform.LinuxHelper
import io.github.gaboron.spwisland.ui.PlaybackActions
import java.io.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** The host's Skiko swapBuffers can block its EDT when minimized; never render on that EDT. */
class LinuxIslandProcess(private val timeline: PlaybackSource, private val settings: SettingsStore,
                         private val actions: PlaybackActions, private val report: (Throwable) -> Unit,
                         javaHome: String = System.getProperty("java.home")) : AutoCloseable {
    private val process = LinuxHelper.startUi(javaHome)
    @Volatile var presentedFrames: Long = 0
        private set
    private val output = ObjectOutputStream(BufferedOutputStream(process.outputStream)).apply { flush() }
    private val sender = Executors.newSingleThreadScheduledExecutor { Thread(it, "SPW Island IPC").apply { isDaemon = true } }
    private val closed = AtomicBoolean()
    private val showAbout = AtomicBoolean()
    private var previousSettings: IslandSettings? = null
    private var previousMetadata: TrackMetadata? = null
    private var previousLyrics: List<LyricLine>? = null

    init {
        sender.scheduleWithFixedDelay({
            if (!closed.get()) try {
                val snapshot = timeline.snapshot()
                val currentSettings = settings.read()
                val state = IslandState(snapshot.copy(metadata = TrackMetadata(), lyrics = emptyList()),
                    currentSettings.takeIf { it != previousSettings },
                    snapshot.metadata.takeIf { it != previousMetadata },
                    snapshot.lyrics.takeIf { it != previousLyrics }, showAbout.getAndSet(false))
                output.writeObject(state)
                output.reset() // Bound the stream's object table over long listening sessions.
                output.flush()
                previousSettings = currentSettings
                previousMetadata = snapshot.metadata
                previousLyrics = snapshot.lyrics
            } catch (error: Exception) {
                if (!closed.get()) { close(); report(IllegalStateException("Linux 词岛进程已断开", error)) }
            }
        }, 0, 100, TimeUnit.MILLISECONDS)
        Thread({
            try {
                ObjectInputStream(BufferedInputStream(process.inputStream)).use { input ->
                    while (!closed.get()) dispatch(input.readObject() as IslandCommand)
                }
            } catch (error: Exception) {
                if (!closed.get()) { close(); report(IllegalStateException("Linux 词岛进程已退出", error)) }
            }
        }, "SPW Island commands").apply { isDaemon = true }.start()
    }

    private fun dispatch(command: IslandCommand) {
        val a = command.arguments
        try {
            when (command.action) {
                "frames" -> presentedFrames = a.single().toLong()
                "previous" -> actions.previous()
                "toggle" -> actions.toggle()
                "next" -> actions.next()
                "seek" -> actions.seek(a.single().toLong())
                "set" -> settings.set(a[0], if (a[1] == "boolean") a[2].toBooleanStrict() else a[2])
                "position" -> settings.savePosition(a[0], a[1].toInt(), a[2].toInt(),
                    requireNotNull(IslandAnchor.fromStorage(a[3])))
                "reset" -> settings.resetPosition()
                "resetAll" -> settings.resetAll()
                "error" -> report(IllegalStateException(a.single()))
            }
        } catch (error: Exception) { report(error) }
    }

    fun about() { showAbout.set(true) }
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        sender.shutdownNow()
        // Killing the process also closes a blocked pipe writer; do not wait for the host EDT.
        process.destroy()
        Thread({
            if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly()
            runCatching { output.close() }
        }, "SPW Island cleanup").apply { isDaemon = true }.start()
    }
}
