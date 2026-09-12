// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.host

import io.github.gaboron.spwisland.core.PlaybackTimeline
import io.github.gaboron.spwisland.core.Track
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Briefly recovers the current track when plugin startup misses the host's load callback. */
internal class CurrentTrackRecovery(
    private val timeline: PlaybackTimeline,
    private val probe: () -> Track?,
    private val load: (Track) -> Unit
) : AutoCloseable {
    private val worker = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "SPW Island current track").apply { isDaemon = true }
    }
    private var attempts = 0
    @Volatile private var closed = false

    fun start() {
        worker.scheduleWithFixedDelay(::attempt, 0, 500, TimeUnit.MILLISECONDS)
    }

    private fun attempt() {
        if (closed) return
        if (timeline.snapshot().track != null) {
            finish()
            return
        }
        attempts++
        probe()?.let {
            load(it)
            finish()
            return
        }
        if (attempts >= 8) finish()
    }

    private fun finish() {
        closed = true
        worker.shutdown()
    }

    override fun close() {
        closed = true
        worker.shutdownNow()
    }
}
