// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.remote

import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Optional fixed-size spectrum channel, independent of lyric and control messages. */
internal class WindowsSpectrumPipe(private val name: String) : AutoCloseable {
    private var sink: FileOutputStream? = null

    fun send(levels: FloatArray) {
        if (levels.size < 4) return
        val connected = sink ?: runCatching { FileOutputStream("\\\\.\\pipe\\" + name) }
            .getOrNull()?.also { sink = it } ?: return
        val frame = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        repeat(4) { frame.putFloat(levels[it].takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f) }
        runCatching { connected.write(frame.array()) }.onFailure { close() }
    }

    override fun close() {
        runCatching { sink?.close() }
        sink = null
    }
}
